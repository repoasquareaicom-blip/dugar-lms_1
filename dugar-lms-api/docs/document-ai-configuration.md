# Document AI backend configuration

Document generation uses the OpenAI Responses API and `gpt-5-mini`. English generation and Contract Number Preview never call OpenAI. Hindi generation sends only dynamic values missing from the PostgreSQL translation cache; names and addresses use transliteration. Uploaded DOCX formatting and static text remain untouched.

Configure these environment variables on the backend only (never in React or source control):

| Variable | Purpose / default |
| --- | --- |
| `OPENAI_API_KEY` | Secret OpenAI API key; no default |
| `DOCUMENT_AI_MODEL` | Translation model; default `gpt-5-mini` |
| `DOCUMENT_AI_ENABLED` | `true` to allow Hindi API requests; current application.properties default is `true` (gateway fallback is `false`) |
| `DOCUMENT_AI_USD_INR` | Agreed positive USD/INR exchange rate; must be supplied before API use |
| `DOCUMENT_AI_INPUT_USD_PER_MILLION` | Standard input price, default `0.25` USD |
| `DOCUMENT_AI_CACHED_INPUT_USD_PER_MILLION` | Provider-cached input price, default `0.025` USD |
| `DOCUMENT_AI_OUTPUT_USD_PER_MILLION` | Output price, default `2.00` USD |

Equivalent `app.document-ai.*` Spring properties are in `application.properties`. The model defaults to `gpt-5-mini` and is configurable with `DOCUMENT_AI_MODEL`; the billing multiplier is fixed to **3**. Missing/invalid exchange rate stops the request before any provider call. Rates are snapshotted in `ai_api_usage.applied_rates` for every request; historical pricing versions and usage records are retained. PostgreSQL translation-cache hits create no API request or charge. Provider prompt-cache tokens are still charged at the configured cached-input rate.

Recorded default rates reference [official OpenAI GPT-5 mini documentation](https://developers.openai.com/api/docs/models/gpt-5-mini). Review configured rates when provider pricing or the agreed exchange rate changes. API usage costs are calculated from returned usage and configured rates, not a reconciled provider invoice. Failed requests without usage keep unknown costs null.

Flyway V072 disables only the retired Document AI Settings menu. No existing migration or production database is changed during implementation/testing.

## API authentication and Windows setup

`OpenAiTranslationGateway` injects `${app.document-ai.api-key:}` using Spring `@Value`. The source property is now:

```properties
app.document-ai.api-key=${OPENAI_API_KEY:}
```

There is no fallback credential. The previous working-tree property had a hardcoded, quoted fallback: Spring preserves those quotes as part of the value, and the gateway previously sent them unchanged in the Bearer header. No credential value is reproduced here. No profile-specific application configuration or explicit external config import was found. Standard higher-priority Spring sources can still override the property (for example `APP_DOCUMENT_AI_API_KEY`, JVM/command-line properties, or `SPRING_APPLICATION_JSON`). A direct environment override is covered by a binding test. `.env` files are not loaded automatically by this integration.

The inspected tool-process environment and Windows user/machine environment had no `OPENAI_API_KEY` or direct property override. This does not prove the environment inherited by an already-running IDE/backend process. No AI-key or profile/config argument was detected on the inspected Java process command lines; actual process environments were not read. Set the variable for the account/process that starts the backend.

On Windows, open **Edit environment variables for your account**, create `OPENAI_API_KEY`, and paste the actual key as the value without literal quote characters or a `Bearer ` prefix. Do not paste it into source code, the frontend, chat, or a shell command that saves it in history. Restart the terminal/IDE or service launcher so it inherits the new environment, then restart/rebuild the backend with the changed code. A key previously stored in source should be replaced with a fresh credential. This task does not set Windows environment variables or restart the running backend.

The gateway strips surrounding whitespace, rejects embedded whitespace, quotes, non-ASCII characters and unresolved placeholders before calling the provider, and sends exactly one `Authorization: Bearer <configured-key>` header. Missing or malformed credentials block only work that requires Hindi conversion; English and cached/no-conversion work remain available. HTTP 401 is not retried; frontend/audit errors include only a fixed diagnostic message and HTTP status, never the configured credential or provider error body.

The endpoint remains `https://api.openai.com/v1/responses` and the supported model remains `gpt-5-mini`; the model is intentionally constrained by the existing billing rules. No organization or project configuration/header is currently implemented. OpenAI supports optional `OpenAI-Organization` / `OpenAI-Project` headers for relevant account setups, but no evidence was found that these caused this failure, so none were added. See [official authentication documentation](https://developers.openai.com/api/reference/overview) and [GPT-5 mini documentation](https://developers.openai.com/api/docs/models/gpt-5-mini).

The quoted fallback is a confirmed local defect capable of producing the reported 401. Whether the underlying credential is valid, revoked, expired, or authorized for the required project/model was not tested: no real OpenAI request was made. If 401 remains after setting a fresh valid credential and restarting, verify that the backend launcher inherited the variable and remove stale direct property overrides before checking key/project access in the OpenAI dashboard.
