> Final design: VARCHAR numeric-string Bank IDs are now finalized locally; no BIGINT conversion was performed. See [final migration and validation results](manual-migrations/stock-bank-varchar-final-summary.md). Earlier designs below are historical.

> Superseded storage design: the application now targets numeric `contract_details.stock_marked_to_bank`. See [single-column preparation and validation](manual-migrations/stock-bank-single-column-summary.md). Its conversion is prepared and rollback-tested, not committed. The two-column migration notes below are historical.

# Stock Statement date and bank ID enhancements

Implemented locally on 15 October 2026. The authorized manual database migration and menu grants are now complete on local development only; no deployment or automatic migration was performed. Existing working-tree edits were preserved. The repository uses React 19, Java 21 and Spring Boot 4.1.0; this change keeps its existing dependencies and architecture.

## Behavior

Stock Statement defaults its As On Date from the backend Asia/Kolkata clock. The date is editable. Changing either bank or date clears the generated preview and disables Excel until Generate succeeds. The selected date reaches the unchanged `sp_branch_wise_ageing_test` call and the existing report query. Financial formulas and Demand List source code are unchanged by this enhancement. Both heading and Excel use the selected date. The JSON response retains the existing `businessDate` field, now containing the requested reporting date.

Excel recomputes using the preview bank ID and date, then checks the fingerprint of the complete dataset (including bank details/date). A change produces HTTP 409 and invalidates the preview instead of downloading inconsistent data. Excel formatting is unchanged.

Stock Statement filters `contract_details.stock_marked_bank_id` through `bank_masters.id`. Bank names are read from Bank Master, so renaming a bank preserves assignments.

The new page is `/credit/transaction/stock-bank-marking`. Exact contract-number search restricts results to active contracts and displays contract date, borrower, amount, vehicle, status and current bank. Both this page and Contract Add/Edit save the same bank ID column. Active master banks are available for new assignments; an existing inactive assignment is displayed and may be retained. Shared backend validation locks the parent contract and validates the bank within the write transaction. The transaction also checks and locks an active contract before saving, and updates only the bank ID and audit fields; it creates a details row if one does not yet exist. The original text column is never overwritten by either screen. Unresolved legacy names remain visible as read-only legacy information until reconciled.

The new transaction checks `CREDIT_TRANSACTION_STOCK_BANK_MARKING` menu access and existing contract/area access on both search and save. It follows the current LMS page-level `can_view` permission model. The manually executed menu SQL grants view permission to the authorized active SUPER_ADMIN and CREDIT roles; it has now been executed on local development. Contract Add/Edit retains its existing access conventions.

## APIs

- `GET /api/reports/stock-statement/business-date` ? Kolkata default date, unchanged endpoint.
- `GET /api/reports/stock-statement?bankId=1&asOnDate=2026-09-30`
- `GET /api/reports/stock-statement/export?bankId=1&asOnDate=2026-09-30&fingerprint=...`
- `GET /api/stock-bank-marking/contracts/{contractNumber}`
- `PUT /api/stock-bank-marking/contracts/{contractId}` with JSON `{ "bankId": 1 }`.

The report endpoints require ISO `asOnDate`. New transaction saves require a bank. Contract Add/Edit supports an unassigned bank through nullable `stockMarkedBankId`; its existing `stockMarkedToBank` response field is display-only.

## Manual migration and reconciliation

All scripts are in `docs/manual-migrations`, outside Flyway:

1. Take a database snapshot. Run `stock-bank-preflight.sql` read-only to identify unique matches, unmatched/ambiguous names and duplicate contract detail rows.
2. During a maintenance window, run `stock-bank-id.sql`. It backs up every original detail-row mapping, adds nullable BIGINT `stock_marked_bank_id`, copies only unique case-insensitive trimmed-name matches, creates an index, and adds an initially unvalidated foreign key. It retains the legacy text and never replaces existing IDs. Reruns preserve the first backup and existing assignments. Active and inactive master banks participate in historical matching.
3. Run `stock-bank-validation.sql`. Check backup coverage, preserved legacy values, orphan IDs, counts, expected unique mappings, full mapping output, unresolved names and duplicate detail rows. Resolve ambiguous/unmatched names explicitly; do not select arbitrary candidates. Reconcile duplicate details before enabling the application; the editing screens use the earliest detail row.
4. Once all exceptions are reviewed, separately run `ALTER TABLE contract_details VALIDATE CONSTRAINT fk_contract_details_stock_bank;`.
5. Run `stock-bank-menu.sql` to register CREDIT ? Transaction ? Stock Marking to Bank. Assign view permission to intended roles through existing Role Menu Permissions, including ancestor visibility as needed. Sign out/in to refresh cached menus.
6. Enable the updated application only after reconciliation, and test both editing screens against the same contract plus historical preview/Excel against actual procedure results.

Do not drop the text column or backup table. Future legacy imports that still write bank names need a repeat reconciliation before their rows can appear in this ID-filtered report.

### Reconciliation result

**Migration completed on confirmed local development after explicit mapping approval.** All **51** named assignments now use Bank ID **1**; no unmatched records, orphan IDs, or duplicate details remain. All **6,106** detail rows were backed up and reconciled. The bank-name column and its values were preserved at migration commit, and the FK is validated. Bank ID 10, other contract fields and Demand List procedure definitions were unchanged. Menu **273** under Credit 1 / Transaction 7 is configured for active CREDIT and SUPER_ADMIN roles.

The locked snapshot contained 15 generic Union Bank names and 36 exact Cash Credit names; live edits had changed this split from the earlier 30/21 preflight. Subsequent live legacy-name edits are separately flagged against the backup; bank IDs remain reconciled. See [the completed migration summary](manual-migrations/stock-bank-migration-summary.md) and its complete validation artifacts for exact counts, backup hashes and post-commit observations. No production or AWS action was performed.

## Verification

- Frontend production build: passed (`npm run build`). Existing bundle-size/plugin timing and outdated Browserslist notices remain.
- Frontend handler/render tests: 8 passed (`node --test scripts/testStockStatementLoading.mjs scripts/testStockBankMarking.mjs`). Covers default date, date invalidation, selected-date generation/export, loading/error/retry behavior, contract summary, inactive selection preservation, bank ID payload, duplicate-save prevention and refreshed bank display.
- Targeted ESLint for StockStatementPage, StockBankMarkingPage and stockStatementService: passed.
- Backend targeted tests: 50 passed, including 1 local database acceptance test. Stock Statement 8; new bank marking service 5 and repository 2; existing Demand List calculation/repository/service tests 34. Covers selected historical date propagation, ID-based filtering, fingerprints, Excel formatting, contract scope, menu denial, shared bank validation, invalid/inactive bank rejection and retained inactive assignments.
- Authorized local database migration completed. The database acceptance test exercised actual historical report generation and bank filtering for 30 September 2026, cross-screen repository read-back and active-contract restrictions, with all test writes rolled back. No browser acceptance run was performed.

## Files changed for this request

Backend paths relative to `dugar-lms-api/`:

- `src/main/java/dugar_lms_api/modules/reports/stockstatement/StockStatementController.java`
- `src/main/java/dugar_lms_api/modules/reports/stockstatement/StockStatementService.java`
- `src/main/java/dugar_lms_api/modules/reports/stockstatement/StockStatementRepository.java`
- `src/main/java/dugar_lms_api/modules/contracts/dto/ContractDocumentationDraftDto.java`
- `src/main/java/dugar_lms_api/modules/contracts/dto/StockBankMarkingDto.java` (new)
- `src/main/java/dugar_lms_api/modules/contracts/repository/ContractDocumentationDraftRepository.java`
- `src/main/java/dugar_lms_api/modules/contracts/repository/StockBankMarkingRepository.java` (new)
- `src/main/java/dugar_lms_api/modules/contracts/service/ContractDocumentationDraftService.java`
- `src/main/java/dugar_lms_api/modules/contracts/service/StockBankMarkingService.java` (new)
- `src/main/java/dugar_lms_api/modules/contracts/controller/StockBankMarkingController.java` (new)
- `src/test/java/dugar_lms_api/modules/reports/stockstatement/StockStatementTest.java`
- `src/test/java/dugar_lms_api/modules/contracts/service/StockBankMarkingServiceTest.java` (new)
- `src/test/java/dugar_lms_api/modules/contracts/repository/StockBankMarkingRepositoryTest.java` (new)
- `docs/stock-statement.md` (current-behavior pointer)
- `docs/stock-bank-enhancements.md` (this handoff)
- `docs/manual-migrations/stock-bank-preflight.sql` (new)
- `docs/manual-migrations/stock-bank-id.sql` (new)
- `docs/manual-migrations/stock-bank-validation.sql` (new)
- `docs/manual-migrations/stock-bank-menu.sql` (new)

Frontend paths relative to `dugar-lms-frontend/`:

- `src/App.jsx`
- `src/utils/menuRouteMap.js`
- `src/services/stockStatementService.js`
- `src/pages/Credit/reports/StockStatementPage.jsx`
- `src/pages/Credit/transaction/contract/ContractEditForm.jsx`
- `src/pages/Credit/transaction/StockBankMarkingPage.jsx` (new)
- `scripts/testStockStatementLoading.mjs`
- `scripts/testStockBankMarking.mjs` (new)
