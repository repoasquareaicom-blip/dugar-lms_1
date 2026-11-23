"""Run with: python src/test/python/test_contract_repayment_schedule.py

Requires psycopg and PostgreSQL binaries on PATH. Always creates its own local
cluster in a fresh temporary directory; never reads application DB credentials.
Existing migrations supply the table definitions. The upfront flag, absent from
those migrations but referenced by the app, is added to the disposable fixture
only, after verifying that an unavailable flag is rejected.
"""
from decimal import Decimal as D, ROUND_HALF_UP
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import unittest

import psycopg
from psycopg.rows import dict_row


API = Path(__file__).resolve().parents[3]
MIGRATIONS = API / "src/main/resources/db/migration"
MIGRATION = MIGRATIONS / "V079__create_contract_repayment_schedule_function.sql"
CORRECTION = MIGRATIONS / "V080__ignore_moratorium_in_contract_repayment_schedule.sql"
STRICT_CORRECTION = MIGRATIONS / "V081__strict_excel_contract_repayment_schedule.sql"


class RepaymentScheduleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(tempfile.mkdtemp(prefix="dugar-repayment-schedule-test-"))
        cls.started = False
        cls.addClassCleanup(cls.stop_cluster)
        for binary in ("initdb", "pg_ctl"):
            if not shutil.which(binary):
                raise RuntimeError(f"PostgreSQL binary missing: {binary}")
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            cls.port = sock.getsockname()[1]
        cls.command("initdb", "-D", str(cls.root / "data"), "-U", "postgres",
                    "-A", "trust", "--encoding=UTF8", "--no-locale")
        cls.command("pg_ctl", "-D", str(cls.root / "data"), "-l",
                    str(cls.root / "postgres.log"), "-o",
                    f"-h 127.0.0.1 -p {cls.port}", "-w", "start")
        cls.started = True
        with psycopg.connect(host="127.0.0.1", port=cls.port, user="postgres",
                             dbname="postgres", autocommit=True) as connection:
            connection.execute("CREATE DATABASE repayment_schedule_stage1_test")
        cls.connection = psycopg.connect(
            host="127.0.0.1", port=cls.port, user="postgres",
            dbname="repayment_schedule_stage1_test", autocommit=True,
            row_factory=dict_row, options="-c statement_timeout=10000")
        cls.addClassCleanup(cls.connection.close)
        for name in ("V001__create_contract_tables.sql",
                     "V009__create_contract_repayment_structures.sql",
                     "V011__add_remaining_financial_terms_columns.sql"):
            cls.connection.execute((MIGRATIONS / name).read_text())
        cls.connection.execute(MIGRATION.read_text())
        cls.connection.execute("""
            INSERT INTO public.contracts
                (contract_id,contract_type,contract_number,status,loan_amount,irr_rate,
                 first_emi_date,tenure_months,finance_charges,total_contract_value,
                 payment_frequency,repayment_terms,repayment_type,moratorium_months)
            VALUES (6535,'HP','17270','Y',300000,24.9970,'2026-11-05',24,84264,384264,
                    'Monthly','Monthly','EMI',1)
        """)
        cls.connection.execute("""
            INSERT INTO public.contract_repayment_structures
                (contract_id,sequence_no,number_of_installments,installment_amount)
            SELECT contract_id,1,24,16011 FROM public.contracts
        """)
        try:
            cls.connection.execute("SELECT * FROM public.fn_contract_repayment_schedule('17270')")
        except psycopg.errors.InvalidParameterValue as error:
            if "Upfront EMI status is unavailable" not in str(error):
                raise
        else:
            raise AssertionError("Missing upfront flag was not rejected")
        cls.connection.execute("ALTER TABLE public.contracts ADD COLUMN is_first_emi_paid BOOLEAN")
        cls.connection.execute("UPDATE public.contracts SET is_first_emi_paid=false")
        cls.before_correction = cls.connection.execute("SELECT to_jsonb(c) AS data FROM public.contracts c").fetchall()
        try:
            cls.connection.execute("SELECT * FROM public.fn_contract_repayment_schedule('17270')")
        except psycopg.errors.InvalidParameterValue as error:
            if "Moratorium contracts are unsupported" not in str(error):
                raise
        else:
            raise AssertionError("V079 baseline should reject moratorium=1")
        cls.connection.execute(CORRECTION.read_text())
        previous_final = cls.connection.execute("SELECT * FROM public.fn_contract_repayment_schedule('17270') WHERE installment_no=24").fetchone()
        if previous_final["closing_principal"] != 0 or previous_final["rounding_adjustment"] != D("0.14"):
            raise AssertionError("V080 baseline should reconcile final principal")
        cls.connection.execute(STRICT_CORRECTION.read_text())
        if cls.before_correction != cls.connection.execute("SELECT to_jsonb(c) AS data FROM public.contracts c").fetchall():
            raise AssertionError("Corrective migration changed contract records")
        cls.connection.execute("CREATE ROLE schedule_stage1_reader")
        cls.connection.execute("GRANT USAGE ON SCHEMA public TO schedule_stage1_reader")
        cls.connection.execute("""
            GRANT SELECT ON public.contracts, public.contract_repayment_structures
            TO schedule_stage1_reader
        """)
        print(f"Disposable PostgreSQL test cluster: {cls.root}; port {cls.port}")
        print("Migration-only schema: missing upfront flag rejection PASS")

    @classmethod
    def command(cls, *args):
        # File handles prevent a Windows server child from retaining output pipes.
        with (cls.root / "commands.log").open("a") as output:
            result = subprocess.run(args, stdout=output, stderr=output, timeout=60,
                                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if result.returncode:
            raise RuntimeError((cls.root / "commands.log").read_text())

    @classmethod
    def stop_cluster(cls):
        if cls.started:
            cls.command("pg_ctl", "-D", str(cls.root / "data"), "-m", "fast", "-w", "stop")
            print("Disposable PostgreSQL cluster stopped; test logs retained in", cls.root)

    def setUp(self):
        self.connection.execute("BEGIN")

    def tearDown(self):
        self.connection.execute("ROLLBACK")

    def rows(self, number="17270"):
        return self.connection.execute(
            "SELECT * FROM public.fn_contract_repayment_schedule(%s) ORDER BY installment_no",
            (number,)).fetchall()

    def rejected(self, message, number="17270"):
        with self.assertRaisesRegex(psycopg.errors.InvalidParameterValue, message):
            with self.connection.transaction(force_rollback=True):
                self.rows(number)

    def test_17270_exact_stored_rate_and_totals(self):
        contract = self.connection.execute("SELECT contract_id,moratorium_months FROM public.contracts WHERE contract_number='17270'").fetchone()
        self.assertEqual(contract, {"contract_id": 6535, "moratorium_months": 1})
        rows = self.rows()
        self.assertEqual(len(rows), 24)
        self.assertEqual(sum(r["emi_amount"] for r in rows), D("384264.00"))
        self.assertEqual(sum(r["principal_amount"] for r in rows), D("299999.86"))
        self.assertEqual(sum(r["interest_amount"] for r in rows), D("84264.14"))
        self.assertEqual(rows[0]["interest_amount"], D("6249.25"))
        self.assertEqual(rows[0]["closing_principal"], D("290238.25"))
        self.assertEqual(str(rows[0]["due_date"]), "2026-11-05")
        self.assertEqual(str(rows[-1]["due_date"]), "2028-10-05")
        self.assertEqual(rows[-1]["opening_principal"], D("15684.42"))
        self.assertEqual(rows[-1]["interest_amount"], D("326.72"))
        self.assertEqual(rows[-1]["principal_amount"], D("15684.28"))
        self.assertEqual(rows[-1]["rounding_adjustment"], D("0.00"))
        self.assertEqual(rows[-1]["closing_principal"], D("0.14"))
        for previous, current in zip(rows, rows[1:]):
            self.assertEqual(previous["closing_principal"], current["opening_principal"])
        for row in rows:
            self.assertEqual(row["emi_amount"], row["principal_amount"] + row["interest_amount"])
            self.assertEqual(row["closing_principal"], row["opening_principal"] - row["principal_amount"])
            expected_interest = (row["opening_principal"] * D("24.9970") / D(1200)).quantize(D("0.01"), rounding=ROUND_HALF_UP)
            self.assertEqual(row["interest_amount"], expected_interest)
            self.assertEqual(row["rounding_adjustment"], 0)
        print("17270 strict formulas: 24 rows; principal 299999.86; interest 84264.14; repayment 384264.00; closing +0.14; adjustment 0.00")

    def test_read_only_minimum_permissions_and_unchanged_records(self):
        self.connection.execute("ROLLBACK")
        snapshot_sql = """
            SELECT (SELECT jsonb_agg(to_jsonb(c) ORDER BY c.contract_id) FROM public.contracts c) AS contracts,
                   (SELECT jsonb_agg(to_jsonb(r) ORDER BY r.repayment_structure_id)
                    FROM public.contract_repayment_structures r) AS repayments
        """
        before = self.connection.execute(snapshot_sql).fetchone()
        self.connection.execute("BEGIN READ ONLY")
        self.connection.execute("SET LOCAL ROLE schedule_stage1_reader")
        self.assertEqual(len(self.rows()), 24)
        after = self.connection.execute(snapshot_sql).fetchone()
        self.assertEqual(before, after)

    def simple_contract(self, principal, irr, installments, slabs, first="2026-01-31"):
        total = sum(D(str(count)) * D(str(amount)) for _, count, amount in slabs)
        self.connection.execute("""
            UPDATE public.contracts SET loan_amount=%s, irr_rate=%s, tenure_months=%s,
                total_contract_value=%s,finance_charges=%s,first_emi_date=%s
        """, (principal, irr, installments, total, total-D(str(principal)), first))
        self.connection.execute("DELETE FROM public.contract_repayment_structures")
        for sequence, count, amount in slabs:
            self.connection.execute("""
                INSERT INTO public.contract_repayment_structures
                    (contract_id,sequence_no,number_of_installments,installment_amount)
                SELECT contract_id,%s,%s,%s FROM public.contracts
            """, (sequence, count, amount))

    def test_month_end_anchor_and_zero_irr(self):
        self.simple_contract(300, 0, 3, [(1, 3, 100)])
        rows = self.rows()
        self.assertEqual([str(r["due_date"]) for r in rows],
                         ["2026-01-31", "2026-02-28", "2026-03-31"])
        self.assertTrue(all(r["interest_amount"] == 0 for r in rows))

    def test_moratorium_does_not_change_any_schedule_value(self):
        expected = self.rows()
        for months in (0, 1, 2, 12, None):
            with self.subTest(months=months), self.connection.transaction(force_rollback=True):
                self.connection.execute("UPDATE public.contracts SET moratorium_months=%s", (months,))
                self.assertEqual(self.rows(), expected)

    def test_correction_preserves_grants_owner_and_dependent_view(self):
        self.connection.execute("REVOKE ALL ON FUNCTION public.fn_contract_repayment_schedule(VARCHAR) FROM PUBLIC")
        self.connection.execute("GRANT EXECUTE ON FUNCTION public.fn_contract_repayment_schedule(VARCHAR) TO schedule_stage1_reader")
        self.connection.execute("CREATE VIEW public.schedule_stage1_dependency AS SELECT * FROM public.fn_contract_repayment_schedule('17270')")
        metadata_sql = """
            SELECT oid, proowner, proacl FROM pg_proc
            WHERE oid='public.fn_contract_repayment_schedule(character varying)'::regprocedure
        """
        before = self.connection.execute(metadata_sql).fetchone()
        self.connection.execute(STRICT_CORRECTION.read_text())
        self.assertEqual(self.connection.execute(metadata_sql).fetchone(), before)
        self.assertEqual(self.connection.execute("SELECT count(*) AS n FROM public.schedule_stage1_dependency").fetchone()["n"], 24)

    def test_ordered_stepped_slabs_and_postgresql_half_paise_rounding(self):
        self.simple_contract(100, D("6.0600"), 2,
                             [(2, 1, D("50.25")), (1, 1, D("50.51"))])
        rows = self.rows()
        self.assertEqual([r["emi_amount"] for r in rows], [D("50.51"), D("50.25")])
        self.assertEqual([r["interest_amount"] for r in rows], [D("0.51"), D("0.25")])
        self.assertEqual(rows[-1]["rounding_adjustment"], 0)

    def test_monthly_frequency_from_either_column(self):
        for column in ("payment_frequency", "repayment_terms"):
            with self.connection.transaction(force_rollback=True):
                self.connection.execute("UPDATE public.contracts SET payment_frequency='Monthly',repayment_terms='Monthly'")
                self.connection.execute(f"UPDATE public.contracts SET {column}=NULL")
                self.assertEqual(len(self.rows()), 24)

    def test_blank_and_missing_contract(self):
        for value, message in ((None, "required"), (" ", "required"), ("missing", "not found")):
            with self.subTest(value=value), self.connection.transaction(force_rollback=True):
                self.rejected(message, value)

    def test_duplicate_contract_number_across_types(self):
        self.connection.execute("""
            INSERT INTO public.contracts(contract_number,contract_type,status)
            VALUES ('17270','OTHER','Y')
        """)
        self.rejected("Ambiguous contract")

    def test_unsupported_or_missing_inputs(self):
        cases = [
            ("is_first_emi_paid=true", "Upfront EMI contracts"),
            ("is_first_emi_paid=NULL", "status is unavailable"),
            ("repayment_type='Interest'", "repayment type"),
            ("payment_frequency='Quarterly'", "Monthly repayment"),
            ("repayment_terms='Bullet'", "Monthly repayment"),
            ("payment_frequency=NULL,repayment_terms=NULL", "Monthly repayment"),
            ("payment_frequency='unknown'", "Monthly repayment"),
            ("loan_amount=NULL", "financial inputs"),
            ("loan_amount=0", "financial inputs"),
            ("loan_amount='NaN'", "financial inputs"),
            ("irr_rate=NULL", "financial inputs"),
            ("irr_rate=-1", "financial inputs"),
            ("irr_rate='NaN'", "financial inputs"),
            ("first_emi_date=NULL", "financial inputs"),
            ("first_emi_date='infinity'", "financial inputs"),
            ("tenure_months=NULL", "financial inputs"),
            ("finance_charges=NULL", "financial inputs"),
            ("finance_charges='NaN'", "financial inputs"),
            ("total_contract_value=NULL", "financial inputs"),
            ("total_contract_value='NaN'", "financial inputs"),
            ("tenure_months=25", "tenure-inconsistent"),
            ("finance_charges=84265", "totals do not reconcile"),
            ("total_contract_value=384265", "totals do not reconcile"),
        ]
        for assignment, message in cases:
            with self.subTest(assignment=assignment), self.connection.transaction(force_rollback=True):
                self.connection.execute("UPDATE public.contracts SET " + assignment)
                self.rejected(message)

    def test_missing_slabs_and_nan_installment(self):
        for mutation, message in (
            ("DELETE FROM public.contract_repayment_structures", "repayment structure"),
            ("UPDATE public.contract_repayment_structures SET installment_amount='NaN'", "repayment structure"),
        ):
            with self.subTest(mutation=mutation), self.connection.transaction(force_rollback=True):
                self.connection.execute(mutation)
                self.rejected(message)

    def test_negative_amortisation(self):
        self.simple_contract(100, 12, 2, [(1, 1, D("0.50")), (2, 1, D("100.50"))])
        self.rejected("Unsupported amortisation")

    def test_early_payoff_rejected(self):
        self.simple_contract(100, 0, 2, [(1, 1, 100), (2, 1, 1)])
        self.rejected("Unsupported amortisation")

    def test_small_residual_does_not_trigger_negative_interest_reconciliation(self):
        self.simple_contract(100, D("0.12"), 2, [(1, 2, 50)])
        rows = self.rows()
        self.assertEqual(rows[-1]["interest_amount"], D("0.01"))
        self.assertEqual(rows[-1]["closing_principal"], D("0.02"))
        self.assertTrue(all(row["rounding_adjustment"] == 0 for row in rows))

    def test_final_residuals_of_both_signs_have_no_cap_or_adjustment(self):
        cases = [
            (0, D("101.00"), D("-1.00")),
            (24, D("101.00"), D("1.00")),
            (0, D("101.01"), D("-1.01")),
            (D("24.12"), D("101.00"), D("1.01")),
        ]
        for irr, amount, residual in cases:
            with self.subTest(irr=irr, amount=amount), self.connection.transaction(force_rollback=True):
                self.simple_contract(100, irr, 1, [(1, 1, amount)])
                row = self.rows()[0]
                self.assertEqual(row["rounding_adjustment"], 0)
                self.assertEqual(row["emi_amount"], amount)
                self.assertEqual(row["closing_principal"], residual)

    def test_large_17270_residuals_do_not_reject_or_rebalance_the_final_row(self):
        for irr in (D("25.1"), D("24.9")):
            with self.subTest(irr=irr), self.connection.transaction(force_rollback=True):
                self.connection.execute("UPDATE public.contracts SET irr_rate=%s", (irr,))
                rows = self.rows()
                self.assertEqual(len(rows), 24)
                self.assertGreater(abs(rows[-1]["closing_principal"]), 1)
                self.assertTrue(all(row["rounding_adjustment"] == 0 for row in rows))
                for row in rows:
                    self.assertEqual(row["interest_amount"], (row["opening_principal"] * irr / D(1200)).quantize(D("0.01"), rounding=ROUND_HALF_UP))

    def test_documented_read_only_sql_queries(self):
        self.connection.execute("ROLLBACK")
        sql = (API / "docs/repayment-schedule-stage1-validation.sql").read_text()
        checked_totals = checked_invariants = False
        with self.connection.cursor() as cursor:
            cursor.execute(sql)
            while True:
                if cursor.description:
                    for row in cursor.fetchall():
                        if "expected_17270_totals_pass" in row:
                            self.assertTrue(row["expected_17270_totals_pass"])
                            checked_totals = True
                        if "row_equations_pass" in row:
                            self.assertTrue(all(row.values()))
                            checked_invariants = True
                if not cursor.nextset():
                    break
        self.assertTrue(checked_totals and checked_invariants)

    def test_existing_name_is_not_overwritten(self):
        with self.assertRaises(psycopg.errors.DuplicateFunction):
            self.connection.execute(MIGRATION.read_text())


if __name__ == "__main__":
    unittest.main(verbosity=2)
