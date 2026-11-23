-- Shared full-precision calculation for the public schedule and PDF totals.
-- No collections, contract mutations, final balancing, or EMI adjustments.
CREATE OR REPLACE FUNCTION public.fn_contract_repayment_schedule_unrounded(p_contract_number VARCHAR)
RETURNS TABLE (
    installment_no INTEGER,
    due_date DATE,
    opening_principal NUMERIC,
    emi_amount NUMERIC,
    interest_amount NUMERIC,
    principal_amount NUMERIC,
    closing_principal NUMERIC,
    rounding_adjustment NUMERIC
)
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
DECLARE
    v_contract public.contracts%ROWTYPE;
    v_slab RECORD;
    v_contract_json JSONB;
    v_frequency TEXT;
    v_terms TEXT;
    v_slab_count BIGINT;
    v_installments BIGINT;
    v_invalid_slabs BIGINT;
    v_repayment_total NUMERIC;
    v_balance NUMERIC;
    v_index INTEGER;
    v_installment_no INTEGER := 0;
BEGIN
    IF p_contract_number IS NULL OR btrim(p_contract_number) = '' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'Contract number is required';
    END IF;

    BEGIN
        SELECT c.* INTO STRICT v_contract
        FROM public.contracts c
        WHERE btrim(c.contract_number) = btrim(p_contract_number);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE EXCEPTION 'Contract not found: %', p_contract_number USING ERRCODE = '22023';
        WHEN TOO_MANY_ROWS THEN
            RAISE EXCEPTION 'Ambiguous contract number: %', p_contract_number USING ERRCODE = '22023';
    END;

    -- Some installations lack this column in their migration history. Inspect the
    -- contract row, not a default false value: missing/null status is unsupported.
    v_contract_json := to_jsonb(v_contract);
    IF NOT (v_contract_json ? 'is_first_emi_paid')
       OR v_contract_json ->> 'is_first_emi_paid' IS NULL THEN
        RAISE EXCEPTION 'Upfront EMI status is unavailable for contract: %', p_contract_number
            USING ERRCODE = '22023';
    END IF;
    IF v_contract_json ->> 'is_first_emi_paid' <> 'false' THEN
        RAISE EXCEPTION 'Upfront EMI contracts are unsupported in Stage 1: %', p_contract_number
            USING ERRCODE = '22023';
    END IF;
    IF NULLIF(lower(btrim(v_contract.repayment_type)), '') IS NOT NULL
       AND lower(btrim(v_contract.repayment_type)) <> 'emi' THEN
        RAISE EXCEPTION 'Unsupported repayment type for contract: %', p_contract_number
            USING ERRCODE = '22023';
    END IF;

    v_frequency := NULLIF(lower(btrim(v_contract.payment_frequency)), '');
    v_terms := NULLIF(lower(btrim(v_contract.repayment_terms)), '');
    IF (v_frequency IS NOT NULL AND v_frequency <> 'monthly')
       OR (v_terms IS NOT NULL AND v_terms <> 'monthly') THEN
        RAISE EXCEPTION 'Only consistent Monthly repayment frequency is supported: %', p_contract_number
            USING ERRCODE = '22023';
    END IF;

    IF v_contract.loan_amount IS NULL OR v_contract.loan_amount <= 0
       OR v_contract.loan_amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR v_contract.irr_rate IS NULL OR v_contract.irr_rate < 0
       OR v_contract.irr_rate::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR v_contract.first_emi_date IS NULL OR NOT isfinite(v_contract.first_emi_date)
       OR v_contract.tenure_months IS NULL OR v_contract.tenure_months <= 0
       OR v_contract.finance_charges IS NULL OR v_contract.finance_charges < 0
       OR v_contract.finance_charges::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR v_contract.total_contract_value IS NULL OR v_contract.total_contract_value <= 0
       OR v_contract.total_contract_value::TEXT IN ('NaN', 'Infinity', '-Infinity') THEN
        RAISE EXCEPTION 'Missing or invalid contractual financial inputs: %', p_contract_number
            USING ERRCODE = '22023';
    END IF;

    SELECT count(*), sum(rs.number_of_installments),
           sum(rs.number_of_installments::NUMERIC * rs.installment_amount),
           count(*) FILTER (WHERE rs.sequence_no IS NULL OR rs.sequence_no <= 0
               OR rs.number_of_installments IS NULL OR rs.number_of_installments <= 0
               OR rs.installment_amount IS NULL OR rs.installment_amount <= 0
               OR rs.installment_amount::TEXT IN ('NaN', 'Infinity', '-Infinity'))
    INTO v_slab_count, v_installments, v_repayment_total, v_invalid_slabs
    FROM public.contract_repayment_structures rs
    WHERE rs.contract_id = v_contract.contract_id;

    IF v_slab_count = 0 OR v_invalid_slabs <> 0
       OR v_installments IS DISTINCT FROM v_contract.tenure_months::BIGINT THEN
        RAISE EXCEPTION 'Missing, invalid, or tenure-inconsistent repayment structure: %', p_contract_number
            USING ERRCODE = '22023';
    END IF;
    IF v_slab_count <> (SELECT count(DISTINCT rs.sequence_no)
                       FROM public.contract_repayment_structures rs
                       WHERE rs.contract_id = v_contract.contract_id) THEN
        RAISE EXCEPTION 'Ambiguous repayment slab sequence: %', p_contract_number
            USING ERRCODE = '22023';
    END IF;
    IF v_repayment_total <> v_contract.total_contract_value
       OR v_repayment_total - v_contract.loan_amount <> v_contract.finance_charges THEN
        RAISE EXCEPTION 'Repayment totals do not reconcile with contract value and finance charges: %', p_contract_number
            USING ERRCODE = '22023';
    END IF;

    v_balance := v_contract.loan_amount;
    FOR v_slab IN
        SELECT rs.number_of_installments, rs.installment_amount
        FROM public.contract_repayment_structures rs
        WHERE rs.contract_id = v_contract.contract_id
        ORDER BY rs.sequence_no
    LOOP
        FOR v_index IN 1 .. v_slab.number_of_installments LOOP
            v_installment_no := v_installment_no + 1;
            installment_no := v_installment_no;
            -- Anchor each date to the original first EMI date to avoid month-end drift.
            due_date := (v_contract.first_emi_date
                         + (v_installment_no - 1) * INTERVAL '1 month')::DATE;
            opening_principal := v_balance;
            emi_amount := v_slab.installment_amount;
            interest_amount := opening_principal * v_contract.irr_rate / 1200::NUMERIC;
            principal_amount := emi_amount - interest_amount;
            closing_principal := opening_principal - principal_amount;
            rounding_adjustment := 0;

            IF principal_amount <= 0 OR interest_amount < 0
               OR (v_installment_no < v_installments AND closing_principal <= 0) THEN
                RAISE EXCEPTION 'Unsupported amortisation at installment % for contract %',
                    v_installment_no, p_contract_number USING ERRCODE = '22023';
            END IF;

            v_balance := closing_principal;
            RETURN NEXT;
        END LOOP;
    END LOOP;
END;
$function$;

-- Keep the existing public signature, columns, ownership and grants.
CREATE OR REPLACE FUNCTION public.fn_contract_repayment_schedule(p_contract_number VARCHAR)
RETURNS TABLE (
    installment_no INTEGER,
    due_date DATE,
    opening_principal NUMERIC,
    emi_amount NUMERIC,
    interest_amount NUMERIC,
    principal_amount NUMERIC,
    closing_principal NUMERIC,
    rounding_adjustment NUMERIC
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
    SELECT s.installment_no, s.due_date,
           round(s.opening_principal, 0), round(s.emi_amount, 0),
           round(s.interest_amount, 0), round(s.principal_amount, 0),
           round(s.closing_principal, 0), round(s.rounding_adjustment, 0)
    FROM public.fn_contract_repayment_schedule_unrounded(p_contract_number) s;
$function$;

COMMENT ON FUNCTION public.fn_contract_repayment_schedule(VARCHAR) IS
    'Read-only monthly schedule using stored IRR, full-precision balances and whole-rupee output. Blank frequency defaults to Monthly. No final reconciliation; rounding_adjustment is always zero.';
