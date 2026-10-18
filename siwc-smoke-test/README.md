# LearnAgent SIWC smoke test

This isolated Java 21 module verifies Sign in with ChatGPT without changing the
existing Spring AI provider. It performs exactly three remote operations:

1. OAuth 2.0 Authorization Code + PKCE through the system browser.
2. Account-specific model discovery through `GET /v1/models`.
3. One streamed `POST /v1/responses` request with `store: false` and
   `stream: true`.

No OpenAI API key is read. Access, refresh, and ID tokens remain in memory and
are discarded when the process exits. Only the stable host ID and issued OAuth
client ID are saved under the repository `.runtime\siwc` directory; `.runtime/` is ignored
by source control.

## Run on Windows

Before running, open ChatGPT **Settings > Usage** and set a conservative app
limit. Disable credit use after plan limits if you want the test to have no
additional cash cost.

```powershell
Set-Location <path-to-your-LearnAgent-clone>
powershell -ExecutionPolicy Bypass -File .\scripts\test-siwc.ps1 -AuthorizeOnly
```

The first run performs authorization only, opens ChatGPT Usage settings, and
does not send an inference request. Set the app limit and disable credit use,
then run the actual one-request test:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-siwc.ps1 -SkipBuild
```

The scripts keep the Maven repository, temporary files, runtime data, and build
outputs on drive D. Complete the browser consent screen after it opens.

To request one of the model slugs printed by the test:

```powershell
$env:SIWC_MODEL = 'MODEL_SLUG_FROM_THE_LIST'
powershell -ExecutionPolicy Bypass -File .\scripts\test-siwc.ps1 -SkipBuild
```

This module is intentionally a smoke test. It does not persist tokens, implement
tool calling, replace the LearnAgent model gateway, or provide embeddings.

If OAuth returns `unsupported_country_region_territory`, OpenAI has rejected the
current account or network region before inference. The test has not made a
model request. Do not repeatedly retry until the account and region are
officially eligible.
