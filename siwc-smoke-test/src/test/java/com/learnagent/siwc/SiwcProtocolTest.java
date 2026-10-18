package com.learnagent.siwc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SiwcProtocolTest {
  @Test
  void createsTheRfc7636S256Challenge() {
    String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    assertThat(SiwcSmokeTest.codeChallenge(verifier))
        .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
  }

  @Test
  void createsACompleteDynamicRegistrationAuthorizationUrl() {
    URI uri = SiwcSmokeTest.authorizationUri(
        URI.create("http://127.0.0.1:1455/auth/callback"),
        "dynamic_agent_client",
        "urn:uuid:11111111-1111-4111-8111-111111111111",
        "state-value",
        "nonce-value",
        "challenge-value",
        true);

    Map<String, String> query = Arrays.stream(uri.getRawQuery().split("&"))
        .map(part -> part.split("=", 2))
        .collect(Collectors.toMap(
            part -> decode(part[0]),
            part -> decode(part[1])));

    assertThat(uri.getHost()).isEqualTo("auth.openai.com");
    assertThat(query).containsEntry("client_id", "dynamic_agent_client")
        .containsEntry("agent_name_hint", "LearnAgent")
        .containsEntry("redirect_uri", "http://127.0.0.1:1455/auth/callback")
        .containsEntry("code_challenge_method", "S256")
        .containsEntry("resource", "https://api.openai.com/v1");
    assertThat(query.get("scope")).contains("chatgpt.tokens.use.direct");
  }

  @Test
  void collectsTextOnlyAfterACompletedResponsesEvent() throws Exception {
    String stream = """
        event: response.output_text.delta
        data: {"type":"response.output_text.delta","delta":"LearnAgent "}

        event: response.output_text.delta
        data: {"type":"response.output_text.delta","delta":"SIWC works."}

        event: response.completed
        data: {"type":"response.completed","response":{"status":"completed"}}

        data: [DONE]

        """;
    ResponsesSseCollector.Result result = new ResponsesSseCollector(new ObjectMapper())
        .collect(new BufferedReader(new StringReader(stream)));
    assertThat(result.completed()).isTrue();
    assertThat(result.text()).isEqualTo("LearnAgent SIWC works.");
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
