# Reminder Letter contract placeholders ? 2026-10-17

## Changes

The fixed Reminder Letter now supports thirteen SQL fields. Added {{CONTRACT_NUMBER}} from contracts.contract_number and {{CONTRACT_DATE}} from contracts.contract_date. The date is formatted in PostgreSQL as DD Mon YYYY (for example 17 Oct 2026) using an explicit English month array, making both English and Hindi output independent of server lc_time. SQL null metadata becomes empty DOCX text through the existing mapper.

The existing JSON snapshot/result mapping already serializes every function output by column name. ReminderLetterService.CODES and its completeness check now accept all thirteen fields, so no new typed DTO, table column, alternate resolver or Java financial calculation is needed. The existing generic DOCX engine merges the added tokens in English and Hindi. Template upload validation recognizes them and the original-template validation endpoint uses the fixed report's field set. The new tokens are optional in uploaded DOCX files, preserving compatibility with existing eleven-placeholder templates.

Fixed-report conversion classifies both added fields as PRESERVE, even if a generic database field rule requests FORMAT or TRANSLATE. Neither is included in OpenAI request batches or translation-cache identities. Existing names/address conversion and cache reuse remain unchanged. Hindi dates retain the requested English abbreviation instead of being reformatted into Hindi.

## Inspection and migration

The configured local application database was inspected read-only. pg_get_functiondef confirmed the eleven-field PL/pgSQL function; pg_depend reported no incoming catalog-tracked dependencies. Source columns were verified as character varying and date. docs/reminder-letter-function-before-v078.sql records the inspected definition as a regression/reference artifact; it is not a migration and must not be run against production.

New V078__reminder_letter_contract_placeholders.sql appends the two return columns and metadata expressions to that definition. All original financial expressions, fn_doc_inr_words usage, temporary-table prerequisite, joins, EMI schedule selection, as-on date/default and eligibility filters are retained.

Changing RETURNS TABLE requires dropping/recreating the function. The migration uses a transactional DO block and DROP FUNCTION ... RESTRICT, never CASCADE. It restores the previous owner, effective execution grants/grant options and comment after recreation, and does not broaden PUBLIC execution permissions. If dependencies prevent dropping the function, the transaction fails and preserves the original function and dependent objects. PostgreSQL catalogs do not enumerate all textual PL/pgSQL/dynamic-SQL callers; retaining the same input signature and first eleven output names/order minimizes caller disruption. External consumers that depend on the exact SELECT * column count still need review.

A normalized source-body checksum and execution-setting preflight stop the migration if an environment has financial customizations or different execution behavior, rather than overwriting them. Halt for review and reconcile any environment-specific differences through a reviewed migration plan before applying V078; do not edit applied migration checksums or use CASCADE. If the function is absent, V078 creates the inspected definition with the two metadata fields. Normal database prerequisites for Aging Analysis and fn_doc_inr_words remain required.

No older migration was edited. V079 in integration tests is only the renamed fixture, not a release migration. No application/production database was changed, and no deployment or real OpenAI request was made. V078 was applied only to the disposable loopback test database. Apply it through the normal controlled Flyway process before using the updated backend, then add either token to the actual English/Hindi DOCX files through Upload/Replace as needed. Existing eleven-placeholder files do not need replacement merely to remain usable.

## Verification

See reminder-letter-contract-placeholder-test-results.txt for final commands/results. Migration tests execute the inspected baseline and V078 using disposable fixtures. They compare every original output value before/after migration, check all twelve English month abbreviations and null metadata, preserve the default date argument and eligibility/Aging prerequisites, preserve restricted grants/owner/comment, and verify rollback for dependent views or customized financial SQL.

Existing integration tests now exercise thirteen-field snapshot mapping and English/Hindi DOCX output, preserve existing cache reuse and procedure-call counts, assert metadata never enters mocked OpenAI payloads, and validate both new and legacy DOCX files. User-facing Reminder grid columns, Pending Due CURRENT_DUE > 0 and All Contracts scope/filter behavior remain unchanged.

## Files changed for this request

- src/main/resources/db/migration/V078__reminder_letter_contract_placeholders.sql (new)
- src/main/java/dugar_lms_api/modules/wordtemplates/automation/ReminderLetterService.java
- src/main/java/dugar_lms_api/modules/wordtemplates/automation/DocumentAutomationService.java (clarifying PRESERVE comment)
- src/main/java/dugar_lms_api/modules/wordtemplates/service/WordTemplateService.java
- src/main/java/dugar_lms_api/modules/wordtemplates/service/WordDocumentGenerationService.java
- src/test/java/dugar_lms_api/modules/wordtemplates/DocumentEnhancementIntegrationTest.java
- src/test/java/dugar_lms_api/modules/wordtemplates/ReminderLetterFunctionMigrationTest.java (new)
- docs/reminder-letter-function-before-v078.sql (new inspected reference)
- docs/reminder-letter-contract-placeholders.md (this report)
- docs/reminder-letter-contract-placeholder-test-results.txt (new)

During the full suite, the existing authentication regression test detected that a hardcoded fallback had reappeared in application.properties. The environment-only reference was restored under the prior security requirement; the test assertion was also changed to report only a boolean/static message, never the configuration value. Generated failed-test diagnostics were redacted, and the final build was rerun. These additional files changed:
- src/main/resources/application.properties (restored ${OPENAI_API_KEY:})
- src/test/java/dugar_lms_api/modules/wordtemplates/DocumentAiAuthenticationTest.java (secret-safe assertion)

All other existing workspace changes were preserved.
