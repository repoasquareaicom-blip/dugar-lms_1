CREATE OR REPLACE FUNCTION public.calculate_loan_irr(p_sanction_amount numeric, p_contract_date date, p_first_emi_date date, p_emi_numbers integer[], p_emi_amounts numeric[], p_is_first_emi_paid boolean)
 RETURNS numeric
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_cashflows numeric[] := ARRAY[]::numeric[];

    v_first_emi numeric := 0;
    v_initial_disbursement numeric;

    v_rate numeric := 0.02;
    v_new_rate numeric;
    v_npv numeric;
    v_derivative numeric;

    v_i integer;
    v_j integer;
    v_t integer;

    v_cf numeric;
    v_skip_first_emi boolean := false;

    v_tolerance numeric := 0.0000001;
    v_max_iterations integer := 1000;
BEGIN

    IF p_sanction_amount IS NULL
       OR p_sanction_amount <= 0 THEN
        RETURN NULL;
    END IF;

    IF p_emi_numbers IS NULL
       OR p_emi_amounts IS NULL
       OR array_length(p_emi_numbers, 1) IS NULL
       OR array_length(p_emi_amounts, 1) IS NULL THEN
        RETURN NULL;
    END IF;

    /*
       New explicit business rule:

       is_first_emi_paid = true
       means first EMI was collected upfront.

       Do NOT infer this from contract_date = first_emi_date.
    */
    IF COALESCE(p_is_first_emi_paid, false) THEN

        v_first_emi :=
            GREATEST(
                COALESCE(p_emi_amounts[1], 0),
                0
            );

        v_skip_first_emi := (v_first_emi > 0);

    END IF;

    /*
       Initial disbursement =
       sanction amount - upfront first EMI
    */
    v_initial_disbursement :=
        GREATEST(
            p_sanction_amount - v_first_emi,
            0
        );

    IF v_initial_disbursement <= 0 THEN
        RETURN NULL;
    END IF;

    v_cashflows :=
        array_append(
            v_cashflows,
            -v_initial_disbursement
        );

    /*
       Expand repayment slabs in sequence.
    */
    FOR v_i IN 1 .. LEAST(
        array_length(p_emi_numbers, 1),
        array_length(p_emi_amounts, 1)
    )
    LOOP

        IF COALESCE(p_emi_numbers[v_i], 0) <= 0
           OR COALESCE(p_emi_amounts[v_i], 0) <= 0 THEN
            CONTINUE;
        END IF;

        FOR v_j IN 1 .. p_emi_numbers[v_i]
        LOOP

            /*
               Skip only first scheduled EMI
               when it was collected upfront.
            */
            IF v_skip_first_emi THEN
                v_skip_first_emi := false;
                CONTINUE;
            END IF;

            v_cashflows :=
                array_append(
                    v_cashflows,
                    p_emi_amounts[v_i]
                );

        END LOOP;

    END LOOP;

    IF array_length(v_cashflows, 1) < 2 THEN
        RETURN NULL;
    END IF;

    /*
       Newton-Raphson IRR
    */
    FOR v_i IN 1 .. v_max_iterations
    LOOP

        v_npv := 0;
        v_derivative := 0;

        FOR v_t IN 0 .. array_length(v_cashflows, 1) - 1
        LOOP

            v_cf := v_cashflows[v_t + 1];

            v_npv :=
                v_npv +
                (
                    v_cf /
                    power(1 + v_rate, v_t)
                );

            IF v_t > 0 THEN

                v_derivative :=
                    v_derivative -
                    (
                        v_t *
                        v_cf /
                        power(1 + v_rate, v_t + 1)
                    );

            END IF;

        END LOOP;

        IF abs(v_derivative) < 0.000000000001 THEN
            RETURN NULL;
        END IF;

        v_new_rate :=
            v_rate - (v_npv / v_derivative);

        IF v_new_rate <= -1 THEN
            RETURN NULL;
        END IF;

        IF abs(v_new_rate - v_rate) < v_tolerance THEN

            RETURN ROUND(
                (v_new_rate * 12 * 100)::numeric,
                4
            );

        END IF;

        v_rate := v_new_rate;

    END LOOP;

    RETURN NULL;

END;
$function$;

CREATE OR REPLACE PROCEDURE public.sp_recalculate_contract_irr(IN p_contract_number character varying)
 LANGUAGE plpgsql
AS $procedure$
DECLARE
    v_contract_id bigint;
    v_loan_amount numeric;
    v_contract_date date;
    v_first_emi_date date;
    v_is_first_emi_paid boolean;

    v_emi_numbers integer[];
    v_emi_amounts numeric[];

    v_irr numeric;
BEGIN

    /*
       Read contract financial values
    */
    SELECT
        c.contract_id,
        c.loan_amount,
        c.contract_date,
        c.first_emi_date,
        COALESCE(c.is_first_emi_paid, false)
    INTO
        v_contract_id,
        v_loan_amount,
        v_contract_date,
        v_first_emi_date,
        v_is_first_emi_paid
    FROM public.contracts c
    WHERE c.contract_number = p_contract_number;

    IF v_contract_id IS NULL THEN
        RAISE EXCEPTION 'Contract not found: %', p_contract_number;
    END IF;

    /*
       Build repayment slab arrays
       in repayment sequence.
    */
    SELECT
        array_agg(
            rs.number_of_installments
            ORDER BY rs.sequence_no
        ),
        array_agg(
            rs.installment_amount
            ORDER BY rs.sequence_no
        )
    INTO
        v_emi_numbers,
        v_emi_amounts
    FROM public.contract_repayment_structures rs
    WHERE rs.contract_id = v_contract_id;

    IF v_emi_numbers IS NULL
       OR v_emi_amounts IS NULL THEN
        RAISE EXCEPTION
            'Repayment structure not found for contract: %',
            p_contract_number;
    END IF;

    /*
       Calculate IRR using explicit
       Is First EMI Paid flag.
    */
    v_irr := public.calculate_loan_irr(
        v_loan_amount,
        v_contract_date,
        v_first_emi_date,
        v_emi_numbers,
        v_emi_amounts,
        v_is_first_emi_paid
    );

    IF v_irr IS NULL THEN
        RAISE EXCEPTION
            'Unable to calculate IRR for contract: %',
            p_contract_number;
    END IF;

    /*
       Update only IRR.
    */
    UPDATE public.contracts
    SET irr_rate = v_irr
    WHERE contract_id = v_contract_id;

END;
$procedure$;
