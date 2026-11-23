# Local Generate Document storage restoration

Investigated on 25 Oct 2026. The failure was an environment/storage mismatch,
not replacement of the Reminder Letter screen. No application source code,
configuration, database records, migrations or template contents were edited.

## Root cause

The route `/operations/transactions/generate-word-document` still loads
`GenerateWordDocument.jsx`. It selects `ReminderLetterGrid` when the selected
template's document key or name identifies Reminder Letter, otherwise it selects
the generic `ContractGrid`. With no selectable templates, the generic grid is
therefore expected fallback behavior; its 6,044-record count is not evidence of
the route having been replaced.

The page requests `GET /api/word-templates?isActive=true`. The backend validates
the physical DOCX before exposing a template for generation. For template-manager
users it returns unavailable entries with `hasFile=false`; the Generate Document
frontend filters these out. Generation-only users receive an already filtered
catalogue. Neither path offers missing templates.

Both registered templates were active and had valid language/document keys:

| ID | Language | Document key | Storage filename |
| --- | --- | --- | --- |
| 6 | en | REMINDER_LETTER | 36552f5a-32c3-4f05-9377-e7efed1480d3.docx |
| 7 | hi | REMINDER_LETTER | a7ea0f38-aa59-4c78-bcc3-68abde37f181.docx |

Neither file existed under the local backend's `storage/word-templates` directory.
The older DOCX files present locally have different storage filenames and were
not substituted for the registered originals.

## Verified local runtime

- Vite on localhost:5173 serves the current Generate Document component, including
  its Reminder grid import, and injects `VITE_API_BASE_URL=http://localhost:8080/api`.
- The API on localhost:8080 runs from this workspace's `dugar-lms-api` directory.
  Its compiled template and Reminder services were built at 12:47:51 and its JVM
  started at 12:47:56 on 25 Oct 2026. The runtime is not an older packaged backend.
- Source and compiled application properties agree on the datasource and storage
  settings. Default storage `./storage/word-templates` resolves under the API
  working directory. No `WORD_TEMPLATE_STORAGE_DIR` override was present in the
  diagnostic shell; the running service's behavior agrees with this storage path.
- The datasource is `jdbc:postgresql://127.0.0.1:5434/dugar_lms`. Port 5434 belongs
  to an SSH forwarding process targeting `prolms.asquareai.com:5432` via remote
  loopback. It is a production tunnel, not a workstation PostgreSQL instance.
  Localhost in this JDBC URL does not make the underlying database local.

V083/V084 change only the Reminder function's mapping/compatibility checks.
They do not deactivate templates, change storage filenames, alter language
filtering or replace the Generate Document route. They are unrelated to this
empty-dropdown problem and were left unchanged.

## Minimum safe correction applied

Located the two exact registered originals in
`/opt/dugar-lms/storage/word-templates` on the server. Downloaded them read-only,
checked SHA-256 against the remote originals, and restored only the two missing
local files. Creation used exclusive `xb` mode to prevent overwriting any existing
file. Both originals contain all thirteen Reminder placeholders.

| Language | SHA-256 |
| --- | --- |
| English | f841ad6ccb834af2d1405df6b998cc781569b32767bf79eb8bb1e70d86c81444 |
| Hindi | cf666b54c56a44551c406b4f1bb95e0e54fc9a1d4ce7670cdbd039dc06d73809 |

The files were not recreated from older templates, uploaded, renamed, edited or
replaced in the database. No remote files were changed. No backend restart or
deployment was performed.

A read-only diagnostic using the same compiled backend classpath and actual
`WordTemplateService.validFile` now reports:

```text
id=6; name=Reminder Letter; language=en; key=REMINDER_LETTER; active=true; hasFile=true
id=7; name=Reminder Letter; language=hi; key=REMINDER_LETTER; active=true; hasFile=true
```

This checks the service's real validation logic against the actual files and
database rows; it is not a captured authenticated browser HTTP response.
The catalogue checks availability on each request, so no code/configuration
change is required. Click **Refresh Templates** on the existing page or reload it.
The existing component can then select Reminder Letter and render its As-on Date,
Pending Due Loans / All Contracts, Refresh Calculations and financial grid.

## Verification and limits

- 39 Java tests passed: DocumentGenerationAccessTest (4),
  DocumentEnhancementIntegrationTest (34), DocumentDocxRoundTripTest (1).
- Eight financial regression tests passed in the same isolated test run.
- Frontend document suite: 21 passed, one existing unrelated preview-button test
  failed. It expects `View Template`, whereas the current screen provides
  `Download Template`. Catalogue/language, Reminder controls and grid, snapshot
  payload, single DOCX, bulk PDF, selection and refresh tests passed.
- All database-writing tests used a new disposable PostgreSQL cluster with no
  application credentials. Application database diagnostics used read-only
  connections. No live Reminder snapshot/export request was submitted, because
  those endpoints write snapshot/job records through the production tunnel.
- No browser was connected to the available UI tool, so a visual inspection of
  the user's authenticated page could not be performed. Current Vite assets,
  compiled backend availability and frontend behavior were verified independently.

For fully local generation tests, point the backend at an approved local database
copy with a matching template-storage folder. No such database target was supplied,
so the running datasource was not changed. Existing production database records
and the confirmed financial mappings were not modified by this task.
