# Reminder Letter migration baseline compatibility

The inspected development database already records V083 as successful. Its live
`pg_get_functiondef('public.fn_doc_reminder_letter(bigint,date)'::regprocedure)`
matches V083, including all thirteen output fields. No failed V083 history row
exists. PostgreSQL can roll back a failed transactional migration without leaving
a failed row, so absence of such a row alone does not prove no attempt occurred.

## Exact reason for the reported validation failure

V083 accepts only the V078 function body, whose MD5 after removing carriage returns
is `0220fd3131f8ac85adb4476d968c5fda`. The current function is the approved V083
body, whose corresponding MD5 is `e5a11a09c8d1b4a08ee00bcb3ec893ec`.
Executing V083 SQL again therefore raises its baseline exception. This was
reproduced in an isolated PostgreSQL/Flyway test.

The actual changes from V078 are exactly:

- `CURRENT_DUE`: use the oldest unpaid overdue FIFO balance instead of the next
  scheduled EMI balance.
- `TOTAL_OS`: use `tmp_contract_report.overdue_amount` instead of `total_outstanding`.
- `TOTAL_OS_WORDS`: pass that same rounded overdue amount to the existing formatter.
- Add the lateral lookup of `pg_temp.tmp_emi_outstanding`, restricted to balance
  greater than zero and due date strictly before the As-on Date, ordered by due
  date then EMI number, with no second deduction of payments.
- Require the EMI outstanding temporary table as well as the report temporary table.

Only V078 and V083 in the pre-existing migration directory modify this function.
V079 through V082 do not modify it. The recorded checksums for V078 (340230891)
and V083 (2016850703) match the workspace files; neither migration was edited.

## Forward-only correction

`V084__validate_reminder_financial_mapping_compatibility.sql` validates the complete
input/output signature, language, default date, and entire body against two embedded
approved definitions. It normalizes CRLF/LF and outer whitespace on both sides.
It does not accept arbitrary financial changes based on matching a few tokens.

- An approved V083 body is a no-op, including on SQL replay.
- An approved V078 body receives only the approved V083 body changes.
- An unknown body, missing function, or unexpected signature stops the migration.

For a V078 body, V084 reuses PostgreSQL's complete `pg_get_functiondef` header and
replaces only the validated body. CREATE OR REPLACE retains the function OID,
dependent views, owner, grants and comment. There is no DROP/CASCADE, history repair,
receipt allocation, data update, or change to either ageing procedure.

Normal Flyway migration skips the already successful V083 and applies V084.
**Do not rerun the V083 SQL file manually.** A database where V083 is still pending
and failing is a different recovery case: Flyway cannot skip that failure to reach
V084. Inspect that target's actual definition and history before deciding on its
recovery; do not mark V083 successful or edit shared migration history automatically.
The reported failure on another database cannot be diagnosed from this local
database's successful history alone.

All existing output columns remain, in order:

`BORROWER_NAME`, `BORROWER_SWD`, `VEHICLE`, `EMI`, `CURRENT_DUE`,
`NO_OF_OVERDUES`, `TOTAL_OS`, `TOTAL_OS_WORDS`, `GUARANTOR_1_NAME`,
`GUARANTOR_2_NAME`, `BORROWER_ADDRESS`, `CONTRACT_NUMBER`, `CONTRACT_DATE`.

## Verification

Read-only SELECTs using the live ageing calculation bodies and the current Reminder
projection were evaluated for As-on Date **24 Oct 2026**. Both live ageing procedures
produce the same overdue amounts; Demand List `current_due` remains unchanged.

| Contract | EMI | Reminder CURRENT_DUE | Overdues | Reminder TOTAL_OS / Demand List overdue_amount |
| --- | --- | --- | --- | --- |
| 13161 | 15,200.00 | 14,200.00 | 9 | 135,800.00 |
| 12755 | 26,000.00 | 12,000.00 | 9 | 220,000.00 |

Both contracts return all thirteen fields, with populated borrower address and
contract date. Existing English amount words agree with the overdue amount:
One Lakh Thirty Five Thousand Eight Hundred Rupees Only; Two Lakh Twenty Thousand
Rupees Only, respectively. Java mappings, translation caching, and Word templates
were not changed by this compatibility fix.

Testing in a fresh disposable PostgreSQL cluster passed:

- Eight financial regression tests, including both reference contracts, partial
  decimals, full payment, fully unpaid instalments, future instalment exclusion,
  zero overdue, date boundaries and missing temporary tables.
- Thirteen Java tests: ReminderLetterFunctionMigrationTest (5),
  ReminderLetterFinancialMigrationTest (3), ReminderLetterCompatibilityMigrationTest (5).
- The new tests cover Flyway migration/validation/repeat runs, V083 replay failure,
  V078 and V083 compatibility, all nonfinancial placeholder values, return shape,
  CRLF/LF tolerance, dependent views, OID/owner/grants/comment retention, and safe
  rejection of custom financial SQL or an altered output signature.

Reproduce from the API directory (requires PostgreSQL binaries, psycopg, Java and Maven):

```powershell
$env:RUN_REMINDER_JAVA_TESTS='1'
$env:REMINDER_JAVA_TEST_CLASSES='ReminderLetterFunctionMigrationTest,ReminderLetterFinancialMigrationTest,ReminderLetterCompatibilityMigrationTest'
python src/test/python/test_reminder_financial_mapping.py
```

Application database access during this investigation was read-only. No production
deployment, application migration command, template edit or Flyway history repair
was issued. V084 was exercised only in disposable tests.
