# Repayment schedule ? Stage 1 strict Excel formulas

## Current correction

`src/main/resources/db/migration/V081__strict_excel_contract_repayment_schedule.sql` replaces only `public.fn_contract_repayment_schedule(VARCHAR)`. V079 and V080 remain unchanged, preserving applied Flyway checksums. Deploy in normal version order; this work does not deploy or run migrations against the application database or production.

V081 removes the final EMI reconciliation, the INR 1.00 residual cap, the principal/interest total accumulators, and the final exact-total/zero-balance validation. The existing return signature is unchanged. `rounding_adjustment` is always 0.00. Final balances of either sign are returned without adjustment, including residuals above INR 1.00.

## Calculation

For every installment, including the last, using PostgreSQL NUMERIC:

1. Opening principal = original `contracts.loan_amount` for installment 1; previous closing thereafter.
2. Interest = `ROUND(opening_principal * contracts.irr_rate / 1200, 2)`.
3. Principal = contractual EMI minus interest.
4. Closing = opening minus principal.
5. Rounding adjustment = 0.00.

The stored IRR is used verbatim and never solved or updated. EMI amounts come from the original repayment slabs in sequence order. Dates remain anchored to `first_emi_date + (installment_no - 1) * INTERVAL '1 month'`. No moratorium value is referenced or applied. PostgreSQL NUMERIC rounds ties away from zero, equivalent to HALF_UP for the supported nonnegative interest inputs.

The function remains STABLE, SECURITY INVOKER, with fixed `pg_catalog` search path. It reads only `public.contracts` and `public.contract_repayment_structures` and writes no records. Demand List, IRR routines, existing contracts/repayment structures, and application functionality are unchanged.

## Preserved validation

- Exactly one trimmed contract-number match across all contract types.
- Upfront status explicitly false; missing/null/true upfront flags remain unsupported.
- Explicit Monthly frequency in at least one of `payment_frequency`/`repayment_terms`; any populated conflicting or unsupported value is rejected.
- Populated `repayment_type` must be EMI.
- Required principal, IRR, date, tenure, finance charges, and contract value must remain valid and finite. Zero IRR is supported.
- Valid ordered repayment slabs, positive counts/amounts, and total installments matching tenure.
- Contractual slab payment totals must still reconcile with stored total contract value and stored finance charges plus loan amount. This input consistency check does not compare computed amortisation totals to stored principal/interest.
- Existing rejection of nonpositive principal repayment, negative interest, and payoff before the final installment is retained. A positive or negative final closing balance alone is not an error.

## Arithmetic discrepancy in the requested example

The supplied final interest of 326.44 and closing balance of -0.14 do not follow the mandated formulas with opening 15684.42, stored IRR 24.9970%, and EMI 16011.00:

```sql
SELECT ROUND(15684.42::NUMERIC * 24.9970::NUMERIC / 1200::NUMERIC, 2);
-- 326.72
```

The exact unrounded interest is 326.71953895. Therefore principal is 16011.00 - 326.72 = 15684.28 and closing is 15684.42 - 15684.28 = +0.14. The implementation follows the mandated formula without introducing a correction to manufacture the conflicting expected values.

## Verified PostgreSQL results

Tests use an isolated PostgreSQL 18.3 cluster and a synthetic copy of the supplied contract inputs (contract_id 6535, contract_number 17270, moratorium_months 1, upfront flag false, Monthly frequency, EMI repayment type). The live database record is not independently verified. The client workbook remains unavailable; the specification is the explicit formulas in the user instructions.

| Check | Strict formula result |
|---|---:|
| Rows | 24 |
| First date | 05-Nov-2026 |
| Last date | 05-Oct-2028 |
| Total EMI | INR 384264.00 |
| Total principal | INR 299999.86 |
| Total interest | INR 84264.14 |
| Final opening | INR 15684.42 |
| Final EMI | INR 16011.00 |
| Final interest | INR 326.72 |
| Final principal | INR 15684.28 |
| Final closing | +INR 0.14 |
| Rounding adjustment, every row | INR 0.00 |

All 18 integration tests passed. They cover all-row formula equivalence, all 24 returned installments, read-only execution with SELECT on only the two source tables and unchanged before/after records, read-only documented SQL queries, stepped slabs, half-paise rounding, month-end dates, zero IRR, both signs of final residuals above the former INR 1.00 limit, unknown/invalid inputs, and retained validation. Moratorium values 0, 1, 2, 12, and NULL produce identical schedules. Corrective replacement preserves function identity, owner, grants, and a dependent view.

The upgrade test executes V079, demonstrates its moratorium rejection, applies V080 and verifies its old final reconciliation, then applies V081 and verifies strict final arithmetic. Test fixture changes occur only in a disposable database. All test clusters are stopped after execution.

## Reproduction and read-only queries

From `dugar-lms-api`:

```text
python src/test/python/test_contract_repayment_schedule.py
```

Requires psycopg and PostgreSQL initdb/pg_ctl binaries on PATH. The runner creates a fresh localhost-only cluster, never reads application DB credentials, and retains logs in its printed temporary directory. Windows sandbox restrictions may require escalation to start the isolated server.

`docs/repayment-schedule-stage1-validation.sql` contains read-only schema/input, schedule, aggregate, date/continuity, and formula verification queries for an authorised local/test database after V081. They were executed against the isolated fixture. No production deployment or PDF integration has been performed.
