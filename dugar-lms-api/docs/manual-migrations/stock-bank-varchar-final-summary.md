# Stock Marked Bank VARCHAR ID storage ? finalized locally

The current design uses only `contract_details.stock_marked_to_bank VARCHAR(100)` to store a canonical Bank Master ID string, such as `'1'` or `'10'`. **Its datatype was not changed.** The redundant `stock_marked_bank_id` column was removed only after validation and runtime-source/database dependency checks.

## Final database results

- Confirmed target: `127.0.0.1:5434/dugar_lms`, the authorized local/development connection. The server identifies itself as loopback port 5432 behind forwarding.
- Total contract-detail rows before/after: **6,106 / 6,106**.
- Assignments reconciled: **75**, all now exactly **`'1'`**.
- The locked final snapshot contained 74 numeric `'1'` values and one exact Cash Credit bank name. **One value was rewritten**, and all 75 assignments were verified.
- Original migrated Union Bank contracts: **51/51 retain ID 1**.
- Unmatched/ambiguous values, conflicting source IDs, orphan IDs, lost mappings and duplicate detail rows: **0**.
- Bank Master, including **TERM LOAN ID 10**, and all unrelated contract fields were verified unchanged. No other bank mappings were reassigned.
- Final reference column: `stock_marked_to_bank VARCHAR(100) NULL`. `stock_marked_bank_id` is absent.
- A validated numeric-string CHECK prevents bank names being reintroduced into the field. Bank existence is validated against Bank Master during migration and by the existing application save validation.
- Both Demand List procedure definitions and financial calculations remain unchanged.

The operation held an exclusive contract-details lock throughout backup, validation, normalization and source retirement. Other clients were idle and no competing writer lock was present at commit. The source-column drop used no CASCADE. No production or AWS action occurred.

## Backup

Complete pre-migration CSV of all 6,106 rows and every field, including both bank columns:

- `contract-details-varchar-final-20261015T100918Z.csv`
- SHA256 `edba026de2233db037d90f612143ece2ff9c84ec436e05c91763fd8fb1f44382`

A complete database snapshot table, `stock_bank_varchar_backup_final`, also retains the pre-migration data and both original columns. The historical backup tables/files were retained.

## Application behavior

Contract Add/Edit and Stock Marking retain numeric IDs in their JSON/UI models and validate bank existence/active status through the shared service. The JDBC repositories explicitly convert those IDs to strings and bind VARCHAR for storage. Contract Edit returns the bank's current name as computed `stockMarkedBankName`; Stock Marking returns `bankName`.

All three flows resolve banks using `bank_masters.id::text = contract_details.stock_marked_to_bank`. Reads obtain the numeric API ID from the joined master record; no unsafe VARCHAR-to-BIGINT column cast is needed. Stock Statement's date handling, existing procedure call, formulas and Excel fingerprint behavior are unchanged. The importer also binds this optional field as VARCHAR.

No production Java or frontend source depends on `stock_marked_bank_id` or `stockMarkedBankId`. Existing frontend controls already send bank IDs, so no new frontend edit was necessary in this final step.

## Validation

- **71 backend tests passed**, including Contract Migration, Demand List regression, Stock Statement, bank validation and actual local database integration after the redundant source column was removed.
- Database integration verified numeric string storage of `'1'` and `'10'`, cross-screen read-back in both directions, active-only search/save, historical Stock Statement bank filtering on 30 September 2026, and bank renaming without losing mappings. Test writes were rolled back; no test audit values remain.
- **8 frontend tests passed** for search, bank-ID payloads, inactive assignment retention, loading/error handling, duplicate submission prevention, and selected-date preview/export.
- **8 resolver cases passed**: both approved Union Bank names map to 1; TERM LOAN name and `'10'` map to 10; canonical/leading-zero numeric IDs normalize; unknown and out-of-range values are reported unmatched.
- Independent post-commit validation confirmed VARCHAR only, 6,106 rows, 75 assignments, 51 original contracts on ID 1, no unmatched/lost/orphan/duplicate mappings, and a validated numeric-string constraint.
- Updated local backend restarted with automatic Flyway migrations disabled. Health endpoint on port 8080 returned **UP**.
- Existing menu **273** remains available under CREDIT ? Transaction to active CREDIT and SUPER_ADMIN roles.

## SQL and audit artifacts

All paths below are in `docs/manual-migrations/` and are manual-only, outside Flyway:

- `stock-bank-varchar-id.sql`: numeric-string reconciliation and full backup, with no datatype conversion.
- `stock-bank-varchar-retire-source.sql`: validated removal of the redundant column.
- `stock-bank-varchar-validation.sql`: final reconciliation queries.
- `stock-bank-varchar-resolver.sql`: ID/name matching used by preflight and migration.
- `finalize_stock_bank_varchar.py`: local-only guarded transactional runner.
- `stock-bank-varchar-final-preflight.json`: unmatched/conflict results before migration.
- `stock-bank-varchar-final-results.json`: complete committed migration results and test status.
- `stock-bank-varchar-post-integration-validation.json`: independent validation results after integration tests.
- `stock-bank-varchar-resolver-tests.json`: alias and TERM LOAN preservation cases.

Earlier BIGINT-conversion/source-copy scripts and blocked/rehearsal handoffs are historical and superseded. Do not execute them against this finalized VARCHAR schema.

## Application/test files modified in this final step

Relative to `dugar-lms-api/`:

- `src/main/java/dugar_lms_api/modules/contracts/repository/ContractDocumentationDraftRepository.java`
- `src/main/java/dugar_lms_api/modules/contracts/repository/StockBankMarkingRepository.java`
- `src/main/java/dugar_lms_api/modules/reports/stockstatement/StockStatementRepository.java`
- `src/main/java/dugar_lms_api/migration/contract/ContractMigrationRepository.java`
- `src/test/java/dugar_lms_api/modules/contracts/repository/StockBankMarkingRepositoryTest.java`
- `src/test/java/dugar_lms_api/modules/reports/stockstatement/StockStatementTest.java`
- `src/test/java/dugar_lms_api/modules/reports/stockstatement/StockBankLocalIntegrationTest.java`

New migration/audit files are listed above. `docs/stock-bank-enhancements.md` and the former single-column summary now point to this final design. Existing unrelated working-tree changes were preserved.
