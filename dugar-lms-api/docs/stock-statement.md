> Current enhancement: Stock Statement now requires an explicit `asOnDate` and filters on `contract_details.stock_marked_bank_id`. The notes below describe the original implementation. See [stock-bank-enhancements.md](stock-bank-enhancements.md) for current APIs, manual migration, file list and verification results.

# Bank-wise Stock Statement

Implemented locally. No production data changes or deployment were performed.

## Files

New backend files under `src/main/java/dugar_lms_api/modules/reports/stockstatement/`:

- `StockStatementController.java`: menu authorization, report/date/export endpoints, validation and changed-preview handling.
- `StockStatementService.java`: backend Asia/Kolkata date, shared report result, totals and content fingerprint.
- `StockStatementRepository.java`: existing Demand List procedure call and bank-filtered SQL projection.
- `StockStatementRow.java` and `StockStatementResponse.java`: shared preview/export data contracts.
- `StockStatementExcel.java`: streaming Apache POI XLSX export, formatting and worksheet rollover.

Other new files:

- `src/main/resources/db/migration/V065__add_stock_statement_menu.sql`
- `src/test/java/dugar_lms_api/modules/reports/stockstatement/StockStatementTest.java`
- `../dugar-lms-frontend/src/pages/Credit/reports/StockStatementPage.jsx`
- `../dugar-lms-frontend/src/services/stockStatementService.js`
- This implementation note.

Modified frontend files: `src/App.jsx` (permission-protected route) and `src/utils/menuRouteMap.js` (menu resolution).

Pre-existing working-tree changes, including Contract Edit and Demand List, were preserved.

## SQL and access

V065 originally expected a root menu code of `CREDIT`. The local database uses `CREDIT_1` and `REPORTS_12`, so V065 was recorded as applied without creating the report. Corrective migration `V066__fix_stock_statement_menu_integration.sql` reuses those existing parent records and adds the report without duplicates. It grants view permission on the report and its ancestors to active SUPER_ADMIN and CREDIT roles, preserving existing add/edit/delete permissions. V066 was applied through Flyway to local PostgreSQL at `127.0.0.1:5434/dugar_lms` on 15 October 2026. No unrelated menus or permissions were changed.

Local integration checks passed: the actual login `MenuService` returns Credit → Reports → Stock Statement for CREDIT (role 1) and SUPER_ADMIN (role 11), pointing to `/credit/reports/stock-statement`. Exactly one report menu exists. Repeating the correction SQL inside a rolled-back transaction created no duplicates. Before/after snapshots confirmed unrelated menus and permissions were identical. The frontend caches the login menu tree in localStorage, so existing sessions must sign out and sign in again; a browser refresh alone retains the cached tree.

No new procedure, financial table, bank mapping table or Business Date table is added. The existing `public.sp_branch_wise_ageing_test(date, varchar, varchar)` is called unchanged. Its temporary tables are read on the same transaction/connection. Bank filtering occurs in SQL against `contract_details.stock_marked_to_bank`, matched case-insensitively to the selected active bank's **name**, which is the value the current Contract Edit form stores. There is no ID/code fallback because that could associate a contract with a different bank.

The procedure applies its existing status, closure, receipt date, user-group and financial rules. The report additionally requires an active contract dated on/before the backend Business Date, positive principal outstanding, the selected bank, and the user's contract/area scope. Matching bank names must identify the intended bank uniquely; the existing name-based storage cannot distinguish duplicate bank names or follow a bank rename automatically.

## APIs

- `GET /api/reports/stock-statement/business-date`
- `GET /api/reports/stock-statement?bankId=1`
- `GET /api/reports/stock-statement/export?bankId=1&fingerprint=<generated-report-fingerprint>`

All endpoints require existing authentication and `CREDIT_REPORTS_STOCK_STATEMENT` view permission. No date is accepted from the browser. Per the user's clarification, Business Date means the backend's current server date in Asia/Kolkata. It is displayed as DD.MM.YYYY; contract dates use DD/MM/YYYY.

The export regenerates through the same service and compares its content fingerprint with the preview. If data, selected bank details or date changed, it returns 409 and requires regeneration. This avoids retaining large user snapshots in server memory and prevents downloading values that differ from the preview.

## Financial mappings

- Loan Amount: `tmp_contract_report.original_principal`, exactly as the current Demand List service maps its Loan Amount (the procedure derives this from contract value less finance charges).
- Principal Outstanding: `tmp_contract_report.principal_outstanding`, unchanged, excluding the procedure's interest outstanding.
- Total EMI: count of entries in the procedure's `tmp_emi_outstanding` schedule.
- Balance EMI: count of schedule entries with `emi_outstanding > 0`, including overdue and partially paid installments. Fully prepaid future entries are excluded.
- Principal per installment: as explicitly confirmed by the user, first unpaid scheduled EMI's full installment amount × original principal ÷ total contract value, rounded to two decimals. The procedure does not expose a dedicated field; this projects its existing proportional allocation basis. Principal outstanding and receipt allocation are never recalculated. For stepped EMI schedules, the first unpaid slab is used. A missing schedule produces zero installment amount/counts, consistent with the available procedure output. The user also explicitly confirmed that Balance EMI includes all unpaid installments, including arrears and future installments.
- Borrower: active Party Master name on the first line; address line 1, address line 2, area, city and state are separate comma-delimited components, followed by ` - PIN`, using the existing Demand List blank-component/comma/PIN formatting rules. No slash separator is introduced; slashes already stored in address fields are preserved. A LEFT JOIN from the party state to `g_others.other_code` displays `other_desc`; unmapped states and existing state names retain their original text. Repeated names within address components are preserved. The shared backend value is used unchanged by preview and Excel.
- Vehicle: first contract asset by asset ID, make and registration, with existing contract field fallbacks matching Contract Edit. The separately stored version/year is not appended.

## Validation

- Backend Maven compile passed.
- Seven Stock Statement tests cover backend date, summary values, validation, existing-procedure connection and parameter use, address punctuation/PIN/blank handling, XLSX headings/data/numeric dates/wrapping, absence of footer totals, empty output, denied access and stale export rejection.
- All 34 existing Demand List calculation, service and repository regression tests passed (41 tests total).
- Frontend production build passed, with existing bundle-size/Browserslist warnings.
- Targeted ESLint passed for the new page and service.

The menu follow-up used local database queries and Flyway execution, with login menu-service verification. No end-to-end browser exercise, financial reconciliation against real contracts, or large-volume benchmark was performed. The referenced Excel screenshot was not included among the supplied files; the implementation follows the supplied textual layout and does not include reference rows.

The subsequent address correction was verified with actual local contracts 15657, 15664, 15668, 15669 and 15670: their stored state `S0005` displays as `MAHARASHTRA` with the correct PIN. All 50 UNION BANK OF INDIA report borrower cells were compared with the generated XLSX and matched exactly. Existing state-name and unmapped-state fallbacks were also checked. The report count remains 50. Footer financial totals have been removed from both preview and Excel; the header contract count remains. Procedure execution used temporary calculation tables and the inspection transaction was rolled back, without master-data changes.

The final area/city separator correction and complete API reconciliation are recorded in `stock-statement-address-api-reconciliation.md`, with all 51 marked records in `stock-statement-api-reconciliation.csv`.

Bank filtering is performed before results leave the database, but the unchanged existing procedure still computes its full permitted portfolio first. This is the performance tradeoff required by the instruction to reuse it unchanged. Workbook rows use an SXSSF window of 100 and output streams; the generated report rows are still materialized for preview and totals.
