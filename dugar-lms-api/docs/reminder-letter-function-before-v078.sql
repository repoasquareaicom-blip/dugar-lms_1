-- Read-only local function definition inspected before V078; regression/reference only.
CREATE OR REPLACE FUNCTION public.fn_doc_reminder_letter(p_contract_id bigint, p_as_on_date date DEFAULT CURRENT_DATE)
 RETURNS TABLE("BORROWER_NAME" text, "BORROWER_SWD" text, "VEHICLE" text, "EMI" numeric, "CURRENT_DUE" numeric, "NO_OF_OVERDUES" integer, "TOTAL_OS" numeric, "TOTAL_OS_WORDS" text, "GUARANTOR_1_NAME" text, "GUARANTOR_2_NAME" text, "BORROWER_ADDRESS" text)
 LANGUAGE plpgsql
AS $function$
BEGIN
    -- Do not silently return stale or incorrect financial values.
    IF to_regclass('pg_temp.tmp_contract_report') IS NULL THEN
        RAISE EXCEPTION 'Aging Analysis has not been run on this connection. CALL public.sp_branch_wise_ageing_test_2(%, NULL) first.', COALESCE(p_as_on_date, CURRENT_DATE);
    END IF;

    RETURN QUERY
    SELECT
        NULLIF(TRIM(CONCAT_WS(' ', NULLIF(TRIM(b.salutation), ''), NULLIF(TRIM(b.full_name), ''))), '')::text,
        COALESCE(b.swd_name, '')::text,
        COALESCE(NULLIF(TRIM(CONCAT_WS(' ', NULLIF(TRIM(a.vehicle_make), ''), NULLIF(TRIM(a.version), ''))), ''), NULLIF(TRIM(c.vehicle_make), ''), '')::text,
        COALESCE(em.installment_amount, 0)::numeric,
        ROUND(COALESCE(r.current_due, 0), 2)::numeric,
        COALESCE(r.overdue_emi_count, 0)::integer,
        ROUND(COALESCE(r.total_outstanding, 0), 2)::numeric,
        public.fn_doc_inr_words(ROUND(COALESCE(r.total_outstanding, 0), 2))::text,
        COALESCE(g1.full_name, '')::text,
        COALESCE(g2.full_name, '')::text,
        (
            CONCAT_WS(', ',
                NULLIF(BTRIM(COALESCE(b.address_line_1, ''), ' ,.'), ''),
                NULLIF(BTRIM(COALESCE(b.address_line_2, ''), ' ,.'), ''),
                NULLIF(BTRIM(COALESCE(b.area, ''), ' ,.'), ''),
                NULLIF(BTRIM(COALESCE(b.city, ''), ' ,.'), ''),
                NULLIF(BTRIM(COALESCE(NULLIF(go.other_desc, ''), b.state, ''), ' ,.'), '')
            )
            || CASE
                WHEN NULLIF(BTRIM(COALESCE(b.pin_code, ''), ' ,.'), '') IS NOT NULL
                THEN ' - ' || BTRIM(b.pin_code, ' ,.')
                ELSE ''
            END
        )::text
    FROM public.contracts c
    JOIN pg_temp.tmp_contract_report r ON r.contract_id = c.contract_id
    LEFT JOIN public.party_masters b ON b.party_code = c.borrower_code
    LEFT JOIN LATERAL (
        SELECT o.other_desc
        FROM public.g_others o
        WHERE TRIM(o.other_code) = TRIM(b.state)
        ORDER BY o.other_desc
        LIMIT 1
    ) go ON TRUE
    LEFT JOIN public.party_masters g1 ON g1.party_code = c.guarantor_code
    LEFT JOIN public.party_masters g2 ON g2.party_code = c.guarantor_2_code
    LEFT JOIN LATERAL (
        SELECT aa.vehicle_make, aa.version
        FROM public.assets aa
        WHERE aa.contract_id = c.contract_id
        ORDER BY aa.asset_id
        LIMIT 1
    ) a ON TRUE
    LEFT JOIN LATERAL (
        -- Find EMI structure effective on the requested date, using the
        -- same month-by-month schedule convention as Aging Analysis.
        WITH structure AS (
            SELECT rs.sequence_no, rs.number_of_installments, rs.installment_amount,
                   COALESCE(SUM(rs.number_of_installments) OVER (
                       ORDER BY rs.sequence_no
                       ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
                   ), 0)::integer AS previous_installments
            FROM public.contract_repayment_structures rs
            WHERE rs.contract_id = c.contract_id
        ), schedule AS (
            SELECT s.installment_amount,
                   (c.first_emi_date + ((s.previous_installments + gs.n - 1) * INTERVAL '1 month'))::date AS due_date
            FROM structure s
            CROSS JOIN LATERAL generate_series(1, s.number_of_installments) AS gs(n)
            WHERE c.first_emi_date IS NOT NULL
        )
        SELECT schedule.installment_amount
        FROM schedule
        ORDER BY
            CASE WHEN schedule.due_date >= COALESCE(p_as_on_date, CURRENT_DATE) THEN 0 ELSE 1 END,
            CASE WHEN schedule.due_date >= COALESCE(p_as_on_date, CURRENT_DATE) THEN schedule.due_date END ASC NULLS LAST,
            schedule.due_date DESC
        LIMIT 1
    ) em ON TRUE
    WHERE c.contract_id = p_contract_id
      AND c.is_active IS TRUE
      AND c.is_draft IS FALSE
      AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'
      AND c.loan_close_date IS NULL;
END;
$function$
