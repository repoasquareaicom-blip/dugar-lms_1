> Final design: VARCHAR numeric-string Bank IDs are now finalized locally; no BIGINT conversion was performed. See [final migration and validation results](stock-bank-varchar-final-summary.md). Earlier designs below are historical.

# Single-column Stock Bank storage ? prepared and verified

The updated application uses only `contract_details.stock_marked_to_bank BIGINT` for the bank reference. Bank Master supplies names for display. No schema or data changes were committed in this step. No AWS or production actions occurred. Database rehearsal DDL and test data were rolled back; the live column remains VARCHAR alongside the existing source column.

## Application behavior

- Contract Add/Edit now sends numeric `stockMarkedToBank` and receives numeric `stockMarkedToBank` plus computed `stockMarkedBankName` for display. It no longer sends/reads `stockMarkedBankId`.
- Stock Marking to Bank keeps its existing `bankId` API payload and uses the converted column internally. Its legacy-name fallback was removed.
- Stock Statement joins `bank_masters.id = contract_details.stock_marked_to_bank` and retains the existing date/procedure/fingerprint/Excel behavior.
- The contract import repository now types this field as nullable Long / JDBC BIGINT, so imports cannot bind VARCHAR to the converted field. Existing imports still leave this optional field null; financial calculations are untouched.
- No production Java or frontend source refers to `stock_marked_bank_id`, `stockMarkedBankId` or `legacyBankName`. Historical migration artifacts retain references intentionally and must not be rerun after this conversion.

## Backup and preflight

A full snapshot of all **6,106 contract_details rows**, including both original bank columns and every other field, was exported and row-count/header validated before rehearsal:

- `contract-details-full-before-single-column-20261015T094912Z.csv`
- SHA256 `a9e87da92b4b385c15ac695dc657599d9737f906b2fe1d9254e855ad53278523`
- Structure and discrepancy results: `stock-bank-single-column-preflight.json`.

At that repeatable-read snapshot, 51 source IDs were populated and 59 legacy names existed. Eight exact Cash Credit names had no source ID; every one resolved uniquely to Bank ID 1. There were no invalid IDs or conflicting names, and all 51 originally migrated contracts still had source ID 1. No database views, stored functions/procedures or user triggers referenced either bank field.

Live legacy-name edits are continuing: the later rollback rehearsal saw **62** named assignments, including **11** uniquely resolvable missing-source IDs. All converted to ID 1 in the rehearsal. These additional assignments are reported separately from the original 51; no names were discarded or guessed. Counts are point-in-time and must be rechecked at actual cutover.

## Prepared SQL and execution order

These files are outside Flyway and are not executed automatically:

1. `stock-bank-single-column-convert.sql`: stop local application writers and refresh the full database/table backup. Under locks in one transaction, create a complete database backup table `stock_bank_contract_details_backup_v2`; reject invalid source IDs; fill only uniquely matched missing source IDs (plus the previously approved generic Union Bank alias); stop on unmatched/ambiguous/conflicting names; convert the legacy field with `USING stock_marked_bank_id`; verify counts, preserved existing IDs and unrelated fields; add/index/validate its Bank Master FK. Any failed check rolls back the conversion and source-gap reconciliation. Reruns after conversion never recopy a potentially stale source column.
2. `stock-bank-single-column-validation.sql`: verify current/backup counts, no orphan IDs, no lost named assignments, preserved original IDs, original Union Bank contracts **51/51 at ID 1**, no duplicate details, and validated FK.
3. Start the updated backend/frontend together and test both editing screens, the bank dropdown and the report. The changed documentation payload requires a coordinated application/schema cutover; keep old application writers stopped.
4. Once code-reference and acceptance checks pass, run `stock-bank-single-column-retire-source.sql`. It requires the complete backup, BIGINT destination, validated FK and equal source/destination values before dropping the old source column. It uses no CASCADE and retains the full backup. If new application edits made the source stale, it stops for reconciliation rather than dropping inconsistent data.
5. Repeat the validation queries. Retain all original backups. Do not rerun the earlier two-column migrations, runners or validation scripts against the converted schema.

Actual cutover and source retirement remain unapplied; this request's preparation and rollback verification are complete. A fresh backup under stopped writers is still required for the final application/schema switch because data is actively changing.

## Validation results

- Frontend production build: passed. Existing bundle-size and Browserslist notices remain.
- Frontend tests: **8 passed**; targeted ESLint for the changed transaction page and report page passed.
- Backend tests: **69 passed**, including Contract Migration and existing Demand List regression tests.
- Local database integration: **1 passed** with conversion and source retirement inside a rollback transaction. Verified active-only search/save; numeric bank-ID writes; read-back in both directions between Contract Edit and Stock Marking; historical Stock Statement filtering on 30 September 2026; and changing a Bank Master name without losing contract mappings.
- SQL rehearsal: **6,106 rows before/after and in full backup**; **62 assigned / 0 orphan / 0 lost / 0 overwritten original IDs / 0 duplicate details**; original Union Bank **51/51 at ID 1**; validated FK.
- Conversion/source retirement and rerun: passed in rollback transaction. A deliberately conflicting name correctly rejected conversion and rolled back. Final live-schema checks confirm VARCHAR legacy column and BIGINT source column still present, no temporary backup table or test writes persisted.
- Raw SQL rehearsal results: `stock-bank-single-column-rehearsal.json`. Reusable rollback-only verifier: `rehearse_stock_bank_single_column.py`.
- Financial formulas and existing Demand List procedures were not modified.

## Modified application and test files

Paths relative to workspace root:

- `dugar-lms-api/src/main/java/dugar_lms_api/modules/contracts/dto/ContractDocumentationDraftDto.java`
- `dugar-lms-api/src/main/java/dugar_lms_api/modules/contracts/dto/StockBankMarkingDto.java`
- `dugar-lms-api/src/main/java/dugar_lms_api/modules/contracts/repository/ContractDocumentationDraftRepository.java`
- `dugar-lms-api/src/main/java/dugar_lms_api/modules/contracts/repository/StockBankMarkingRepository.java`
- `dugar-lms-api/src/main/java/dugar_lms_api/modules/contracts/service/ContractDocumentationDraftService.java`
- `dugar-lms-api/src/main/java/dugar_lms_api/modules/reports/stockstatement/StockStatementRepository.java`
- `dugar-lms-api/src/main/java/dugar_lms_api/migration/contract/ContractMigrationRepository.java`
- `dugar-lms-api/src/test/java/dugar_lms_api/modules/contracts/service/StockBankMarkingServiceTest.java`
- `dugar-lms-api/src/test/java/dugar_lms_api/modules/reports/stockstatement/StockStatementTest.java`
- `dugar-lms-api/src/test/java/dugar_lms_api/modules/reports/stockstatement/StockBankLocalIntegrationTest.java`
- `dugar-lms-frontend/src/pages/Credit/transaction/contract/ContractEditForm.jsx`
- `dugar-lms-frontend/src/pages/Credit/transaction/StockBankMarkingPage.jsx`

New artifacts under `dugar-lms-api/docs/manual-migrations/`: the three SQL scripts, full CSV backup, preflight/rehearsal JSON results, rollback-only Python verifier, and this handoff. Existing `docs/stock-bank-enhancements.md` links to this superseding design.
