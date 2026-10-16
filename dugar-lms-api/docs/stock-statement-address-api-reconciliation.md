# Stock Statement: comma separators and exact API reconciliation

Verified locally on **15 October 2026**, backend Asia/Kolkata date, against `127.0.0.1:5434/dugar_lms`.

## Address correction

Removed the Stock Statement SQL's added ` / ` separator between area and city. The report now uses the same address component sequence as Demand List: address line 1, address line 2, area, city, state and PIN. Empty components are omitted; commas, whitespace and the ` - PIN` suffix follow Demand List's formatter. Stored slashes are untouched. The existing LEFT JOIN state-name mapping remains.

Contract **15691** now returns:

```text
JAFAR KHATALSAB SHAIKH
A/P-133/1 MIRZA PLOT NEAR, LAXMI MANDIR MURUM TAL, UMARGA, DHARASHIV, MAHARASHTRA - 413605
```

The real Demand List API handler returned the same raw address components for this contract, with state `S0005`. Its existing frontend `cleanText` and `formatPartyAddress` functions were executed directly from `DemandListGrid.jsx` against that response. After the explicitly required state-code lookup (`S0005` to `MAHARASHTRA`), the formatted name/address matched Stock Statement exactly. Demand List itself was not modified. No slash is added between UMARGA and DHARASHIV; `A/P-133/1` is preserved.

## Exact reconciliation

Used Spring MVC integration requests against the actual Stock Statement, Demand List and Excel endpoint handlers, with real local JDBC repositories/services, the existing procedure, and real SUPER_ADMIN menu permissions (user 1). No repository or calculation mocks were used. These are in-process API tests, not requests to a running browser or HTTP server. All procedure temporary-table work was rolled back after inspection.

- Database selection: exact `contract_details.stock_marked_to_bank = 'UNION BANK OF INDIA'`.
- Bank: active Bank Master ID 1.
- Bank-marked records: **51**, representing **51 distinct contract IDs**.
- `GET /api/reports/stock-statement?bankId=1`: HTTP **200**, **50 rows**, **50 distinct contract IDs**, contractCount **50**.
- Compared every database record's ID against the API response's IDs. The sole absent contract is **15650**, ID **4359**.
- Complete per-contract comparison: `stock-statement-api-reconciliation.csv`.

| Missing contract 15650: eligibility check | Actual result |
|---|---|
| Contract status / active | `Y` / true |
| Documentation active / bank | true / UNION BANK OF INDIA |
| Contract date | 06 October 2025, before reporting date |
| Loan closure date | NULL |
| Repayment structures | 2 slabs; 12 generated EMIs |
| Procedure active-contract stage | Included |
| Procedure ageing stage | Included |
| Procedure final contract report | Included |
| Total contract value | ₹94,400.00 |
| Total received, from existing procedure | ₹94,400.00 |
| Original principal | ₹80,000.00 |
| Principal outstanding | **₹0.00** |
| Interest outstanding | ₹0.00 |
| Total outstanding | ₹0.00 |
| Unpaid EMIs | **0** |
| Exact exclusion condition | Stock Statement `r.principal_outstanding > 0` |

All 51 marked records reached the procedure's final report. All had active contract/documentation records, status Y, no closure date, and repayment structures. The final report's positive-principal condition accounts for the entire difference. Contract 15650 is fully recovered under the existing procedure and is **legitimately excluded** from an outstanding-stock statement. No filtering correction is recommended or implemented.

## Accidental-exclusion checks

- All 51 documentation records match a real contract ID, so the contracts INNER JOIN loses none.
- All 51 match procedure results, so there is no missing financial result or schedule-related exclusion.
- Party and state joins are LEFT JOINs. Neither requires a matching master record for inclusion. The API has 50 distinct IDs with no join multiplication.
- Asset selection uses a LEFT LATERAL join with LIMIT 1 per contract. Its limit applies only to asset selection, not report contracts.
- EMI aggregation supplies installment details/counts per contract; it does not filter out report rows.
- Bank matching uses EXISTS and does not collapse or group contracts.
- There is no report-level DISTINCT, GROUP BY, LIMIT, OFFSET or pagination. The API response contains the complete 50-row result.

## Validation

- **41 backend tests passed**: 7 Stock Statement tests and 34 Demand List regression tests; backend compiled during the run.
- Frontend production build and targeted Stock Statement page ESLint passed. Existing bundle-size/Browserslist warnings remain.
- API and export integration requests returned 200. Every one of the **50 Excel borrower cells** was compared against its corresponding API value and matched exactly. The workbook has 50 data rows and no footer totals.
- Contract 15691 preserves the address's original slashes and follows the actual Demand List formatter after state resolution.
- No borrower master, contract, voucher, financial record, stored procedure, bank filter or business-rule filter was modified. No migration or deployment was performed.

## Changed files for this correction

- `src/main/java/dugar_lms_api/modules/reports/stockstatement/StockStatementRepository.java`: separate comma-delimited area and city components.
- `src/test/java/dugar_lms_api/modules/reports/stockstatement/StockStatementTest.java`: comma-component expectations and original-slash regression example.
- `docs/stock-statement.md`: updated mapping and verification links.
- `docs/stock-statement-address-api-reconciliation.md`: these findings.
- `docs/stock-statement-api-reconciliation.csv`: all 51 records compared to actual API output.
