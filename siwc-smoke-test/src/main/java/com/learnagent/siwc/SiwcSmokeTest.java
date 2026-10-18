package com.learnagent.siwc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public final class SiwcSmokeTest {
  private static final URI AUTHORIZE_ENDPOINT = URI.create("https://auth.openai.com/api/accounts/authorize");
  private static final URI TOKEN_ENDPOINT = URI.create("https://auth.openai.com/api/accounts/oauth/token");
  private static final URI MODELS_ENDPOINT = URI.create("https://api.openai.com/v1/models");
  private static final URI RESPONSES_ENDPOINT = URI.create("https://api.openai.com/v1/responses");
  private static final URI USAGE_SETTINGS = URI.create("https://chatgpt.com/settings/usage");
  private static final String ISSUER = "https://auth.openai.com";
  private static final String JWKS_URI = "https://auth.openai.com/.well-known/jwks.json";
  private static final String RESOURCE = "https://api.openai.com/v1";
  private static final String DYNAMIC_CLIENT_ID = "dynamic_agent_client";
  private static final String DIRECT_SCOPE = "chatgpt.tokens.use.direct";
  private static final String SCOPES = "openid profile email offline_access resource.invoke " + DIRECT_SCOPE;
  private static final Duration AUTH_TIMEOUT = Duration.ofMinutes(15);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final SecureRandom RANDOM = new SecureRandom();

  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
  private final Path dataDirectory;
  private final boolean authorizeOnly;

  private SiwcSmokeTest(boolean authorizeOnly) {
    String configured = System.getProperty("siwc.data-dir");
    this.dataDirectory = configured == null || configured.isBlank()
        ? Path.of(System.getProperty("user.dir"), ".runtime", "siwc").toAbsolutePath()
        : Path.of(configured).toAbsolutePath();
    this.authorizeOnly = authorizeOnly;
  }

  public static void main(String[] args) {
    try {
      boolean authorizeOnly = List.of(args).contains("--authorize-only");
      new SiwcSmokeTest(authorizeOnly).run();
    } catch (Exception exception) {
      System.err.println();
      System.err.println("SIWC smoke test failed: " + rootMessage(exception));
      System.exit(1);
    }
  }

  private void run() throws Exception {
    Files.createDirectories(dataDirectory);
    String hostId = loadOrCreateHostId();
    Optional<Registration> savedRegistration = loadRegistration();

    System.out.println("LearnAgent Sign in with ChatGPT smoke test");
    System.out.println("Runtime data: " + dataDirectory);
    System.out.println("No OpenAI API key will be used.");
    System.out.println("Access and refresh tokens remain in memory and are discarded when this process exits.");

    try (LoopbackCallback callback = LoopbackCallback.start()) {
      String state = randomValue(32);
      String nonce = randomValue(32);
      String verifier = randomValue(64);
      String requestedClientId = savedRegistration.map(Registration::clientId).orElse(DYNAMIC_CLIENT_ID);
      boolean firstRegistration = DYNAMIC_CLIENT_ID.equals(requestedClientId);
      URI authorizationUri = authorizationUri(
          callback.redirectUri(), requestedClientId, hostId, state, nonce, codeChallenge(verifier), firstRegistration);

      System.out.println();
      System.out.println("Opening the system browser for ChatGPT authorization...");
      openBrowser(authorizationUri);
      System.out.println("If the browser did not open, copy this URL into your browser:");
      System.out.println(authorizationUri);

      CallbackResult returned = callback.await(AUTH_TIMEOUT);
      verifyState(state, returned.state());
      if (returned.error() != null) {
        throw new IllegalStateException("Authorization was not completed: " + returned.error());
      }
      if (returned.code() == null || returned.code().isBlank()) {
        throw new IllegalStateException("The callback did not contain an authorization code.");
      }

      String issuedClientId = resolveIssuedClientId(firstRegistration, requestedClientId, returned.clientId());
      JsonNode tokens = exchangeCode(returned.code(), verifier, callback.redirectUri(), issuedClientId);
      String idToken = requiredText(tokens, "id_token");
      String accessToken = requiredText(tokens, "access_token");
      String grantedScopes = requiredText(tokens, "scope");

      Jwt identity = verifyIdToken(idToken, issuedClientId, nonce);
      requireDirectScope(grantedScopes);
      saveRegistration(new Registration(issuedClientId, hostId));

      System.out.println("Authorized ChatGPT account: " + displayIdentity(identity));
      if (authorizeOnly) {
        System.out.println("AUTHORIZATION SUCCESS: no model request was sent.");
        System.out.println("Opening ChatGPT Usage settings. Set a conservative App limit and disable credit use before the inference test.");
        if (!openBrowser(USAGE_SETTINGS)) {
          System.out.println("Open this URL manually: " + USAGE_SETTINGS);
        }
        return;
      }
      List<ModelChoice> models = listModels(accessToken);
      if (models.isEmpty()) {
        throw new IllegalStateException("Authorization succeeded, but no visible models were returned for this account.");
      }
      printModels(models);
      ModelChoice selected = selectModel(models);
      System.out.println("Testing model: " + selected.displayName() + " (" + selected.slug() + ")");

      ResponsesSseCollector.Result result = callResponses(accessToken, selected.slug());
      if (result.text().isBlank()) {
        throw new IllegalStateException("The model completed the response without text output.");
      }

      System.out.println();
      System.out.println("Model reply: " + result.text().trim());
      System.out.println("SUCCESS: SIWC login, model discovery, and Responses API inference all worked.");
      System.out.println("No OAuth access or refresh token was written to disk.");
    }
  }

  static URI authorizationUri(
      URI redirectUri,
      String clientId,
      String hostId,
      String state,
      String nonce,
      String challenge,
      boolean firstRegistration) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("client_id", clientId);
    if (firstRegistration) {
      parameters.put("agent_name_hint", "LearnAgent");
    }
    parameters.put("ext_agent_host_id", hostId);
    parameters.put("response_type", "code");
    parameters.put("redirect_uri", redirectUri.toString());
    parameters.put("scope", SCOPES);
    parameters.put("resource", RESOURCE);
    parameters.put("state", state);
    parameters.put("nonce", nonce);
    parameters.put("code_challenge_method", "S256");
    parameters.put("code_challenge", challenge);
    return URI.create(AUTHORIZE_ENDPOINT + "?" + formEncode(parameters));
  }

  static String codeChallenge(String verifier) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }

  private JsonNode exchangeCode(String code, String verifier, URI redirectUri, String clientId)
      throws IOException, InterruptedException {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("grant_type", "authorization_code");
    form.put("client_id", clientId);
    form.put("code", code);
    form.put("code_verifier", verifier);
    form.put("redirect_uri", redirectUri.toString());
    form.put("resource", RESOURCE);

    HttpRequest request = HttpRequest.newBuilder(TOKEN_ENDPOINT)
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
        .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    requireSuccess("OAuth code exchange", response.statusCode(), response.body(), response.headers().firstValue("openai-request-id"));
    return JSON.readTree(response.body());
  }

  private Jwt verifyIdToken(String idToken, String clientId, String expectedNonce) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
    Jwt jwt = decoder.decode(idToken);
    if (!jwt.getAudience().contains(clientId)) {
      throw new IllegalStateException("The ID token audience did not match the issued client ID.");
    }
    if (!MessageDigest.isEqual(
        expectedNonce.getBytes(StandardCharsets.UTF_8),
        Optional.ofNullable(jwt.getClaimAsString("nonce")).orElse("").getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalStateException("The ID token nonce did not match the authorization attempt.");
    }
    if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
      throw new IllegalStateException("The verified ID token did not contain a subject.");
    }
    return jwt;
  }

  private List<ModelChoice> listModels(String accessToken) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(MODELS_ENDPOINT)
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + accessToken)
        .GET()
        .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    requireSuccess("Model discovery", response.statusCode(), response.body(), response.headers().firstValue("openai-request-id"));

    JsonNode payload = JSON.readTree(response.body());
    JsonNode items = payload.path("models");
    if (!items.isArray()) {
      throw new IllegalStateException("The model-list response did not contain a models array.");
    }
    List<ModelChoice> models = new ArrayList<>();
    for (JsonNode item : items) {
      if (!"list".equals(item.path("visibility").asText())) {
        continue;
      }
      String slug = item.path("slug").asText();
      if (!slug.isBlank()) {
        String displayName = item.path("display_name").asText(slug);
        models.add(new ModelChoice(slug, displayName));
      }
    }
    return models;
  }

  private ResponsesSseCollector.Result callResponses(String accessToken, String model)
      throws IOException, InterruptedException {
    ObjectNode body = JSON.createObjectNode();
    body.put("model", model);
    ArrayNode input = body.putArray("input");
    ObjectNode message = input.addObject();
    message.put("role", "user");
    message.put("content", "Reply with exactly: LearnAgent SIWC works.");
    body.put("store", false);
    body.put("stream", true);

    HttpRequest request = HttpRequest.newBuilder(RESPONSES_ENDPOINT)
        .timeout(Duration.ofMinutes(2))
        .header("Accept", "text/event-stream")
        .header("Authorization", "Bearer " + accessToken)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
        .build();
    HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      try (InputStream stream = response.body()) {
        String errorBody = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        requireSuccess("Responses API", response.statusCode(), errorBody, response.headers().firstValue("openai-request-id"));
      }
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
      return new ResponsesSseCollector(JSON).collect(reader);
    }
  }

  private String loadOrCreateHostId() throws IOException {
    Path file = dataDirectory.resolve("host-id.txt");
    if (Files.exists(file)) {
      String hostId = Files.readString(file, StandardCharsets.UTF_8).trim();
      if (!hostId.isBlank()) {
        return hostId;
      }
    }
    String hostId = "urn:uuid:" + UUID.randomUUID();
    atomicWrite(file, hostId + System.lineSeparator());
    return hostId;
  }

  private Optional<Registration> loadRegistration() throws IOException {
    Path file = dataDirectory.resolve("registration.json");
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    return Optional.of(JSON.readValue(file.toFile(), Registration.class));
  }

  private void saveRegistration(Registration registration) throws IOException {
    Path file = dataDirectory.resolve("registration.json");
    atomicWrite(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(registration) + System.lineSeparator());
  }

  private static void atomicWrite(Path target, String content) throws IOException {
    Files.createDirectories(target.getParent());
    Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
    try {
      Files.writeString(temporary, content, StandardCharsets.UTF_8);
      try {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static String resolveIssuedClientId(boolean firstRegistration, String requestedClientId, String returnedClientId) {
    if (firstRegistration) {
      if (returnedClientId == null || returnedClientId.isBlank()) {
        throw new IllegalStateException("Dynamic registration did not return an issued client ID.");
      }
      return returnedClientId;
    }
    if (returnedClientId != null && !returnedClientId.isBlank() && !requestedClientId.equals(returnedClientId)) {
      throw new IllegalStateException("The callback returned a different client ID from the selected registration.");
    }
    return requestedClientId;
  }

  private static void verifyState(String expected, String actual) {
    if (actual == null || !MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalStateException("The OAuth state did not match the authorization attempt.");
    }
  }

  private static void requireDirectScope(String grantedScopes) {
    boolean granted = List.of(grantedScopes.split("\\s+")).contains(DIRECT_SCOPE);
    if (!granted) {
      throw new IllegalStateException("ChatGPT sign-in succeeded, but ChatGPT plan usage was not authorized.");
    }
  }

  private static String requiredText(JsonNode object, String field) {
    String value = object.path(field).asText();
    if (value.isBlank()) {
      throw new IllegalStateException("The OAuth token response did not contain " + field + ".");
    }
    return value;
  }

  private static ModelChoice selectModel(List<ModelChoice> models) {
    String requested = System.getenv("SIWC_MODEL");
    if (requested == null || requested.isBlank()) {
      return models.getFirst();
    }
    return models.stream()
        .filter(model -> model.slug().equals(requested))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("SIWC_MODEL is not available to this account: " + requested));
  }

  private static void printModels(List<ModelChoice> models) {
    System.out.println("Available models (first " + Math.min(10, models.size()) + "):");
    models.stream().limit(10).forEach(model ->
        System.out.println("  - " + model.displayName() + " (" + model.slug() + ")"));
  }

  private static String displayIdentity(Jwt identity) {
    String email = identity.getClaimAsString("email");
    String name = identity.getClaimAsString("name");
    if (email != null && !email.isBlank()) {
      return email;
    }
    if (name != null && !name.isBlank()) {
      return name;
    }
    return identity.getSubject();
  }

  private static boolean openBrowser(URI authorizationUri) {
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(authorizationUri);
        return true;
      }
    } catch (Exception ignored) {
      // The complete URL is printed for manual opening below.
    }
    return false;
  }

  private static String randomValue(int bytes) {
    byte[] value = new byte[bytes];
    RANDOM.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private static String formEncode(Map<String, String> parameters) {
    return parameters.entrySet().stream()
        .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
        .reduce((left, right) -> left + "&" + right)
        .orElse("");
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static void requireSuccess(String operation, int status, String body, Optional<String> requestId) {
    if (status >= 200 && status < 300) {
      return;
    }
    String diagnostic = body == null ? "" : body.replaceAll("\\s+", " ").trim();
    if (diagnostic.length() > 500) {
      diagnostic = diagnostic.substring(0, 500) + "...";
    }
    String suffix = requestId.map(id -> " requestId=" + id).orElse("");
    throw new IllegalStateException(operation + " failed with HTTP " + status + suffix
        + (diagnostic.isBlank() ? "" : ": " + diagnostic));
  }

  private static String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return Optional.ofNullable(current.getMessage()).orElse(current.getClass().getSimpleName());
  }

  record Registration(String clientId, String hostId) {}

  record ModelChoice(String slug, String displayName) {}

  record CallbackResult(String code, String state, String clientId, String error) {}

  static final class LoopbackCallback implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private final CompletableFuture<CallbackResult> result;
    private final URI redirectUri;

    private LoopbackCallback(HttpServer server, ExecutorService executor, CompletableFuture<CallbackResult> result) {
      this.server = server;
      this.executor = executor;
      this.result = result;
      this.redirectUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/auth/callback");
    }

    static LoopbackCallback start() throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "siwc-loopback-callback");
        thread.setDaemon(true);
        return thread;
      });
      CompletableFuture<CallbackResult> result = new CompletableFuture<>();
      server.setExecutor(executor);
      server.createContext("/auth/callback", exchange -> handleCallback(exchange, result));
      server.start();
      return new LoopbackCallback(server, executor, result);
    }

    URI redirectUri() {
      return redirectUri;
    }

    CallbackResult await(Duration timeout) throws Exception {
      try {
        return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (java.util.concurrent.TimeoutException exception) {
        throw new IllegalStateException("No browser authorization callback was received within "
            + timeout.toMinutes() + " minutes.", exception);
      }
    }

    @Override
    public void close() {
      server.stop(0);
      executor.shutdownNow();
    }

    private static void handleCallback(HttpExchange exchange, CompletableFuture<CallbackResult> result)
        throws IOException {
      Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
      CallbackResult callback = new CallbackResult(
          query.get("code"), query.get("state"), query.get("client_id"), query.get("error"));
      String html = """
          <!doctype html><html lang="zh-CN"><meta charset="utf-8">
          <title>LearnAgent SIWC</title>
          <body style="font-family:system-ui;padding:3rem;max-width:720px;margin:auto">
          <h1>LearnAgent 已收到授权结果</h1>
          <p>可以关闭此页面并返回终端查看验证结果。</p>
          </body></html>
          """;
      byte[] response = html.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
      result.complete(callback);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
      Map<String, String> values = new LinkedHashMap<>();
      if (rawQuery == null || rawQuery.isBlank()) {
        return values;
      }
      for (String pair : rawQuery.split("&")) {
        String[] parts = pair.split("=", 2);
        String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
        values.put(key, value);
      }
      return values;
    }
  }
}
