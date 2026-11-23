# Hindi Reminder Letter HTTP 401 investigation ? 2026-10-17

## Configuration source and confirmed defect

The existing OpenAiTranslationGateway constructor uses Spring @Value("${app.document-ai.api-key:}"). It does not read a credential from the database, template metadata or frontend. The application.properties working copy supplied OPENAI_API_KEY with a hardcoded fallback credential inside literal quotes. The placeholder syntax itself was valid: Spring treats quote characters in a .properties value/default as literal characters, not string delimiters. The previous gateway neither trimmed nor validated the credential, so choosing that fallback produced an Authorization header whose credential contained quotation marks.

This is a confirmed local configuration defect capable of causing HTTP 401. The exact credential/account status behind the observed live failure is not established: no real OpenAI request was made, and the already-running backend's inherited environment was not read. A valid key supplied by a higher-priority source could have bypassed the fallback. No complete or partial original credential is reproduced in this report, logs or diagnostic output.

No profile-specific application properties/YAML files or explicit external configuration imports/locations were found in the inspected workspace. OPENAI_API_KEY and direct Spring key overrides were absent in the tool process and Windows user/machine environment settings. SPRING_APPLICATION_JSON and SPRING_PROFILES_ACTIVE were also absent there. No AI-key or Spring profile/config arguments were detected on inspected Java process command lines. No related override was detected in this session's JAVA_TOOL_OPTIONS, JDK_JAVA_OPTIONS, JAVA_OPTS or MAVEN_OPTS. IDE/service launchers may still supply their own process environment; this was not assumed equivalent to the tool's environment.

## Changes

- application.properties now uses app.document-ai.api-key=${OPENAI_API_KEY:}, with an empty default and no embedded credential. The Git index/HEAD did not contain the removed OpenAI credential; it was in the modified working copy. No Git commit or staging operation was performed.
- The existing gateway strips surrounding whitespace and rejects literal quotes, embedded whitespace/control characters, non-ASCII token characters and unresolved placeholder text before attempting a request. These checks run only when conversion is needed, so English work can still run with an absent key or disabled AI.
- HTTP 401 retains its status code and no-finalization behavior, is not retried, and now gives fixed guidance to check the backend credential and restart. No provider error body, Authorization header or configured key is copied into frontend/audit error messages.
- No new integration, translation service, model, endpoint, organization/project header or production configuration was added. Existing enabled/pricing values and financial logic were preserved.

## Header, endpoint and model

Requests still send exactly one Authorization: Bearer <configured-key> header and Content-Type: application/json to https://api.openai.com/v1/responses. The model remains gpt-5-mini, as required by the existing pricing rules. OpenAI's official documentation confirms Bearer authentication and GPT-5 mini support for Responses/structured outputs. There is no configured OpenAI-Organization or OpenAI-Project header in this integration. Those headers are optional for relevant account setups; there is no evidence here that missing organization/project selection caused the reported failure.

References used with the OpenAI Docs skill:
- https://developers.openai.com/api/reference/overview
- https://developers.openai.com/api/docs/models/gpt-5-mini

## Required user action

1. Set OPENAI_API_KEY to a valid credential for the Windows account/process that starts the backend. Prefer a fresh key in place of the previously source-stored credential. Use Windows account Environment Variables settings or the backend launcher's secret configuration. Paste only the credential value, without literal quotation marks or a Bearer prefix; do not put it in a tracked file, frontend, chat or shell history.
2. Restart the terminal, IDE or service launcher if necessary so it inherits the new variable, then restart/rebuild the backend with this fix. The gateway captures configuration at startup; it does not hot-reload changed environment variables. The running backend was deliberately not restarted during this task.
3. Check for a stale direct property override if the wrong credential still appears to be used. Spring higher-priority property sources can override the packaged property; the APP_DOCUMENT_AI_API_KEY override path is covered by an isolated binding test.
4. Retry the Hindi Reminder Letter when ready. If 401 persists with the fresh key after restart, check key validity and project/model access in the OpenAI dashboard. Live credential validity and account access remain unverified because real API requests were not approved or made.

## Verification and files

See document-ai-authentication-test-results.txt for final commands and results. Tests use fictional credentials, isolated Spring property sources, mocked transport and a disposable loopback PostgreSQL database. They verify environment binding, direct override precedence, whitespace normalization, malformed/missing credential rejection, exact Bearer header construction, no 401 retries, safe errors/audits, a failed Hindi Reminder Letter export without a finalized document, successful English exports with no credential/disabled AI, and existing cache-backed Hindi generation.

Files changed for this request:
- src/main/resources/application.properties
- src/main/java/dugar_lms_api/modules/wordtemplates/automation/OpenAiTranslationGateway.java
- src/test/java/dugar_lms_api/modules/wordtemplates/DocumentAiAuthenticationTest.java (new; automation test package)
- src/test/java/dugar_lms_api/modules/wordtemplates/DocumentEnhancementIntegrationTest.java
- docs/document-ai-configuration.md
- docs/document-ai-authentication-fix.md (new)
- docs/document-ai-authentication-test-results.txt (new)

No application/production database or production configuration was changed. No deployment, real OpenAI request or running-backend restart was performed.
