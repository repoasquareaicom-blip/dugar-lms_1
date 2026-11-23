> Historical implementation snapshot. For the completed bulk/PDF workflow, current limits, tests and deployment prerequisites, see [document-bulk-generation-continuation.md](document-bulk-generation-continuation.md).

# Document Auto Generation — implementation and local handoff

Date: 2026-10-16. No ProLMS/CoreLMS deployment, production SQL, or real OpenAI requests were performed.

## Existing implementation and compatibility

The existing three-panel Template Master, group/template repositories, original-file upload/replacement/download, schema-driven canonical registry, contract access predicate, DOCX merge engine and generation route were extended. Canonical placeholders, existing template IDs/files, API prefixes, menu codes and the two original page URLs are preserved. The working-tree V067 menu/permission work from the preceding task is included unchanged.

The actual POM uses **Spring Boot 4.1.0 and Java 21**, not Spring Boot 3. No framework upgrade/downgrade or new document conversion library was introduced. The application datasource configuration already had local edits when this task began; those edits were preserved. Tests never use that datasource.

The bundled old PostgreSQL archive shows numeric legacy menu codes and `SUPER_ADMIN_1` with role name SUPER_ADMIN. V067 handles root/parent-scoped legacy names, singular Transaction and canonical codes; V070 reuses Administration/Reports where available. Missing or ambiguous required records fail clearly. The current ProLMS/CoreLMS histories and schema are **not verified**; the local application database was unavailable/rejected credentials during initial inspection. Compatibility assessment is based on existing migrations, the archive and isolated PostgreSQL fixtures.

## Implemented behavior

- Document terminology throughout the existing screens and cached navigation labels. New Administration → Reports → AI API Usage & Billing and Administration → Masters → Document AI Settings menus.
- English/Hindi template variants share a mandatory logical `documentKey` within a group. Existing templates default to English with their old name as the logical key. Each language file can be replaced independently. A partial unique index prevents two active variants for the same group, logical key and language; inactive versions and display order remain available.
- Available Fields has alias CRUD/copy/search and conversion-rule editing. Aliases are globally case-insensitively unique, reject invalid characters and canonical-name collisions, and resolve through the existing canonical value resolver. Changing conversion metadata does not replace aliases.
- PRESERVE, TRANSLITERATE, TRANSLATE and deterministic FORMAT rules. Existing names/address fields receive conservative transliteration defaults from the actual registry schema; other fields remain PRESERVE. Identifiers are protected. Hindi amount words, Indian currency grouping and month names do not use AI.
- Hindi generation extracts only required fields, resolves aliases, looks up exact contextual cache entries and batches unique misses in groups of 25. Static document text is never sent for translation. Original paragraphs, run formatting, tables, headers/footers and page breaks pass through the existing DOCX engine.
- Cache identity includes language, conversion type, context, exact source hash/value and prompt version. Case and whitespace are not normalized. Hash collisions are checked against the stored exact value. Ordered PostgreSQL advisory locks coordinate application instances; concurrency admission leaves connection capacity for independent usage/cache commits.
- Successful API batches and usage logs survive a subsequent batch failure. Manually approved cache values are not overwritten by AI. Optimistic versions, correction reasons and history preserve corrections and finalized-document translation references.
- Field preview, authorized corrections, Hindi approval and downloadable `PREVIEW-*.docx` output. Finalization rechecks actor ownership, contract access, template language/activity/file hash and source values. Previews expire after 30 minutes and cannot be finalized twice. Temporary DOCX downloads are deleted when their streams close.
- Every preview/generation workflow records an audit, including English/cache-only attempts and failures. API logging is independent of cache rollback. A provider request without reported usage has **unknown cost**, not a fabricated estimate or a cache charge.
- Common usage attribution supports future modules without a document ID. Bulk preview supports up to 100 documents, links them to a batch and allocates newly shared request charges exactly once. Existing-cache hits outside that batch do not acquire new charges. Individual documents still use the approval/finalization endpoints; no new bulk UI/ZIP download was added.
- Administrator pricing versions, active status, configurable model rates, exchange rate, multiplier and tool-pricing metadata. Applied historical prices are immutable snapshots in usage records. The current translation client invokes no tools, so tool usage is empty and no tool fees are fabricated.
- Server-side report filtering/pagination, summary totals, audit/request/version drill-down, complete filtered Excel export and complete filtered print/PDF view. Report and export queries reuse the existing contract scope predicate. Aggregate costs count each provider request once; document drill-down uses allocation shares.

## Flyway migrations

Do not edit V000–V066 or repair their checksums. V067 from the preceding task is also unchanged.

| File | Purpose |
|---|---|
| `V067__rename_document_menus_and_restore_permissions.sql` | Existing menu labels/hierarchy and view permissions |
| `V068__document_languages_and_logical_types.sql` | Languages, English backfill, logical types and active-language uniqueness |
| `V069__document_conversion_cache_audit_and_ai_billing.sql` | Aliases, rules, cache/history, pricing, audit, usage, version/allocation relationships |
| `V070__document_ai_administration_menus.sql` | Report/settings menus and SUPER_ADMIN access |

Schema additions:

| Table | Responsibility |
|---|---|
| `document_languages` | Extensible language catalogue; en/hi initially supported by the conversion service |
| `word_templates` additions | `language_code`, `document_key`; original files and IDs unchanged |
| `document_placeholder_aliases` | Unique aliases to canonical codes |
| `document_field_rules` | Conversion/context/deterministic formatter configuration |
| `document_translation_cache` | Exact source, Hindi value, model/prompt/version, approval and originating usage |
| `document_translation_history` | Versioned original conversion and authorized corrections |
| `ai_pricing_versions` | Provider/model prices, effective date, exchange rate, multiplier and active status |
| `document_generation_audit` | Actor, contract/template/language, workflow status, batch, preview and cache counters |
| `ai_api_usage` | Actual provider calls, reported tokens, status, timing and snapshotted estimated billing |
| `document_generation_api_links` | Shared-request allocation; allocations sum to one |
| `document_generation_translation_versions` | Historical translation versions used by finalized documents |

Audit template references are snapshots rather than foreign keys to mutable templates, so deleting a template does not erase its historical audit. Cache/history/usage relationships retain their foreign keys. No large document binaries are stored in usage/audit tables.

## API changes

Existing `/api/word-templates` CRUD/file endpoints remain. Metadata upload/update now requires `languageCode` and `documentKey`; returned template objects include both. Existing `/api/word-documents/generate` keeps DOCX download and accepts optional `languageCode` (default en) and `previewId`. Without a preview ID it starts a new audited workflow; Hindi requires an approved preview. Existing contract search, placeholder listing and validation URLs remain. Generation-only users can read the active catalog and validate without receiving template mutation privileges.

New endpoints under **`/api/document-automation`**:

| Method/path | Access and purpose |
|---|---|
| GET `/languages`, `/capabilities` | Language catalogue / generation correction capability and DOCX format capability |
| GET `/fields` | Template Master: canonical fields, aliases and rules |
| POST `/aliases`; DELETE `/aliases/{id}` | Template Master: add/edit/remove aliases |
| PUT `/fields` | Template Master: conversion metadata |
| POST `/preview` | Generate permission; same contract/template request plus language |
| POST `/bulk-preview` | Generate permission; array of up to 100 requests |
| POST `/preview/{id}/approve` | Owner and Generate permission; `{corrections:{canonicalCode:text}}` |
| GET `/preview/{id}/download` | Owner and Generate permission; DOCX preview |
| GET/POST `/pricing`; PUT `/pricing/{id}/active` | Separate Document AI Settings permission |
| GET `/cache`; POST `/cache/{id}/approve`; GET `/cache/{id}/history` | Settings permission; review/search/correct/version history |
| GET `/report`; GET `/report/{id}` | Separate report permission plus existing contract access scope |
| GET `/report-export`; GET `/report-print` | Same report authorization and selected filters; Excel or printable HTML |

Report filters: `fromDate`, `toDate`, `username`, `module`, `feature`, `documentType`, `language`, `contractNumber`, `model`, `apiStatus`, `generationStatus`. Pages start at 0; report page size is capped at 200. Cache pages contain 50 rows. Report dates use the database session timezone, consistent between screen and exports; align that timezone with the business deployment settings.

SUPER_ADMIN receives the required menu grants through Flyway. Other report/settings roles need explicit grants. OPERATIONS retains Generate Document only. Corrections require Template Master access or explicit `can_edit` on the generation menu; view access alone can approve unchanged preview values but cannot edit them. Template mutation and original-template downloads still require Template Master.

## OpenAI configuration and cost calculation

Backend environment only:

```text
DOCUMENT_AI_ENABLED=false
OPENAI_API_KEY=<server-side secret>
DOCUMENT_AI_MODEL=gpt-5-mini
```

OpenAI is disabled by default. Before enabling it, configure an active pricing version for the chosen model and the agreed USD/INR rate in Document AI Settings. The seeded pricing row is inactive; its exchange-rate placeholder is not an operational exchange-rate quote. Existing cached Hindi values and deterministic formatting can be used without new API calls.

The integration posts to `https://api.openai.com/v1/responses`, uses `store:false` and a strict JSON schema in `text.format`. It sends only required source values with their conversion/context. Responses must be completed, contain every requested field, contain appropriate Hindi text and preserve numeric sequences/embedded identifiers. Duplicate JSON keys, explanations, missing values, unexpected English text and incomplete/refused output are rejected. Retryable network/429/5xx failures have at most three attempts with backoff; connection/request timeouts are bounded. Every attempted provider request is logged. No real API calls are included in the tests.

Published initial text prices used by the inactive seed are $0.25 input, $0.025 cached input and $2.00 output per million tokens. Sources: [GPT-5 mini model/pricing](https://developers.openai.com/api/docs/models/gpt-5-mini), [Responses structured output format](https://developers.openai.com/api/docs/guides/structured-outputs). Model availability, production credentials and actual Hindi output quality have not been live-tested.

```text
USD estimate = ((input_tokens - cached_input_tokens) × input_price
                + cached_input_tokens × cached_price
                + output_tokens × output_price) / 1,000,000
INR estimate = USD estimate × applied USD/INR
Client billable INR = INR estimate × applied multiplier (default 3.00)
```

Java BigDecimal/PostgreSQL NUMERIC are used. Cached input tokens are a subset of input tokens and are not charged twice. Provider prompt-cache tokens still incur their configured rate; a local translation-cache hit makes no API request and costs zero new API charges. Failed requests with reported usage retain the corresponding estimate; missing/invalid usage is flagged as unknown. `reconciled_usd` is distinct from the calculated estimate; automatic provider invoice reconciliation is not implemented.

## Local testing

Tests require only a disposable loopback PostgreSQL database named `document_menu_test`. They refuse application database names and remote hosts. The fixture schema is cleaned between scenarios; this destructive test cleanup is confined to the disposable test database. All schema/fixture installation is through Flyway; application mutations occur through tested services.

From `dugar-lms-api`, with the isolated test cluster listening:

```powershell
$env:DOCUMENT_MENU_TEST_URL='jdbc:postgresql://127.0.0.1:55467/document_menu_test'
mvn -q '-Dtest=DocumentEnhancementIntegrationTest,OpenAiTranslationGatewayTest,DocumentAutomationAuthorizationTest,DocumentMenuMigrationTest,DocumentGenerationAccessTest,DocumentDocxRoundTripTest,WordDocumentGenerationServiceTest,WordTemplateStorageServiceTest' test
mvn -q -DskipTests package
```

The test cluster uses trust authentication for user `postgres`, exclusively on loopback. It is separate from the application's configured datasource. Without `DOCUMENT_MENU_TEST_URL`, database integration tests are skipped.

From `dugar-lms-frontend`:

```powershell
node --test scripts/testDocumentAutomation.mjs scripts/testDocumentMenuLabels.mjs
npm run build
```

Coverage includes existing English generation, language backfill, actual DOCX upload/replacement, Hindi preview/approval, aliases/duplicates/canonical collisions, exact source cache identities, concurrency, corrections/history, retained successful batches, bounded timeout/rate-limit retries, invalid/missing/incomplete/mixed-language output, numeric protection, provider usage/billing, batch allocation, failed audits, report scope/export, metadata authorization, formatting across split runs, font/bold/table/header/footer/page-break preservation and Flyway repeat validation. The report/export and UI tests use mocked provider transport; no token charges are incurred.

Final run results are recorded in `document-enhancement-test-results.txt`. Complete source/delivery file list: `document-enhancement-files.txt`. `document-enhancement-verification.sql` and the earlier `document-generation-verification.sql` contain read-only checks.

## Deployment runbook — instructions only, not executed

1. Inspect the actual ProLMS Flyway history, menu tree, roles, schema constraints and target connection identity with read-only access. Compare applied files/checksums; do not use Flyway repair to hide divergence. Repeat this assessment independently for CoreLMS before any future rollout.
2. Before V068, check active English template names for collisions under `(group_id, LOWER(BTRIM(template_name)))`. Existing case/space variants may fail the new index and require an explicitly reviewed migration. Check V067's required active roles/ancestors and V070's Reports fallback for ambiguity.
3. Take the normal database and template-storage backups. Rehearse the migration chain on a sanitized local/restored copy of the target environment. Verify PostgreSQL support, privileges, public-schema assumptions and connection-pool capacity (at least two connections; normal application headroom is also required).
4. Build/release backend and frontend together because template metadata now includes mandatory language/type fields. Set datasource/storage explicitly for the intended environment rather than relying on a local developer default. Migrations run through the existing Spring Boot/Flyway startup mechanism; do not execute these SQL files manually against application databases.
5. Start with `DOCUMENT_AI_ENABLED=false`; verify English generation and permissions after migration. Sign out/in to refresh cached menu trees and verify the new administration menus.
6. Configure the server-side key/model and active pricing/rate version. Review initial field conversion rules, upload actual English/Hindi variants under the same document key, and validate all placeholders. An authorized operator must separately approve any real API smoke test and review Hindi names/addresses before use.
7. Run the read-only verification queries and reconcile audit/usage counts and allocated costs. A problem should be corrected with a new forward Flyway migration or the normal recovery procedure, never by editing already-applied scripts.

## Explicit limits and unverified items

- Document output and document preview are DOCX. There is no validated DOCX-to-PDF converter in this repository, so PDF document generation was not added. Report PDF export uses the browser Print/Save as PDF workflow; printable HTML and filtering were tested, but the native print dialog/PDF rendering was not automated.
- No live OpenAI request, real Hindi quality assessment, live browser walkthrough or full LMS application suite was run. Tests cover this module with an isolated database; they do not establish complete production schema/history equivalence.
- Bulk support is an API/attribution workflow; the existing React screen remains a single-document workflow. No bulk ZIP interface was requested or added.
- New languages can be added to the catalogue/schema, but their conversion/formatting policy must be implemented explicitly; unsupported conversion languages fail rather than using another language silently.
- Successful finalization clears its transient preview. Expired/abandoned previews remain audit data until the deployment's retention policy removes their sensitive preview payloads; no automated retention job or provider-invoice reconciliation UI is included.
- A crash during FINALIZING leaves an auditable in-progress record that requires operational review. Durable generated-document archival/re-download is not introduced; the existing immediate-download model is preserved.
