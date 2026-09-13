DROP PROCEDURE IF EXISTS public.sp_branch_wise_ageing_test(date, character varying);

CREATE OR REPLACE PROCEDURE public.sp_branch_wise_ageing_test(
	IN p_as_on_date date,
	IN p_area_code character varying,
	IN p_user_group character varying)
LANGUAGE 'plpgsql'
AS $BODY$
DECLARE
    /* NULL As-On Date = Today */
    v_as_on_date DATE := COALESCE(p_as_on_date, CURRENT_DATE);

    /* NULL or blank Area Code = All Areas */
    v_area_code VARCHAR(50) := NULLIF(TRIM(p_area_code), '');

    /* Only explicit admin is unrestricted; invalid or blank groups match no contracts. */
    v_user_group VARCHAR(20) := LOWER(NULLIF(TRIM(p_user_group), ''));
BEGIN

    /* ============================================================
       0. ACTIVE CONTRACTS
       ------------------------------------------------------------
       Filter active contracts only once and reuse everywhere.
       ============================================================ */

    DROP TABLE IF EXISTS tmp_active_contracts;

    CREATE TEMP TABLE tmp_active_contracts
    ON COMMIT PRESERVE ROWS
    AS
    SELECT
        c.contract_id,
        c.contract_number,
        UPPER(TRIM(c.contract_number)) AS normalized_contract_number,

        c.contract_type,
        c.area_code,

        c.borrower_code,
        c.guarantor_code,

        c.contract_date,
        c.first_emi_date,

        c.vehicle_make,
        c.registration_number,
        c.manufacturing_year,
        c.category,

        c.total_contract_value,
        c.finance_charges

    FROM public.contracts c

    WHERE
        UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'

        AND c.loan_close_date IS NULL

        AND
        (
            v_area_code IS NULL
            OR UPPER(TRIM(c.area_code)) = UPPER(v_area_code)
        )

        AND
        (
            v_user_group = 'admin'

            OR
            (
                v_user_group = 'user'

                AND EXISTS
                (
                    SELECT 1
                    FROM public.users u
                    WHERE u.user_id::text = TRIM(COALESCE(c.updated_by, ''))
                      AND LOWER(TRIM(COALESCE(u.user_group, ''))) = 'user'
                )
            )
        );

    CREATE UNIQUE INDEX idx_tmp_active_contracts_id
        ON tmp_active_contracts(contract_id);

    CREATE INDEX idx_tmp_active_contracts_contract
        ON tmp_active_contracts(normalized_contract_number);

    ANALYZE tmp_active_contracts;

    /* ============================================================
       1. FINAL BRANCH WISE AGEING TEMP TABLE
       ============================================================ */

    DROP TABLE IF EXISTS tmp_branch_wise_ageing;

    CREATE TEMP TABLE tmp_branch_wise_ageing
    (
        area_code              VARCHAR(50),
        no_of_accounts         BIGINT,
        aum                    NUMERIC(18,2),
        outstanding_interest   NUMERIC(18,2),
        current_amount         NUMERIC(18,2),
        days_1_30              NUMERIC(18,2),
        days_31_60             NUMERIC(18,2),
        days_61_90             NUMERIC(18,2),
        days_91_120            NUMERIC(18,2),
        days_121_150           NUMERIC(18,2),
        days_151_180           NUMERIC(18,2),
        days_above_180         NUMERIC(18,2),
        total_outstanding      NUMERIC(18,2)
    ) ON COMMIT PRESERVE ROWS;

    /* ============================================================
       2. COLLECTIONS

       Rules:
       - AUTHORISED only
       - HJ excluded
       - ledger 3001 / 4201
       - sub ledger = contract number
       - voucher date <= As-On Date

       IMPORTANT:
       Only collections belonging to selected ACTIVE CONTRACTS
       are processed.
       ============================================================ */

    DROP TABLE IF EXISTS tmp_contract_collection;

    CREATE TEMP TABLE tmp_contract_collection
    (
        contract_number VARCHAR(100),
        total_received  NUMERIC(18,2)
    ) ON COMMIT PRESERVE ROWS;

    INSERT INTO tmp_contract_collection
    (
        contract_number,
        total_received
    )
    SELECT
        UPPER(TRIM(vd.sub_ledger_code)) AS contract_number,

        SUM
        (
            ABS
            (
                COALESCE(vd.debit_amount, 0)
                -
                COALESCE(vd.credit_amount, 0)
            )
        ) AS total_received

    FROM public.voucher_headers vh

    INNER JOIN public.voucher_details vd
        ON vd.voucher_header_id = vh.voucher_header_id

    WHERE
        vh.voucher_date <= v_as_on_date

        AND UPPER(TRIM(COALESCE(vh.status, ''))) = 'AUTHORISED'

        AND UPPER(TRIM(COALESCE(vh.voucher_type, ''))) <> 'HJ'

        AND TRIM(COALESCE(vd.ledger_code, ''))
            IN ('3001', '4201')

        AND NULLIF(TRIM(vd.sub_ledger_code), '') IS NOT NULL

        /*
        AND NULLIF(TRIM(vh.receipt_number), '') IS NOT NULL
        */

        AND EXISTS
        (
            SELECT 1
            FROM tmp_active_contracts ac
            WHERE ac.normalized_contract_number =
                  UPPER(TRIM(vd.sub_ledger_code))
        )

    GROUP BY
        UPPER(TRIM(vd.sub_ledger_code));

    CREATE UNIQUE INDEX idx_tmp_contract_collection_contract
        ON tmp_contract_collection(contract_number);

    ANALYZE tmp_contract_collection;

    /* ============================================================
       3. EMI SCHEDULE

       OPTIMIZATION:
       Previous installments calculated through window SUM rather
       than running a LATERAL SUM query for every repayment row.
       ============================================================ */

    DROP TABLE IF EXISTS tmp_emi_schedule;

    CREATE TEMP TABLE tmp_emi_schedule
    (
        contract_id        BIGINT,
        contract_number    VARCHAR(100),
        area_code          VARCHAR(50),
        emi_no             INTEGER,
        emi_due_date       DATE,
        installment_amount NUMERIC(18,2)
    ) ON COMMIT PRESERVE ROWS;

    INSERT INTO tmp_emi_schedule
    (
        contract_id,
        contract_number,
        area_code,
        emi_no,
        emi_due_date,
        installment_amount
    )

    WITH repayment_structure AS
    (
        SELECT
            rs.contract_id,
            rs.sequence_no,
            rs.number_of_installments,
            rs.installment_amount,

            COALESCE
            (
                SUM(rs.number_of_installments)
                OVER
                (
                    PARTITION BY rs.contract_id

                    ORDER BY rs.sequence_no

                    GROUPS BETWEEN
                        UNBOUNDED PRECEDING
                        AND 1 PRECEDING
                ),
                0
            )::INTEGER AS previous_installments

        FROM public.contract_repayment_structures rs

        INNER JOIN tmp_active_contracts ac
            ON ac.contract_id = rs.contract_id
    )

    SELECT
        c.contract_id,

        c.contract_number,

        c.area_code,

        (
            rs.previous_installments
            +
            gs.installment_no
        )::INTEGER AS emi_no,

        (
            c.first_emi_date
            +
            (
                (
                    rs.previous_installments
                    +
                    gs.installment_no
                    - 1
                )
                * INTERVAL '1 month'
            )
        )::DATE AS emi_due_date,

        rs.installment_amount

    FROM tmp_active_contracts c

    INNER JOIN repayment_structure rs
        ON rs.contract_id = c.contract_id

    CROSS JOIN LATERAL
    (
        SELECT generate_series
        (
            1,
            rs.number_of_installments
        ) AS installment_no
    ) gs

    WHERE
        c.first_emi_date IS NOT NULL;

    /* ============================================================
       TEMP INDEXES FOR NEXT CALCULATIONS
       ============================================================ */

    CREATE INDEX idx_tmp_emi_schedule_contract_emi
        ON tmp_emi_schedule(contract_id, emi_no);

    CREATE INDEX idx_tmp_emi_schedule_contract_date
        ON tmp_emi_schedule(contract_id, emi_due_date);

    ANALYZE tmp_emi_schedule;

    /* ============================================================
       4. EMI-WISE OUTSTANDING

       FIFO:
       Oldest EMI cleared first
       ============================================================ */

    DROP TABLE IF EXISTS tmp_emi_outstanding;

    CREATE TEMP TABLE tmp_emi_outstanding
    (
        contract_id        BIGINT,
        contract_number    VARCHAR(100),
        area_code          VARCHAR(50),
        emi_no             INTEGER,
        emi_due_date       DATE,
        installment_amount NUMERIC(18,2),
        emi_outstanding    NUMERIC(18,2)
    ) ON COMMIT PRESERVE ROWS;

    INSERT INTO tmp_emi_outstanding
    (
        contract_id,
        contract_number,
        area_code,
        emi_no,
        emi_due_date,
        installment_amount,
        emi_outstanding
    )

    WITH emi_running AS
    (
        SELECT
            e.*,

            COALESCE
            (
                SUM(e.installment_amount)
                OVER
                (
                    PARTITION BY e.contract_id

                    ORDER BY e.emi_no

                    ROWS BETWEEN
                        UNBOUNDED PRECEDING
                        AND 1 PRECEDING
                ),
                0
            ) AS previous_cumulative_emi

        FROM tmp_emi_schedule e
    )

    SELECT
        e.contract_id,

        e.contract_number,

        e.area_code,

        e.emi_no,

        e.emi_due_date,

        e.installment_amount,

        GREATEST
        (
            e.installment_amount
            -
            GREATEST
            (
                COALESCE(c.total_received, 0)
                -
                e.previous_cumulative_emi,

                0
            ),

            0
        ) AS emi_outstanding

    FROM emi_running e

    LEFT JOIN tmp_contract_collection c
        ON c.contract_number =
           UPPER(TRIM(e.contract_number));

    CREATE INDEX idx_tmp_emi_outstanding_contract_emi
        ON tmp_emi_outstanding(contract_id, emi_no);

    CREATE INDEX idx_tmp_emi_outstanding_contract_date
        ON tmp_emi_outstanding(contract_id, emi_due_date);

    ANALYZE tmp_emi_outstanding;

    /* ============================================================
       5. CONTRACT AGEING / OVERDUE INFORMATION

       AGEING RULE

       0 overdue EMI = Current
       1 overdue EMI = 1-30
       2 overdue EMI = 31-60
       3 overdue EMI = 61-90
       4 overdue EMI = 91-120
       5 overdue EMI = 121-150
       6 overdue EMI = 151-180
       7+ overdue EMI = Above 180

       OPTIMIZATION:
       - overdue count calculated once
       - current due date calculated once
       - removed correlated SELECT MIN(...) per EMI
       ============================================================ */

    DROP TABLE IF EXISTS tmp_contract_ageing;

    CREATE TEMP TABLE tmp_contract_ageing
    (
        contract_id           BIGINT,
        contract_number       VARCHAR(100),
        area_code             VARCHAR(50),

        overdue_emi_count     INTEGER,

        overdue_amount        NUMERIC(18,2),

        overdue_from_date     DATE,
        overdue_end_date      DATE,

        current_due           NUMERIC(18,2),
        current_due_date      DATE,

        total_outstanding     NUMERIC(18,2),

        ageing_bucket         VARCHAR(30)
    ) ON COMMIT PRESERVE ROWS;

    INSERT INTO tmp_contract_ageing
    (
        contract_id,
        contract_number,
        area_code,

        overdue_emi_count,

        overdue_amount,

        overdue_from_date,
        overdue_end_date,

        current_due,
        current_due_date,

        total_outstanding,

        ageing_bucket
    )

    WITH contract_summary AS
    (
        SELECT
            e.contract_id,

            MAX(e.contract_number) AS contract_number,

            MAX(e.area_code) AS area_code,

            /* OVERDUE EMI COUNT */

            COUNT(*)
            FILTER
            (
                WHERE
                    e.emi_outstanding > 0
                    AND e.emi_due_date < v_as_on_date
            )::INTEGER AS overdue_emi_count,

            /* OVERDUE AMOUNT */

            COALESCE
            (
                SUM(e.emi_outstanding)
                FILTER
                (
                    WHERE
                        e.emi_outstanding > 0
                        AND e.emi_due_date < v_as_on_date
                ),
                0
            ) AS overdue_amount,

            /* FIRST OVERDUE DATE */

            MIN(e.emi_due_date)
            FILTER
            (
                WHERE
                    e.emi_outstanding > 0
                    AND e.emi_due_date < v_as_on_date
            ) AS overdue_from_date,

            /* LAST OVERDUE DATE */

            MAX(e.emi_due_date)
            FILTER
            (
                WHERE
                    e.emi_outstanding > 0
                    AND e.emi_due_date < v_as_on_date
            ) AS overdue_end_date,

            /* NEXT EMI DATE */

            MIN(e.emi_due_date)
            FILTER
            (
                WHERE
                    e.emi_due_date >= v_as_on_date
            ) AS current_due_date,

            /* TOTAL OUTSTANDING */

            COALESCE
            (
                SUM(e.emi_outstanding),
                0
            ) AS total_outstanding

        FROM tmp_emi_outstanding e

        GROUP BY
            e.contract_id
    ),

    current_due_summary AS
    (
        SELECT
            cs.contract_id,

            COALESCE
            (
                SUM(e.emi_outstanding),
                0
            ) AS current_due

        FROM contract_summary cs

        LEFT JOIN tmp_emi_outstanding e
            ON e.contract_id = cs.contract_id
           AND e.emi_due_date = cs.current_due_date
           AND e.emi_outstanding > 0

        GROUP BY
            cs.contract_id
    )

    SELECT
        cs.contract_id,

        cs.contract_number,

        cs.area_code,

        cs.overdue_emi_count,

        cs.overdue_amount,

        cs.overdue_from_date,

        cs.overdue_end_date,

        COALESCE(cd.current_due, 0) AS current_due,

        cs.current_due_date,

        cs.total_outstanding,

        CASE
            WHEN cs.overdue_emi_count = 0
                THEN 'CURRENT'

            WHEN cs.overdue_emi_count = 1
                THEN '1-30'

            WHEN cs.overdue_emi_count = 2
                THEN '31-60'

            WHEN cs.overdue_emi_count = 3
                THEN '61-90'

            WHEN cs.overdue_emi_count = 4
                THEN '91-120'

            WHEN cs.overdue_emi_count = 5
                THEN '121-150'

            WHEN cs.overdue_emi_count = 6
                THEN '151-180'

            ELSE
                'ABOVE 180'

        END AS ageing_bucket

    FROM contract_summary cs

    LEFT JOIN current_due_summary cd
        ON cd.contract_id = cs.contract_id;

    CREATE UNIQUE INDEX idx_tmp_contract_ageing_contract
        ON tmp_contract_ageing(contract_id);

    ANALYZE tmp_contract_ageing;

    /* ============================================================
       6. PRINCIPAL / INTEREST / AUM

       IMPORTANT:
       Keep internal calculation in decimals.
       Do NOT round here.

       This prevents accumulated rounding difference.
       ============================================================ */

    DROP TABLE IF EXISTS tmp_contract_aum;

    CREATE TEMP TABLE tmp_contract_aum
    (
        contract_id             BIGINT,
        contract_number         VARCHAR(100),
        area_code               VARCHAR(50),

        total_contract_value    NUMERIC(18,2),
        finance_charges         NUMERIC(18,2),

        original_principal      NUMERIC(18,2),

        total_received          NUMERIC(18,2),

        principal_recovered     NUMERIC(18,2),
        interest_recovered      NUMERIC(18,2),

        principal_outstanding   NUMERIC(18,2),
        interest_outstanding    NUMERIC(18,2)
    ) ON COMMIT PRESERVE ROWS;

    INSERT INTO tmp_contract_aum
    (
        contract_id,
        contract_number,
        area_code,

        total_contract_value,
        finance_charges,

        original_principal,

        total_received,

        principal_recovered,
        interest_recovered,

        principal_outstanding,
        interest_outstanding
    )

    SELECT
        c.contract_id,

        c.contract_number,

        c.area_code,

        COALESCE(c.total_contract_value, 0),

        COALESCE(c.finance_charges, 0),

        /* ========================================================
           ORIGINAL PRINCIPAL
           ======================================================== */

        GREATEST
        (
            COALESCE(c.total_contract_value, 0)
            -
            COALESCE(c.finance_charges, 0),

            0
        ) AS original_principal,

        /* ========================================================
           TOTAL RECEIVED
           ======================================================== */

        COALESCE(cc.total_received, 0)
            AS total_received,

        /* ========================================================
           PRINCIPAL RECOVERED
           ======================================================== */

        LEAST
        (
            GREATEST
            (
                COALESCE(c.total_contract_value, 0)
                -
                COALESCE(c.finance_charges, 0),

                0
            ),

            CASE

                WHEN COALESCE(c.total_contract_value, 0) > 0

                THEN
                    COALESCE(cc.total_received, 0)
                    *
                    (
                        GREATEST
                        (
                            COALESCE(c.total_contract_value, 0)
                            -
                            COALESCE(c.finance_charges, 0),

                            0
                        )
                        /
                        c.total_contract_value
                    )

                ELSE 0

            END
        ) AS principal_recovered,

        /* ========================================================
           INTEREST RECOVERED
           ======================================================== */

        LEAST
        (
            COALESCE(c.finance_charges, 0),

            CASE

                WHEN COALESCE(c.total_contract_value, 0) > 0

                THEN
                    COALESCE(cc.total_received, 0)
                    *
                    (
                        COALESCE(c.finance_charges, 0)
                        /
                        c.total_contract_value
                    )

                ELSE 0

            END
        ) AS interest_recovered,

        /* ========================================================
           PRINCIPAL OUTSTANDING / AUM
           ======================================================== */

        GREATEST
        (
            GREATEST
            (
                COALESCE(c.total_contract_value, 0)
                -
                COALESCE(c.finance_charges, 0),

                0
            )

            -

            LEAST
            (
                GREATEST
                (
                    COALESCE(c.total_contract_value, 0)
                    -
                    COALESCE(c.finance_charges, 0),

                    0
                ),

                CASE

                    WHEN COALESCE(c.total_contract_value, 0) > 0

                    THEN
                        COALESCE(cc.total_received, 0)
                        *
                        (
                            GREATEST
                            (
                                COALESCE(c.total_contract_value, 0)
                                -
                                COALESCE(c.finance_charges, 0),

                                0
                            )
                            /
                            c.total_contract_value
                        )

                    ELSE 0

                END
            ),

            0
        ) AS principal_outstanding,

        /* ========================================================
           INTEREST OUTSTANDING
           ======================================================== */

        GREATEST
        (
            COALESCE(c.finance_charges, 0)

            -

            LEAST
            (
                COALESCE(c.finance_charges, 0),

                CASE

                    WHEN COALESCE(c.total_contract_value, 0) > 0

                    THEN
                        COALESCE(cc.total_received, 0)
                        *
                        (
                            COALESCE(c.finance_charges, 0)
                            /
                            c.total_contract_value
                        )

                    ELSE 0

                END
            ),

            0
        ) AS interest_outstanding

    FROM tmp_active_contracts c

    LEFT JOIN tmp_contract_collection cc
        ON cc.contract_number =
           c.normalized_contract_number;

    CREATE UNIQUE INDEX idx_tmp_contract_aum_contract
        ON tmp_contract_aum(contract_id);

    ANALYZE tmp_contract_aum;

    /* ============================================================
       7. COMMON CONTRACT REPORT

       Used for:
       - Demand List
       - Consolidated
       - Ageing Drill Down

       OPTIMIZATION:
       owner_serial_no pulled directly from assets.
       No separate UPDATE required.
       ============================================================ */

    DROP TABLE IF EXISTS tmp_contract_report;

    CREATE TEMP TABLE tmp_contract_report
    (
        contract_id             BIGINT,

        contract_number         VARCHAR(100),
        contract_type           VARCHAR(50),

        area_code               VARCHAR(50),

        borrower_code           VARCHAR(50),
        guarantor_code          VARCHAR(50),

        contract_date           DATE,
        first_emi_date          DATE,

        vehicle_make            VARCHAR(100),
        owner_serial_no         VARCHAR(50),
        registration_number     VARCHAR(50),
        manufacturing_year      INTEGER,
        category                VARCHAR(50),

        total_contract_value    NUMERIC(18,2),

        original_principal      NUMERIC(18,2),

        total_received          NUMERIC(18,2),

        principal_recovered     NUMERIC(18,2),
        interest_recovered      NUMERIC(18,2),

        principal_outstanding   NUMERIC(18,2),
        interest_outstanding    NUMERIC(18,2),

        total_outstanding       NUMERIC(18,2),

        overdue_emi_count       INTEGER,
        overdue_amount          NUMERIC(18,2),

        overdue_from_date       DATE,
        overdue_end_date        DATE,

        current_due             NUMERIC(18,2),
        current_due_date        DATE,

        ageing_bucket           VARCHAR(30)
    ) ON COMMIT PRESERVE ROWS;

    INSERT INTO tmp_contract_report
    (
        contract_id,

        contract_number,
        contract_type,

        area_code,

        borrower_code,
        guarantor_code,

        contract_date,
        first_emi_date,

        vehicle_make,
        owner_serial_no,
        registration_number,
        manufacturing_year,
        category,

        total_contract_value,

        original_principal,

        total_received,

        principal_recovered,
        interest_recovered,

        principal_outstanding,
        interest_outstanding,

        total_outstanding,

        overdue_emi_count,
        overdue_amount,

        overdue_from_date,
        overdue_end_date,

        current_due,
        current_due_date,

        ageing_bucket
    )

    SELECT
        c.contract_id,

        TRIM(c.contract_number),

        c.contract_type,

        c.area_code,

        c.borrower_code,

        c.guarantor_code,

        c.contract_date,

        c.first_emi_date,

        c.vehicle_make,

        ast.owner_serial_no,

        c.registration_number,

        c.manufacturing_year,

        c.category,

        a.total_contract_value,

        a.original_principal,

        a.total_received,

        a.principal_recovered,

        a.interest_recovered,

        a.principal_outstanding,

        a.interest_outstanding,

        ca.total_outstanding,

        ca.overdue_emi_count,

        ca.overdue_amount,

        ca.overdue_from_date,

        ca.overdue_end_date,

        ca.current_due,

        ca.current_due_date,

        ca.ageing_bucket

    FROM tmp_contract_ageing ca

    INNER JOIN tmp_active_contracts c
        ON c.contract_id = ca.contract_id

    INNER JOIN tmp_contract_aum a
        ON a.contract_id = ca.contract_id

    LEFT JOIN LATERAL
    (
        SELECT
            aa.owner_serial_no

        FROM public.assets aa

        WHERE aa.contract_id = c.contract_id

        LIMIT 1
    ) ast ON TRUE;

    CREATE UNIQUE INDEX idx_tmp_contract_report_contract
        ON tmp_contract_report(contract_id);

    CREATE INDEX idx_tmp_contract_report_area
        ON tmp_contract_report(area_code);

    ANALYZE tmp_contract_report;

    /* ============================================================
       8. FINAL BRANCH WISE AGEING

       FINAL REPORT AMOUNTS ROUNDED TO WHOLE RUPEES
       ============================================================ */

    INSERT INTO tmp_branch_wise_ageing
    (
        area_code,
        no_of_accounts,

        aum,
        outstanding_interest,

        current_amount,

        days_1_30,
        days_31_60,
        days_61_90,
        days_91_120,
        days_121_150,
        days_151_180,
        days_above_180,

        total_outstanding
    )

    SELECT
        COALESCE
        (
            NULLIF(TRIM(r.area_code), ''),
            'UNASSIGNED'
        ) AS area_code,

        COUNT(DISTINCT r.contract_id)
            AS no_of_accounts,

        /* AUM */

        ROUND
        (
            SUM(r.principal_outstanding),
            0
        ) AS aum,

        /* OUTSTANDING INTEREST */

        ROUND
        (
            SUM(r.interest_outstanding),
            0
        ) AS outstanding_interest,

        /* CURRENT */

        ROUND
        (
            SUM
            (
                CASE
                    WHEN r.overdue_emi_count = 0
                    THEN r.total_outstanding
                    ELSE 0
                END
            ),
            0
        ) AS current_amount,

        /* 1 - 30 */

        ROUND
        (
            SUM
            (
                CASE
                    WHEN r.overdue_emi_count = 1
                    THEN r.total_outstanding
                    ELSE 0
                END
            ),
            0
        ) AS days_1_30,

        /* 31 - 60 */

        ROUND
        (
            SUM
            (
                CASE
                    WHEN r.overdue_emi_count = 2
                    THEN r.total_outstanding
                    ELSE 0
                END
            ),
            0
        ) AS days_31_60,

        /* 61 - 90 */

        ROUND
        (
            SUM
            (
                CASE
                    WHEN r.overdue_emi_count = 3
                    THEN r.total_outstanding
                    ELSE 0
                END
            ),
            0
        ) AS days_61_90,

        /* 91 - 120 */

        ROUND
        (
            SUM
            (
                CASE
                    WHEN r.overdue_emi_count = 4
                    THEN r.total_outstanding
                    ELSE 0
                END
            ),
            0
        ) AS days_91_120,

        /* 121 - 150 */

        ROUND
        (
            SUM
            (
                CASE
                    WHEN r.overdue_emi_count = 5
                    THEN r.total_outstanding
                    ELSE 0
                END
            ),
            0
        ) AS days_121_150,

        /* 151 - 180 */

        ROUND
        (
            SUM
            (
                CASE
                    WHEN r.overdue_emi_count = 6
                    THEN r.total_outstanding
                    ELSE 0
                END
            ),
            0
        ) AS days_151_180,

        /* ABOVE 180 */

        ROUND
        (
            SUM
            (
                CASE
                    WHEN r.overdue_emi_count >= 7
                    THEN r.total_outstanding
                    ELSE 0
                END
            ),
            0
        ) AS days_above_180,

        /* TOTAL OUTSTANDING */

        ROUND
        (
            SUM(r.total_outstanding),
            0
        ) AS total_outstanding

    FROM tmp_contract_report r

    GROUP BY
        COALESCE
        (
            NULLIF(TRIM(r.area_code), ''),
            'UNASSIGNED'
        );

    ANALYZE tmp_branch_wise_ageing;

	/* ============================================================
	   9. CONSOLIDATED PORTFOLIO BASE
	
	   IMPORTANT:
	   - Existing Aging / Demand List logic is NOT changed.
	   - Base monetary values remain in RUPEES.
	   - Summary tables below convert amounts to CRORES.
	   - DPD uses p_as_on_date.
	   ============================================================ */
	
	DROP TABLE IF EXISTS tmp_consolidated_portfolio_base;
	
	CREATE TEMP TABLE tmp_consolidated_portfolio_base
	(
	    contract_id            BIGINT,
	    contract_number        VARCHAR(100),
	
	    tenor_months           INTEGER,
	
	    ticket_size            NUMERIC(18,2),
	
	    state_code             VARCHAR(50),
	    state_name             VARCHAR(100),
	
	    principal_outstanding  NUMERIC(18,2),
	
	    dpd_days               INTEGER,
	
	    par_bucket             VARCHAR(30)
	) ON COMMIT PRESERVE ROWS;
	
	
	INSERT INTO tmp_consolidated_portfolio_base
	(
	    contract_id,
	    contract_number,
	
	    tenor_months,
	
	    ticket_size,
	
	    state_code,
	    state_name,
	
	    principal_outstanding,
	
	    dpd_days,
	
	    par_bucket
	)
	
	SELECT
	    r.contract_id,
	
	    r.contract_number,
	
	    /* --------------------------------------------------------
	       TENOR IN MONTHS
	       -------------------------------------------------------- */
	
	    COALESCE(
	        ten.tenor_months,
	        0
	    ) AS tenor_months,
	
	    /* --------------------------------------------------------
	       LOAN TICKET SIZE
	
	       Original Principal.
	       Keep in rupees because this is used for band selection.
	       -------------------------------------------------------- */
	
	    COALESCE(
	        r.original_principal,
	        0
	    ) AS ticket_size,
	
	    /* --------------------------------------------------------
	       STATE CODE
	       -------------------------------------------------------- */
	
	    pm.state_code,
	
	    /* --------------------------------------------------------
	       STATE NAME
	       -------------------------------------------------------- */
	
	    COALESCE(
	        go.state_name,
	        pm.state_code,
	        'UNASSIGNED'
	    ) AS state_name,
	
	    /* --------------------------------------------------------
	       PRINCIPAL OUTSTANDING
	
	       Base value remains in rupees.
	       -------------------------------------------------------- */
	
	    COALESCE(
	        r.principal_outstanding,
	        0
	    ) AS principal_outstanding,
	
	    /* --------------------------------------------------------
	       DPD BASED ON REPORT AS-ON DATE
	       -------------------------------------------------------- */
	
	    CASE
	        WHEN r.overdue_from_date IS NULL
	            THEN 0
	
	        ELSE
	            GREATEST(
	                p_as_on_date - r.overdue_from_date,
	                0
	            )
	    END AS dpd_days,
	
	    /* --------------------------------------------------------
	       CONSOLIDATED PAR BUCKET
	
	       This is separate from existing Aging Analysis buckets.
	       -------------------------------------------------------- */
	
	    CASE
	        WHEN r.overdue_from_date IS NULL
	            THEN 'STANDARD'
	
	        WHEN (p_as_on_date - r.overdue_from_date) <= 30
	            THEN '0-30'
	
	        WHEN (p_as_on_date - r.overdue_from_date) <= 60
	            THEN '31-60'
	
	        WHEN (p_as_on_date - r.overdue_from_date) <= 90
	            THEN '61-90'
	
	        WHEN (p_as_on_date - r.overdue_from_date) <= 180
	            THEN '91-180'
	
	        WHEN (p_as_on_date - r.overdue_from_date) <= 365
	            THEN '181-365'
	
	        ELSE '>365'
	
	    END AS par_bucket
	
	FROM tmp_contract_report r
	
	
	/* ============================================================
	   TENOR FROM REPAYMENT STRUCTURE
	   ============================================================ */
	
	LEFT JOIN
	(
	    SELECT
	        contract_id,
	
	        SUM(
	            COALESCE(number_of_installments, 0)
	        )::INTEGER AS tenor_months
	
	    FROM public.contract_repayment_structures
	
	    GROUP BY contract_id
	
	) ten
	    ON ten.contract_id = r.contract_id
	
	
	/* ============================================================
	   BORROWER STATE FROM PARTY MASTER
	   ============================================================ */
	
	LEFT JOIN
	(
	    SELECT
	        UPPER(TRIM(party_code)) AS party_code,
	
	        MAX(
	            NULLIF(
	                TRIM(state),
	                ''
	            )
	        ) AS state_code
	
	    FROM public.party_masters
	
	    WHERE NULLIF(
	        TRIM(party_code),
	        ''
	    ) IS NOT NULL
	
	    GROUP BY
	        UPPER(TRIM(party_code))
	
	) pm
	    ON pm.party_code =
	       UPPER(TRIM(r.borrower_code))
	
	
	/* ============================================================
	   STATE NAME FROM G_OTHERS
	   ============================================================ */
	
	LEFT JOIN
	(
	    SELECT
	        UPPER(TRIM(other_code)) AS other_code,
	
	        MAX(
	            NULLIF(
	                TRIM(other_desc),
	                ''
	            )
	        ) AS state_name
	
	    FROM public.g_others
	
	    WHERE NULLIF(
	        TRIM(other_code),
	        ''
	    ) IS NOT NULL
	
	    GROUP BY
	        UPPER(TRIM(other_code))
	
	) go
	    ON go.other_code =
	       UPPER(TRIM(pm.state_code));
	
	
	/* ============================================================
	   10. TENOR-WISE CONSOLIDATED PORTFOLIO
	
	   ALL AMOUNTS IN CRORES
	   ============================================================ */
	
	DROP TABLE IF EXISTS tmp_portfolio_tenor;
	
	CREATE TEMP TABLE tmp_portfolio_tenor
	(
	    sort_order                  INTEGER,
	
	    tenor_band                  VARCHAR(50),
	
	    no_of_accounts              BIGINT,
	
	    principal_outstanding_cr    NUMERIC(18,2),
	
	    standard_cr                 NUMERIC(18,2),
	
	    days_0_30_cr                NUMERIC(18,2),
	    days_31_60_cr               NUMERIC(18,2),
	    days_61_90_cr               NUMERIC(18,2),
	
	    days_91_180_cr              NUMERIC(18,2),
	
	    days_181_365_cr             NUMERIC(18,2),
	
	    days_above_365_cr           NUMERIC(18,2)
	
	) ON COMMIT PRESERVE ROWS;
	
	
	INSERT INTO tmp_portfolio_tenor
	(
	    sort_order,
	    tenor_band,
	
	    no_of_accounts,
	
	    principal_outstanding_cr,
	
	    standard_cr,
	
	    days_0_30_cr,
	    days_31_60_cr,
	    days_61_90_cr,
	
	    days_91_180_cr,
	
	    days_181_365_cr,
	
	    days_above_365_cr
	)
	
	SELECT
	
	    CASE
	        WHEN tenor_months <= 12 THEN 1
	        WHEN tenor_months <= 24 THEN 2
	        WHEN tenor_months <= 36 THEN 3
	        WHEN tenor_months <= 48 THEN 4
	        ELSE 5
	    END AS sort_order,
	
	    CASE
	        WHEN tenor_months <= 12
	            THEN 'Up to 12 Months'
	
	        WHEN tenor_months <= 24
	            THEN '13 - 24 Months'
	
	        WHEN tenor_months <= 36
	            THEN '25 - 36 Months'
	
	        WHEN tenor_months <= 48
	            THEN '37 - 48 Months'
	
	        ELSE
	            'Above 48 Months'
	    END AS tenor_band,
	
	    COUNT(DISTINCT contract_id)
	        AS no_of_accounts,
	
	    /* Principal O/S in Crores */
	
	    ROUND(
	        SUM(principal_outstanding) / 10000000.0,
	        2
	    ) AS principal_outstanding_cr,
	
	    /* Standard */
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = 'STANDARD'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS standard_cr,
	
	    /* 0-30 */
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '0-30'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_0_30_cr,
	
	    /* 31-60 */
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '31-60'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_31_60_cr,
	
	    /* 61-90 */
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '61-90'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_61_90_cr,
	
	    /* 91-180 */
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '91-180'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_91_180_cr,
	
	    /* 181-365 */
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '181-365'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_181_365_cr,
	
	    /* Above 365 */
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '>365'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_above_365_cr
	
	FROM tmp_consolidated_portfolio_base
	
	GROUP BY
	
	    CASE
	        WHEN tenor_months <= 12 THEN 1
	        WHEN tenor_months <= 24 THEN 2
	        WHEN tenor_months <= 36 THEN 3
	        WHEN tenor_months <= 48 THEN 4
	        ELSE 5
	    END,
	
	    CASE
	        WHEN tenor_months <= 12
	            THEN 'Up to 12 Months'
	
	        WHEN tenor_months <= 24
	            THEN '13 - 24 Months'
	
	        WHEN tenor_months <= 36
	            THEN '25 - 36 Months'
	
	        WHEN tenor_months <= 48
	            THEN '37 - 48 Months'
	
	        ELSE
	            'Above 48 Months'
	    END;
	
	
	/* ============================================================
	   11. LOAN TICKET SIZE-WISE CONSOLIDATED PORTFOLIO
	
	   Ticket-size classification uses RUPEES.
	   Report amounts are CRORES.
	   ============================================================ */
	
	DROP TABLE IF EXISTS tmp_portfolio_ticket_size;
	
	CREATE TEMP TABLE tmp_portfolio_ticket_size
	(
	    sort_order                  INTEGER,
	
	    ticket_size_band            VARCHAR(50),
	
	    no_of_accounts              BIGINT,
	
	    principal_outstanding_cr    NUMERIC(18,2),
	
	    standard_cr                 NUMERIC(18,2),
	
	    days_0_30_cr                NUMERIC(18,2),
	    days_31_60_cr               NUMERIC(18,2),
	    days_61_90_cr               NUMERIC(18,2),
	
	    days_91_180_cr              NUMERIC(18,2),
	
	    days_181_365_cr             NUMERIC(18,2),
	
	    days_above_365_cr           NUMERIC(18,2)
	
	) ON COMMIT PRESERVE ROWS;
	
	
	INSERT INTO tmp_portfolio_ticket_size
	(
	    sort_order,
	    ticket_size_band,
	
	    no_of_accounts,
	
	    principal_outstanding_cr,
	
	    standard_cr,
	
	    days_0_30_cr,
	    days_31_60_cr,
	    days_61_90_cr,
	
	    days_91_180_cr,
	
	    days_181_365_cr,
	
	    days_above_365_cr
	)
	
	SELECT
	
	    CASE
	        WHEN ticket_size < 100000 THEN 1
	        WHEN ticket_size <= 500000 THEN 2
	        WHEN ticket_size <= 750000 THEN 3
	        WHEN ticket_size <= 1000000 THEN 4
	        WHEN ticket_size <= 1500000 THEN 5
	        WHEN ticket_size <= 2000000 THEN 6
	        WHEN ticket_size <= 2500000 THEN 7
	        ELSE 8
	    END AS sort_order,
	
	    CASE
	        WHEN ticket_size < 100000
	            THEN 'Below 1.00 Lakh'
	
	        WHEN ticket_size <= 500000
	            THEN '1.00 - 5.00 Lakhs'
	
	        WHEN ticket_size <= 750000
	            THEN '5.00 - 7.50 Lakhs'
	
	        WHEN ticket_size <= 1000000
	            THEN '7.50 - 10.00 Lakhs'
	
	        WHEN ticket_size <= 1500000
	            THEN '10.00 - 15.00 Lakhs'
	
	        WHEN ticket_size <= 2000000
	            THEN '15.00 - 20.00 Lakhs'
	
	        WHEN ticket_size <= 2500000
	            THEN '20.00 - 25.00 Lakhs'
	
	        ELSE
	            'Above 25.00 Lakhs'
	    END AS ticket_size_band,
	
	    COUNT(DISTINCT contract_id)
	        AS no_of_accounts,
	
	    ROUND(
	        SUM(principal_outstanding) / 10000000.0,
	        2
	    ) AS principal_outstanding_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = 'STANDARD'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS standard_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '0-30'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_0_30_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '31-60'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_31_60_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '61-90'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_61_90_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '91-180'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_91_180_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '181-365'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_181_365_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '>365'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_above_365_cr
	
	FROM tmp_consolidated_portfolio_base
	
	GROUP BY
	
	    CASE
	        WHEN ticket_size < 100000 THEN 1
	        WHEN ticket_size <= 500000 THEN 2
	        WHEN ticket_size <= 750000 THEN 3
	        WHEN ticket_size <= 1000000 THEN 4
	        WHEN ticket_size <= 1500000 THEN 5
	        WHEN ticket_size <= 2000000 THEN 6
	        WHEN ticket_size <= 2500000 THEN 7
	        ELSE 8
	    END,
	
	    CASE
	        WHEN ticket_size < 100000
	            THEN 'Below 1.00 Lakh'
	
	        WHEN ticket_size <= 500000
	            THEN '1.00 - 5.00 Lakhs'
	
	        WHEN ticket_size <= 750000
	            THEN '5.00 - 7.50 Lakhs'
	
	        WHEN ticket_size <= 1000000
	            THEN '7.50 - 10.00 Lakhs'
	
	        WHEN ticket_size <= 1500000
	            THEN '10.00 - 15.00 Lakhs'
	
	        WHEN ticket_size <= 2000000
	            THEN '15.00 - 20.00 Lakhs'
	
	        WHEN ticket_size <= 2500000
	            THEN '20.00 - 25.00 Lakhs'
	
	        ELSE
	            'Above 25.00 Lakhs'
	    END;
	
	
	/* ============================================================
	   12. STATE-WISE CONSOLIDATED PORTFOLIO
	
	   ALL AMOUNTS IN CRORES
	   ============================================================ */
	
	DROP TABLE IF EXISTS tmp_portfolio_state;
	
	CREATE TEMP TABLE tmp_portfolio_state
	(
	    state_code                  VARCHAR(50),
	
	    state_name                  VARCHAR(100),
	
	    no_of_accounts              BIGINT,
	
	    principal_outstanding_cr    NUMERIC(18,2),
	
	    standard_cr                 NUMERIC(18,2),
	
	    days_0_30_cr                NUMERIC(18,2),
	    days_31_60_cr               NUMERIC(18,2),
	    days_61_90_cr               NUMERIC(18,2),
	
	    days_91_180_cr              NUMERIC(18,2),
	
	    days_181_365_cr             NUMERIC(18,2),
	
	    days_above_365_cr           NUMERIC(18,2)
	
	) ON COMMIT PRESERVE ROWS;
	
	
	INSERT INTO tmp_portfolio_state
	(
	    state_code,
	    state_name,
	
	    no_of_accounts,
	
	    principal_outstanding_cr,
	
	    standard_cr,
	
	    days_0_30_cr,
	    days_31_60_cr,
	    days_61_90_cr,
	
	    days_91_180_cr,
	
	    days_181_365_cr,
	
	    days_above_365_cr
	)
	
	SELECT
	
	    COALESCE(
	        NULLIF(TRIM(state_code), ''),
	        'UNASSIGNED'
	    ) AS state_code,
	
	    COALESCE(
	        NULLIF(TRIM(state_name), ''),
	        'UNASSIGNED'
	    ) AS state_name,
	
	    COUNT(DISTINCT contract_id)
	        AS no_of_accounts,
	
	    ROUND(
	        SUM(principal_outstanding) / 10000000.0,
	        2
	    ) AS principal_outstanding_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = 'STANDARD'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS standard_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '0-30'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_0_30_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '31-60'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_31_60_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '61-90'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_61_90_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '91-180'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_91_180_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '181-365'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_181_365_cr,
	
	    ROUND(
	        SUM(
	            CASE
	                WHEN par_bucket = '>365'
	                    THEN principal_outstanding
	                ELSE 0
	            END
	        ) / 10000000.0,
	        2
	    ) AS days_above_365_cr
	
	FROM tmp_consolidated_portfolio_base
	
	GROUP BY
	
	    COALESCE(
	        NULLIF(TRIM(state_code), ''),
	        'UNASSIGNED'
	    ),
	
	    COALESCE(
	        NULLIF(TRIM(state_name), ''),
	        'UNASSIGNED'
	    );
END;
$BODY$;
