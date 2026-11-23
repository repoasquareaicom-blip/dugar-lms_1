-- Append contract identifiers only. Financial SQL below is preserved from the inspected function.
-- Flyway runs this DO block transactionally. Never use CASCADE: unknown dependents
-- must be reviewed rather than silently dropped. Existing owner/ACL/comment survive recreation.
DO $migration$
DECLARE
    previous pg_proc%ROWTYPE;
    previous_comment text;
    permission record;
    target_role text;
BEGIN
    SELECT * INTO previous FROM pg_proc
    WHERE oid=to_regprocedure('public.fn_doc_reminder_letter(bigint,date)');
    IF previous.oid IS NOT NULL THEN
        -- Do not overwrite financial customizations in another environment.
        IF md5(replace(previous.prosrc, E'\r', '')) <> '10a9f3ec87c9a6b7efb2c390aad2b632' THEN
            RAISE EXCEPTION 'Reminder function body differs from the inspected baseline; review financial SQL before migration';
        END IF;
        IF previous.prolang <> (SELECT oid FROM pg_language WHERE lanname='plpgsql')
           OR previous.prosecdef OR previous.proleakproof OR previous.prosupport <> 0 OR previous.proconfig IS NOT NULL
           OR previous.provolatile <> 'v' OR previous.proparallel <> 'u' OR previous.proisstrict
           OR previous.procost <> 100 OR previous.prorows <> 1000
           OR previous.pronargdefaults <> 1 OR pg_get_expr(previous.proargdefaults,0) IS DISTINCT FROM 'CURRENT_DATE' THEN
            RAISE EXCEPTION 'Reminder function has unexpected execution settings; review before migration';
        END IF;
        previous_comment := obj_description(previous.oid,'pg_proc');
        DROP FUNCTION public.fn_doc_reminder_letter(bigint,date) RESTRICT;
    END IF;
    EXECUTE $definition$
CREATE OR REPLACE FUNCTION public.fn_doc_reminder_letter(p_contract_id bigint, p_as_on_date date DEFAULT CURRENT_DATE)
 RETURNS TABLE("BORROWER_NAME" text, "BORROWER_SWD" text, "VEHICLE" text, "EMI" numeric, "CURRENT_DUE" numeric, "NO_OF_OVERDUES" integer, "TOTAL_OS" numeric, "TOTAL_OS_WORDS" text, "GUARANTOR_1_NAME" text, "GUARANTOR_2_NAME" text, "BORROWER_ADDRESS" text, "CONTRACT_NUMBER" text, "CONTRACT_DATE" text)
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
$function$

$definition$;
    IF previous.oid IS NOT NULL THEN
        -- Remove creation-time default grants, then restore the original effective ACL.
        FOR permission IN SELECT * FROM aclexplode(COALESCE(
            (SELECT proacl FROM pg_proc WHERE oid='public.fn_doc_reminder_letter(bigint,date)'::regprocedure),
            acldefault('f',(SELECT proowner FROM pg_proc WHERE oid='public.fn_doc_reminder_letter(bigint,date)'::regprocedure)))) LOOP
            target_role := CASE WHEN permission.grantee=0 THEN 'PUBLIC' ELSE quote_ident(pg_get_userbyid(permission.grantee)) END;
            EXECUTE 'REVOKE ALL ON FUNCTION public.fn_doc_reminder_letter(bigint,date) FROM ' || target_role;
        END LOOP;
        FOR permission IN SELECT * FROM aclexplode(COALESCE(previous.proacl,acldefault('f',previous.proowner))) LOOP
            target_role := CASE WHEN permission.grantee=0 THEN 'PUBLIC' ELSE quote_ident(pg_get_userbyid(permission.grantee)) END;
            EXECUTE 'GRANT EXECUTE ON FUNCTION public.fn_doc_reminder_letter(bigint,date) TO ' || target_role
                || CASE WHEN permission.is_grantable THEN ' WITH GRANT OPTION' ELSE '' END;
        END LOOP;
        EXECUTE format('ALTER FUNCTION public.fn_doc_reminder_letter(bigint,date) OWNER TO %I',pg_get_userbyid(previous.proowner));
        EXECUTE format('COMMENT ON FUNCTION public.fn_doc_reminder_letter(bigint,date) IS %L',previous_comment);
    END IF;
END;
$migration$;
