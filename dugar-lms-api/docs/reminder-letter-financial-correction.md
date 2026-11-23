# Reminder Letter overdue and partial EMI correction

V083 corrects only `public.fn_doc_reminder_letter(bigint,date)`. Both ageing
procedures, Demand List mappings, repayment structures, receipts and DOCX files
are unchanged. The function retains its 13 output fields, scheduled EMI lookup,
eligibility filters, borrower metadata and existing English amount formatter.

The confirmed Monthly Part Amount Due rule is the oldest unpaid **overdue**
instalment; return zero if no such instalment exists. An instalment due on the
As-on Date is not overdue, matching the existing ageing procedure's strict `<`
date boundary.

| Placeholder | Source after V083 |
| --- | --- |
| `EMI` | Existing scheduled monthly EMI lookup, unchanged |
| `CURRENT_DUE` | `pg_temp.tmp_emi_outstanding.emi_outstanding` for this contract, where balance > 0 and due date < As-on Date, ordered by due date then EMI number, first row; zero if absent |
| `NO_OF_OVERDUES` | `pg_temp.tmp_contract_report.overdue_emi_count`, unchanged |
| `TOTAL_OS` | `ROUND(COALESCE(pg_temp.tmp_contract_report.overdue_amount,0),2)` |
| `TOTAL_OS_WORDS` | Existing `fn_doc_inr_words` applied to that same rounded overdue amount |

The partial amount is already net of receipts allocated by the existing FIFO
calculation. The function does not subtract receipts or reproduce the allocation.
Fully paid rows are skipped. A fully unpaid selected instalment retains its full
balance. Demand List `current_due` still means the next scheduled EMI balance.

Java continues passing all 13 SQL fields directly to placeholders. English words
come from SQL; Hindi words continue using the existing deterministic formatter
with `TOTAL_OS` as numeric input. Translation caching and DOCX rendering are
unchanged. Reminder grid headings now identify Monthly Part Amount Due and Total
Overdue EMI Amount. Its existing `CURRENT_DUE > 0` Pending Due Loans predicate
therefore selects contracts with unpaid overdue instalments under the confirmed
rule; All Contracts remains available for zero-overdue contracts.

## Verification on 24 Oct 2026

Read-only queries against documented development `127.0.0.1:5434/dugar_lms`
compared SELECTs extracted from both live ageing procedures and the corrected
function projection, using As-on Date `2026-10-24`. No procedure CALL or DML was
issued against that database during verification.

| Contract | Internal ID | EMI | Monthly Part Amount Due | Overdues | Reminder total and Demand List overdue amount |
| --- | --- | --- | --- | --- | --- |
| 13161 | 2136 | 15,200.00 | 14,200.00 (25 Jan 2026) | 9 | 135,800.00 |
| 12755 | 1887 | 26,000.00 | 12,000.00 (16 Feb 2026) | 9 | 220,000.00 |

English words were verified using the existing database formatter:

- 13161: One Lakh Thirty Five Thousand Eight Hundred Rupees Only.
- 12755: Two Lakh Twenty Thousand Rupees Only.

For 13161, Demand List total outstanding remains 196,200.00. Its 60,400.00
difference from overdue amount is the unpaid instalments on 25 Oct, 25 Nov and
25 Dec 2026 (15,200 each), plus 25 Jan 2027 (14,800).

## Migration and test status

V083 uses CREATE OR REPLACE and retains the function OID, dependencies, owner,
grants and comment. A baseline-body guard rejects unexpected custom financial
SQL. The older migrations have not been edited.

During development, a read-only check found V083 already applied by an existing
development Flyway runner at 13:38:20 on 24 Oct 2026. No migration command was
issued by this task against the application database. The recorded successful
V083 checksum (2016850703) matches the workspace migration. No production
database was targeted and no deployment was initiated.

Testing used a newly initialized, isolated PostgreSQL cluster with no application
credentials. The test fixture calls the existing V059 ageing procedure, whose
financial SELECTs match the inspected live `_test_2` procedure.

- Eight Python financial regression tests passed: both reference contracts,
  decimal partial payments, fully paid EMI exclusion, fully unpaid EMI balance,
  fully paid contracts, zero overdues, future partial EMI exclusion, date boundary,
  and missing ageing tables (some tests cover several assertions).
- All 48 selected Java tests passed: ReminderLetterFunctionMigrationTest (5),
  ReminderLetterFinancialMigrationTest (3), DocumentEnhancementIntegrationTest
  (34), DocumentAutomationAuthorizationTest (5), DocumentDocxRoundTripTest (1).
  These include Flyway validation/repeat-run checks, migration metadata retention,
  custom-function rejection, Java placeholder mapping, Hindi formatting, caching,
  document generation, snapshots and authorization.
- Frontend document suite: 21 passed, 1 existing unrelated failure. The original
  template preview test looks for a `View Template` button, while the current
  screen provides `Download Template`. The Reminder grid tests passed.

Reproduce financial tests from the API directory:

```powershell
python src/test/python/test_reminder_financial_mapping.py
```

To include the selected Java suites in the same disposable test run:

```powershell
$env:RUN_REMINDER_JAVA_TESTS='1'
python src/test/python/test_reminder_financial_mapping.py
```

Existing Reminder snapshots retain their stored values until expiry. Refresh
Calculations or reopen Generate Document before comparing or generating with
the corrected fields. The latest registered DOCX files were unavailable locally;
no template files were replaced or edited.
