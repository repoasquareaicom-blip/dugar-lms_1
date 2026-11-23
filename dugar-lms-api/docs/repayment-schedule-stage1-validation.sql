-- Read-only verification for an authorised LOCAL/TEST database after V081
-- has been applied there. This script does not install or migrate anything.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '15s';

-- Verify actual field types and the installation-specific upfront flag.
SELECT table_name, column_name, data_type, numeric_precision, numeric_scale
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('contracts', 'contract_repayment_structures')
  AND column_name IN (
      'contract_id', 'contract_number', 'contract_type', 'loan_amount', 'irr_rate',
      'first_emi_date', 'tenure_months', 'finance_charges', 'total_contract_value',
      'payment_frequency', 'repayment_terms', 'repayment_type',
      'moratorium_months', 'is_first_emi_paid', 'sequence_no',
      'number_of_installments', 'installment_amount'
  )
ORDER BY table_name, ordinal_position;

-- Must identify exactly one contract (expected id 6535, moratorium 1).
-- Upfront status must explicitly be false. Moratorium does not alter the schedule.
SELECT c.contract_id, c.contract_number, c.contract_type,
       c.loan_amount, c.irr_rate, c.first_emi_date, c.tenure_months,
       c.finance_charges, c.total_contract_value,
       c.payment_frequency, c.repayment_terms, c.repayment_type,
       c.moratorium_months,
       to_jsonb(c) ? 'is_first_emi_paid' AS upfront_flag_column_present,
       to_jsonb(c) ->> 'is_first_emi_paid' AS upfront_flag
FROM public.contracts c
WHERE btrim(c.contract_number) = '17270';

SELECT rs.sequence_no, rs.number_of_installments, rs.installment_amount
FROM public.contract_repayment_structures rs
JOIN public.contracts c ON c.contract_id = rs.contract_id
WHERE btrim(c.contract_number) = '17270'
ORDER BY rs.sequence_no;

SELECT * FROM public.fn_contract_repayment_schedule('17270')
ORDER BY installment_no;

-- Strict arithmetic gives interest 326.72 on the final opening of 15684.42.
-- The requested 326.44 / -0.14 example is inconsistent with the supplied formula.
SELECT round(15684.42::NUMERIC * 24.9970::NUMERIC / 1200::NUMERIC, 2)
    AS final_interest_by_required_formula; -- 326.72

-- Expected: 24 | 384264.00 | 299999.86 | 84264.14 | 0.14 | 0.00 | true
WITH schedule AS (
    SELECT * FROM public.fn_contract_repayment_schedule('17270')
), totals AS (
    SELECT count(*) AS row_count,
           sum(emi_amount) AS total_repayment,
           sum(principal_amount) AS total_principal,
           sum(interest_amount) AS total_interest,
           max(closing_principal) FILTER (WHERE installment_no = 24) AS final_closing,
           sum(rounding_adjustment) AS total_rounding_adjustment
    FROM schedule
)
SELECT *,
       row_count = 24
       AND total_repayment = 384264.00
       AND total_principal = 299999.86
       AND total_interest = 84264.14
       AND final_closing = 0.14
       AND total_rounding_adjustment = 0.00 AS expected_17270_totals_pass
FROM totals;

-- Expected: true for every invariant check, including the final installment.
WITH schedule AS (
    SELECT *, lag(closing_principal) OVER (ORDER BY installment_no) AS previous_closing
    FROM public.fn_contract_repayment_schedule('17270')
)
SELECT bool_and(emi_amount = principal_amount + interest_amount
                AND closing_principal = opening_principal - principal_amount) AS row_equations_pass,
       bool_and(opening_principal = COALESCE(previous_closing, 300000.00)) AS continuity_pass,
       bool_and(due_date = (DATE '2026-11-05' + (installment_no - 1) * INTERVAL '1 month')::DATE)
           AS dates_pass,
       bool_and(interest_amount = round(opening_principal * 24.9970::NUMERIC / 1200::NUMERIC, 2))
           AS interest_formula_pass,
       bool_and(rounding_adjustment = 0.00) AS no_adjustment_pass
FROM schedule;

ROLLBACK;
