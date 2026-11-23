# Fixed Reminder Letter implementation

Date: 2026-10-17. No deployment or application/production database mutation was performed. Only read-only local metadata/template catalogue inspection and disposable PostgreSQL tests were used. No real OpenAI calls were made.

## Behavior

Reminder Letter uses the existing Operations ? Transactions ? Generate Document page. An active word_templates record whose document_key or name is Reminder Letter (spaces or underscores, case-insensitive) selects the fixed report. The existing catalogue supplies English/Hindi variants; this change adds no administrator creation/editing flow and creates no template records or files.

The page shows an As-on Date defaulting to the business day in Asia/Kolkata, Pending Due Loans / All Contracts, loan-number/borrower-name search, and the six requested columns. Pending Due (default) means only CURRENT_DUE > 0, using the existing Ageing calculation directly. NO_OF_OVERDUES, OVERDUE_AMOUNT and TOTAL_OS are not Pending Due filter conditions. All Contracts means all function-eligible active, non-draft, open contracts in the user's scope. Search is literal, case-insensitive substring matching. Selection persists across pages and supports all matching rows. Date/filter/search/calculation changes invalidate the old selection. Existing Clear Result behavior, Word/PDF choice, original preview/download and export progress/errors remain.

## Connection and calculation guarantee

ReminderLetterService creates a REQUIRES_NEW, REPEATABLE_READ JDBC transaction for each calculation. JdbcTemplate and NamedParameterJdbcTemplate use the same datasource and transaction manager, so the procedure call and every lateral function read share the Spring-bound physical PostgreSQL connection. No function read relies on a temporary table from an earlier pooled connection.

A grid calculation calls public.sp_branch_wise_ageing_test_2(as_on_date, NULL::varchar) once, then materializes all 11 outputs of public.fn_doc_reminder_letter(contract_id, as_on_date) for eligible authorized contracts. Paging, search and Pending Due / All filters read this snapshot without running the procedure again. A changed date or Refresh Calculations requests a new snapshot.

Job submission freezes the authorized/filter-matching contract IDs from the displayed snapshot. The background export worker calls the procedure once for that export batch and materializes the function's 11 fields into its selected document_export_items. This recalculation uses the requested date and current database state; changes since the grid snapshot can therefore change the exported financial values. All function reads and materialization finish before the transaction commits and the connection is released. Translation, cache approvals and DOCX/PDF/ZIP rendering then use these immutable batch values. Finalization compares against those batch values and does not run Ageing again or invoke Java financial/address resolution.

The unchanged procedure uses ON COMMIT PRESERVE ROWS. The calculation explicitly drops its known pg_temp tables before committing. On failure PostgreSQL rolls the calculation back. Independent concurrent transactions have independent temporary namespaces. Integration tests exercise concurrent dates/exports and sequential reuse of the same connection, including temporary-table cleanup.

All 11 keys are mapped directly, including BORROWER_ADDRESS and TOTAL_OS_WORDS. SQL converts numeric JSON values to exact text before storage to preserve decimal precision/scale. There is no Java address assembly or financial calculation. For Hindi, names/SWD/vehicle/guarantors/address reuse dynamic-field translation and the PostgreSQL cache; numeric values are preserved, and TOTAL_OS_WORDS uses the existing deterministic Hindi amount formatter from SQL's TOTAL_OS. Static template text is not translated.

Owner, active-user, Generate-menu and current database user-group scope checks protect grid snapshots. Current eligibility/scope is rechecked on grid reads, selection, batch calculation, item generation and download. Direct legacy preview attempts on a Reminder Letter fail with an instruction to use the dated batch workflow, rather than silently using the old generic resolver.

## Migration and retention

New V075__reminder_letter_calculation_snapshots.sql is genuinely needed for connection-independent paging and rendering. It adds document_reminder_snapshots, document_reminder_rows and nullable document_export_items.document_values. No existing migration, function or Ageing procedure was edited. Apply V075 through normal Flyway after reviewing the target migration history; no application Flyway startup was run here.

Grid snapshots expire after 30 minutes and are removed by the existing minute cleanup scheduler. Export source values clear after the export's existing expiry; jobs and audit history are retained. Existing template/export storage and PDF configuration remain required. Snapshot count is bounded by the existing DOCUMENT_EXPORT_MAX_CONTRACTS setting (default 100,000); larger datasets fail explicitly.

## Verification and remaining prerequisites

Backend tests/build and frontend tests/build are recorded in reminder-letter-test-results.txt. Tests use only 127.0.0.1:55467/document_menu_test and fixture procedure/function definitions that assert session/date identity. They test integration wiring, not a reimplementation of the production financial formulas. The existing production function/procedure definitions were inspected read-only and left unchanged; their financial correctness remains the existing tested database contract.

The local catalogue inspected contained Reminder Letter in English only (template ID 5). A fixed Hindi DOCX/word_templates variant with document_key Reminder Letter must be provisioned through the existing controlled database/file process before Hindi can be offered; no English fallback or synthetic Hindi template was introduced.

Actual LibreOffice/font layout, live browser walkthrough, production schema/migration compatibility, real Hindi output quality and large-volume performance are not verified here. The repository POM is Spring Boot 4.1.0 / Java 21, despite the prompt's Spring Boot 3 description; no framework version was changed. The application-context test remains excluded because its hard-coded datasource can run Flyway against the application database; the stock-bank application-database integration test remains opt-in/skipped.

## Files changed for this request

- src/main/java/dugar_lms_api/modules/wordtemplates/automation/ReminderLetterService.java (new)
- src/main/java/dugar_lms_api/modules/wordtemplates/automation/DocumentExportService.java
- src/main/java/dugar_lms_api/modules/wordtemplates/automation/DocumentExportController.java
- src/main/java/dugar_lms_api/modules/wordtemplates/automation/DocumentExportRequest.java
- src/main/java/dugar_lms_api/modules/wordtemplates/automation/DocumentAutomationService.java
- src/main/resources/db/migration/V075__reminder_letter_calculation_snapshots.sql (new)
- src/test/java/dugar_lms_api/modules/wordtemplates/DocumentAutomationAuthorizationTest.java
- src/test/java/dugar_lms_api/modules/wordtemplates/DocumentEnhancementIntegrationTest.java
- ../dugar-lms-frontend/src/pages/word-documents/ReminderLetterGrid.jsx (new component, no new route/screen)
- ../dugar-lms-frontend/src/pages/word-documents/GenerateWordDocument.jsx
- ../dugar-lms-frontend/scripts/testDocumentAutomation.mjs
- docs/reminder-letter-implementation.md and docs/reminder-letter-test-results.txt

All other pre-existing workspace changes were preserved.
