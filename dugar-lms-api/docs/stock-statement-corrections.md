# Stock Statement corrections and bank count investigation

Verified against local PostgreSQL `127.0.0.1:5434/dugar_lms` for the backend Asia/Kolkata reporting date **15 October 2026**.

## Vehicle display

`StockStatementRepository.java` now projects only the asset's vehicle make (with the Contract Edit contract-make fallback) and registration on separate lines. It no longer selects or appends `assets.version` or `contracts.equipment_model`. Preview and Excel use this same `vehicleDetails` value; no frontend or Excel-specific formatting fork was introduced.

For contract **15660**, the existing asset data is make `HYUNDAI XCENT VTVT SX`, version `2017`, registration `RJ01CD2898`. Actual repository output and an XLSX generated from that output were checked and both contain exactly:

```text
HYUNDAI XCENT VTVT SX
RJ01CD2898
```

Underlying asset and contract records were not changed.

## UNION BANK OF INDIA: 51 marked, 50 report rows

Bank Master ID **1**, active. There are **51 documentation records representing 51 distinct contracts**, so the discrepancy is not a duplicate-documentation count issue. All 51 have contract status `Y`, active contract/documentation records, no loan closure date, and existing repayment structures. The full list and per-contract procedure/report membership are recorded in `stock-statement-union-bank-audit.csv`.

The only missing contract is **15650**, contract ID **4359**.

| Check | Result |
|---|---|
| Contract date | 06 October 2025, before the reporting date |
| Contract status / active | `Y` / true |
| Documentation active / bank | true / UNION BANK OF INDIA |
| Loan closure date | NULL |
| First EMI date | 06 November 2025 |
| Repayment structure | 11 × ₹7,900 plus 1 × ₹7,500; 12 EMIs |
| Procedure active-contract stage | Included |
| Procedure ageing stage | Included |
| Procedure final contract report | Included |
| Original principal returned by procedure | ₹80,000.00 |
| Finance charges | ₹14,400.00 |
| Total contract value | ₹94,400.00 |
| Total received returned by procedure | ₹94,400.00 |
| Principal outstanding returned by procedure | **₹0.00** |
| Unpaid EMIs in procedure schedule | **0** |
| Stock Statement exclusion | Existing `r.principal_outstanding > 0` condition |

A separate read-only voucher check confirmed **15 authorised eligible voucher lines** totalling ₹94,400.00, with voucher dates from 07 November 2025 through 19 August 2026. The check used the existing procedure's ledger 3001/4201, authorised-status, HJ-exclusion, contract sub-ledger, and reporting-date rules.

**Recommendation:** retain the current filtering. Contract 15650 is fully recovered under the existing Demand List calculation rules and has no principal stock outstanding. The broad documentation count includes it because that query does not check outstanding balances. Neither missing schedules nor missing financial data caused its exclusion. Its still-active status and NULL closure date do not override its zero principal balance. No filtering or financial-calculation changes were made.

## Validation and changed files

- `src/main/java/dugar_lms_api/modules/reports/stockstatement/StockStatementRepository.java`: vehicle projection correction only; bank, access and outstanding filters unchanged.
- `src/test/java/dugar_lms_api/modules/reports/stockstatement/StockStatementTest.java`: actual make/registration example used by workbook consistency checks.
- `docs/stock-statement.md`: corrected vehicle mapping documentation.
- `docs/stock-statement-corrections.md`: these findings.
- `docs/stock-statement-union-bank-audit.csv`: all 51 marked contracts, financial output and inclusion flags.

All **40 tests passed**: six Stock Statement tests and 34 existing Demand List calculation/service/repository tests. The updated backend was compiled as part of that run. A local integration check used the real repository and existing procedure, reproduced exactly 50 report rows, compared every marked contract, and reopened the generated XLSX to verify contract 15660's vehicle cell matches its preview value.

The investigation issued SELECTs and invoked the existing report procedure, whose writes are confined to temporary calculation tables. The procedure/comparison transaction was rolled back. No contract, asset, voucher, financial, permission, menu, or stored-procedure definitions were changed. No migration or AWS deployment was performed. Browser interaction was not exercised.
