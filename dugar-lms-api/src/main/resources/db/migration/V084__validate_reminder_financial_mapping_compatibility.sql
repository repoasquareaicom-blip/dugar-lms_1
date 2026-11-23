-- Forward-only compatibility migration. V083 is already applied in development.
-- Accept either the complete V078 body or the complete approved V083 body.
-- CRLF/LF and outer whitespace are immaterial; other body changes are rejected.
-- No data, ageing procedures, return columns, templates or Flyway history are changed.
DO $migration$
DECLARE
    existing pg_proc%ROWTYPE;
    definition text;
    approved_v078 constant text := $v078$
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
        )::text,
        c.contract_number::text,
        CASE WHEN c.contract_date IS NULL THEN NULL ELSE
            TO_CHAR(c.contract_date, 'DD') || ' ' ||
            (ARRAY['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'])[EXTRACT(MONTH FROM c.contract_date)::integer]
            || ' ' || TO_CHAR(c.contract_date, 'YYYY')
        END::text
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
$v078$;
    approved_v083 constant text := $v083$
BEGIN
    -- Do not silently return stale or incorrect financial values.
    IF to_regclass('pg_temp.tmp_contract_report') IS NULL
       OR to_regclass('pg_temp.tmp_emi_outstanding') IS NULL THEN
        RAISE EXCEPTION 'Aging Analysis has not been run on this connection. CALL public.sp_branch_wise_ageing_test_2(%, NULL) first.', COALESCE(p_as_on_date, CURRENT_DATE);
    END IF;

    RETURN QUERY
    SELECT
        NULLIF(TRIM(CONCAT_WS(' ', NULLIF(TRIM(b.salutation), ''), NULLIF(TRIM(b.full_name), ''))), '')::text,
        COALESCE(b.swd_name, '')::text,
        COALESCE(NULLIF(TRIM(CONCAT_WS(' ', NULLIF(TRIM(a.vehicle_make), ''), NULLIF(TRIM(a.version), ''))), ''), NULLIF(TRIM(c.vehicle_make), ''), '')::text,
        COALESCE(em.installment_amount, 0)::numeric,
        ROUND(COALESCE(part.emi_outstanding, 0), 2)::numeric,
        COALESCE(r.overdue_emi_count, 0)::integer,
        ROUND(COALESCE(r.overdue_amount, 0), 2)::numeric,
        public.fn_doc_inr_words(ROUND(COALESCE(r.overdue_amount, 0), 2))::text,
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
        )::text,
        c.contract_number::text,
        CASE WHEN c.contract_date IS NULL THEN NULL ELSE
            TO_CHAR(c.contract_date, 'DD') || ' ' ||
            (ARRAY['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'])[EXTRACT(MONTH FROM c.contract_date)::integer]
            || ' ' || TO_CHAR(c.contract_date, 'YYYY')
        END::text
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
    LEFT JOIN LATERAL (
        -- Reminder Monthly Part Amount Due: reuse the oldest unpaid overdue
        -- EMI's balance after ageing FIFO allocation. Do not deduct receipts again.
        -- A fully unpaid EMI retains its entire balance; no overdue EMI yields zero.
        SELECT e.emi_outstanding
        FROM pg_temp.tmp_emi_outstanding e
        WHERE e.contract_id = c.contract_id
          AND e.emi_outstanding > 0
          AND e.emi_due_date < COALESCE(p_as_on_date, CURRENT_DATE)
        ORDER BY e.emi_due_date, e.emi_no
        LIMIT 1
    ) part ON TRUE
    WHERE c.contract_id = p_contract_id
      AND c.is_active IS TRUE
      AND c.is_draft IS FALSE
      AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'
      AND c.loan_close_date IS NULL;
END;
$v083$;
BEGIN
    SELECT * INTO existing FROM pg_proc
    WHERE oid = to_regprocedure('public.fn_doc_reminder_letter(bigint,date)');
    IF existing.oid IS NULL THEN
        RAISE EXCEPTION 'Reminder compatibility validation failed: expected fn_doc_reminder_letter(bigint,date) is missing';
    END IF;
    -- Validate names, order and SQL types of every output, including all metadata.
    IF pg_get_function_result(existing.oid) IS DISTINCT FROM $shape$TABLE("BORROWER_NAME" text, "BORROWER_SWD" text, "VEHICLE" text, "EMI" numeric, "CURRENT_DUE" numeric, "NO_OF_OVERDUES" integer, "TOTAL_OS" numeric, "TOTAL_OS_WORDS" text, "GUARANTOR_1_NAME" text, "GUARANTOR_2_NAME" text, "BORROWER_ADDRESS" text, "CONTRACT_NUMBER" text, "CONTRACT_DATE" text)$shape$
       OR existing.prokind <> 'f'
       OR existing.prolang <> (SELECT oid FROM pg_language WHERE lanname = 'plpgsql')
       OR existing.proargnames[1:2] IS DISTINCT FROM ARRAY['p_contract_id','p_as_on_date']::text[]
       OR existing.pronargdefaults <> 1
       OR pg_get_expr(existing.proargdefaults, 0) IS DISTINCT FROM 'CURRENT_DATE' THEN
        RAISE EXCEPTION 'Reminder compatibility validation failed: unexpected input/output signature, language or default date; no function was replaced';
    END IF;
    IF btrim(replace(existing.prosrc, E'\r', ''), E' \n\t') = btrim(replace(approved_v083, E'\r', ''), E' \n\t') THEN
        -- Already corrected: safe SQL replay, without replacing even the function body.
        RETURN;
    END IF;
    IF btrim(replace(existing.prosrc, E'\r', ''), E' \n\t') <> btrim(replace(approved_v078, E'\r', ''), E' \n\t') THEN
        RAISE EXCEPTION 'Reminder compatibility validation failed: body is neither approved V078 nor approved V083; review pg_get_functiondef before proceeding';
    END IF;
    -- Preserve PostgreSQL's complete CREATE OR REPLACE header, execution properties,
    -- OID, dependents, owner, grants and comment. Change only the approved body.
    definition := pg_get_functiondef(existing.oid);
    IF length(definition) - length(replace(definition, existing.prosrc, '')) <> length(existing.prosrc) THEN
        RAISE EXCEPTION 'Reminder compatibility validation failed: cannot identify a unique function body';
    END IF;
    EXECUTE replace(definition, existing.prosrc, approved_v083);
END;
$migration$;
