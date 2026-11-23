"""Isolated V082 regression; no application database credentials or deployment."""
from decimal import Decimal as D, localcontext, ROUND_HALF_UP
import unittest
from test_contract_repayment_schedule import RepaymentScheduleTest as Baseline, MIGRATIONS


class FullPrecisionScheduleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from pathlib import Path
        import tempfile, socket, psycopg
        from psycopg.rows import dict_row
        cls.root = Path(tempfile.mkdtemp(prefix="dugar-full-precision-test-"))
        cls.started = False
        cls.addClassCleanup(cls.stop_cluster)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            cls.port = sock.getsockname()[1]
        cls.command("initdb", "-D", str(cls.root / "data"), "-U", "postgres", "-A", "trust", "--encoding=UTF8", "--no-locale")
        cls.command("pg_ctl", "-D", str(cls.root / "data"), "-l", str(cls.root / "postgres.log"),
                    "-o", f"-h 127.0.0.1 -p {cls.port}", "-w", "start")
        cls.started = True
        with psycopg.connect(host="127.0.0.1", port=cls.port, user="postgres", dbname="postgres", autocommit=True) as connection:
            connection.execute("CREATE DATABASE repayment_schedule_stage1_test")
        cls.connection = psycopg.connect(host="127.0.0.1", port=cls.port, user="postgres", dbname="repayment_schedule_stage1_test",
                                          autocommit=True, row_factory=dict_row, options="-c statement_timeout=10000")
        cls.addClassCleanup(cls.connection.close)
        for name in ("V001__create_contract_tables.sql", "V009__create_contract_repayment_structures.sql",
                     "V011__add_remaining_financial_terms_columns.sql"):
            cls.connection.execute((MIGRATIONS / name).read_text())
        cls.connection.execute("ALTER TABLE public.contracts ADD COLUMN is_first_emi_paid BOOLEAN")
        cls.connection.execute("""
            INSERT INTO public.contracts(contract_id,contract_type,contract_number,status,loan_amount,irr_rate,
                first_emi_date,tenure_months,finance_charges,total_contract_value,payment_frequency,repayment_terms,
                repayment_type,moratorium_months,is_first_emi_paid)
            VALUES (6535,'HP','17270','Y',300000,24.9970,'2026-11-05',24,84264,384264,'Monthly','Monthly','EMI',1,false);
            INSERT INTO public.contract_repayment_structures(contract_id,sequence_no,number_of_installments,installment_amount)
            VALUES (6535,1,24,16011);
            CREATE ROLE schedule_stage1_reader;
            GRANT USAGE ON SCHEMA public TO schedule_stage1_reader;
            GRANT SELECT ON public.contracts, public.contract_repayment_structures TO schedule_stage1_reader;
        """)
        cls.connection.execute((MIGRATIONS / "V081__strict_excel_contract_repayment_schedule.sql").read_text())
        cls.connection.execute((MIGRATIONS / "V082__full_precision_contract_repayment_schedule.sql").read_text())

    command = classmethod(Baseline.command.__func__)
    stop_cluster = classmethod(Baseline.stop_cluster.__func__)

    setUp = Baseline.setUp
    tearDown = Baseline.tearDown
    rows = Baseline.rows
    rejected = Baseline.rejected
    simple_contract = Baseline.simple_contract
    test_blank_and_missing_contract = Baseline.test_blank_and_missing_contract
    test_duplicate_contract_number_across_types = Baseline.test_duplicate_contract_number_across_types
    test_missing_slabs_and_nan_installment = Baseline.test_missing_slabs_and_nan_installment
    test_negative_amortisation = Baseline.test_negative_amortisation
    test_early_payoff_rejected = Baseline.test_early_payoff_rejected
    test_read_only_minimum_permissions_and_unchanged_records = Baseline.test_read_only_minimum_permissions_and_unchanged_records
    test_moratorium_does_not_change_any_schedule_value = Baseline.test_moratorium_does_not_change_any_schedule_value
    test_month_end_anchor_and_zero_irr = Baseline.test_month_end_anchor_and_zero_irr

    def check_oracle(self, principal, rate, payments, number="17270"):
        rows = self.rows(number)
        self.assertEqual(len(rows), len(payments))
        with localcontext() as context:
            context.prec = 200
            balance = D(str(principal))
            for row, payment in zip(rows, payments):
                opening = balance
                interest = opening * D(str(rate)) / 1200
                principal_paid = D(str(payment)) - interest
                balance = opening - principal_paid
                for key, value in (("opening_principal", opening), ("emi_amount", D(str(payment))),
                                   ("interest_amount", interest), ("principal_amount", principal_paid),
                                   ("closing_principal", balance)):
                    self.assertEqual(row[key], value.quantize(D(1), rounding=ROUND_HALF_UP), (row["installment_no"], key))
                self.assertEqual(row["rounding_adjustment"], 0)
        return rows

    def test_17270_every_installment_and_zero_display_residual(self):
        rows = self.check_oracle(300000, "24.9970", [16011]*24)
        self.assertEqual(rows[-1]["closing_principal"], 0)
        raw = self.connection.execute("SELECT * FROM public.fn_contract_repayment_schedule_unrounded('17270') ORDER BY installment_no").fetchall()
        self.assertNotEqual(raw[-1]["closing_principal"], 0)
        for previous, current in zip(raw, raw[1:]):
            self.assertEqual(previous["closing_principal"], current["opening_principal"])
        self.assertTrue(any(r["interest_amount"] != r["interest_amount"].quantize(D(".01")) for r in raw))
        print("17270: all 24 installments match decimal oracle; displayed final balance 0; internal residual retained")

    def test_17276_actual_database_inputs_and_negative_one_display_residual(self):
        # Read-only verified against contracts + contract_repayment_structures.
        # All mutations below affect only this disposable fixture transaction.
        self.simple_contract(450000, D("22.9993"), 48, [(1, 48, 14423)], first="2026-11-05")
        self.connection.execute("UPDATE public.contracts SET contract_number='17276',payment_frequency=NULL,repayment_terms=NULL,repayment_type=NULL")
        rows = self.check_oracle(450000, "22.9993", [14423]*48, "17276")
        self.assertEqual(rows[-1]["closing_principal"], -1)
        self.assertEqual(sum(r["emi_amount"] for r in rows), 692304)
        print("17276: actual stored inputs; 48 installments; displayed final balance -1; EMI unchanged")

    def test_client_excel_installment_values(self):
        import os, zipfile, xml.etree.ElementTree as ET
        from datetime import date, timedelta
        from pathlib import Path
        workbook = Path(os.environ.get("REPAYMENT_SCHEDULE_EXCEL", r"H:\workspace\Dugar\LMS\Repaymane.xlsx"))
        if not workbook.exists():
            self.skipTest("Client workbook unavailable; set REPAYMENT_SCHEDULE_EXCEL")
        ns = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
        with zipfile.ZipFile(workbook) as source:
            sheet = ET.fromstring(source.read("xl/worksheets/sheet1.xml"))
        cells = {c.get("r"): c.find("m:v", ns).text for c in sheet.findall(".//m:c", ns)
                 if c.find("m:v", ns) is not None}
        rows = self.rows()
        raw = self.connection.execute("SELECT * FROM public.fn_contract_repayment_schedule_unrounded('17270') ORDER BY installment_no").fetchall()
        mapping = {"C": "opening_principal", "D": "emi_amount", "E": "principal_amount",
                   "F": "interest_amount", "G": "closing_principal"}
        self.assertEqual(date(1899, 12, 30) + timedelta(days=int(cells["B9"])), date(2026, 10, 25))
        self.assertEqual(rows[0]["due_date"], date(2026, 11, 5))
        for offset, (row, precise) in enumerate(zip(rows, raw), 9):
            self.assertEqual(row["installment_no"], int(cells[f"A{offset}"]))
            for column, field in mapping.items():
                expected = D(cells[f"{column}{offset}"])
                self.assertEqual(row[field], expected.quantize(D(1), rounding=ROUND_HALF_UP), (offset, field))
                self.assertLess(abs(precise[field] - expected), D("0.0000001"), (offset, field))
        self.assertEqual(len(rows), 24)
        print("Client Excel: all 24 installment numbers and 120 monetary cells match at whole rupees; raw values within 0.0000001")

    def test_multiple_slabs_with_fractional_emi(self):
        self.simple_contract(100, D("6.0600"), 2, [(2, 1, D("50.25")), (1, 1, D("50.51"))])
        self.check_oracle(100, "6.0600", ["50.51", "50.25"])

    def test_blank_frequency_defaults_to_monthly_and_invalid_still_rejected(self):
        expected = self.rows()
        for frequency, terms in ((None, None), ("", " "), ("Monthly", None), (None, "Monthly")):
            self.connection.execute("UPDATE public.contracts SET payment_frequency=%s,repayment_terms=%s", (frequency, terms))
            self.assertEqual(self.rows(), expected)
        self.connection.execute("UPDATE public.contracts SET repayment_terms='Quarterly'")
        self.rejected("Monthly repayment")

    def test_preserved_financial_validations(self):
        for assignment in ("is_first_emi_paid=true", "is_first_emi_paid=NULL", "repayment_type='Interest'",
                           "loan_amount=NULL", "loan_amount=0", "loan_amount='NaN'", "irr_rate=-1",
                           "irr_rate=NULL", "irr_rate='NaN'", "first_emi_date=NULL", "first_emi_date='infinity'",
                           "tenure_months=NULL", "tenure_months=25", "finance_charges=NULL",
                           "finance_charges='NaN'", "total_contract_value=NULL", "total_contract_value='NaN'",
                           "finance_charges=84265", "total_contract_value=384265"):
            with self.subTest(assignment=assignment), self.connection.transaction(force_rollback=True):
                self.connection.execute("UPDATE public.contracts SET " + assignment)
                self.rejected(".")

    def test_final_residuals_and_contractual_emi_are_not_adjusted(self):
        for rate, payment in ((0, "101"), (24, "101"), (0, "101.51"), ("24.12", "101")):
            with self.subTest(rate=rate), self.connection.transaction(force_rollback=True):
                self.simple_contract(100, D(str(rate)), 1, [(1, 1, D(payment))])
                self.check_oracle(100, rate, [payment])

    def test_replace_preserves_oid_grants_and_dependent_view(self):
        self.connection.execute("REVOKE ALL ON FUNCTION public.fn_contract_repayment_schedule(VARCHAR) FROM PUBLIC")
        self.connection.execute("GRANT EXECUTE ON FUNCTION public.fn_contract_repayment_schedule(VARCHAR) TO schedule_stage1_reader")
        self.connection.execute("CREATE VIEW schedule_dependency AS SELECT * FROM public.fn_contract_repayment_schedule('17270')")
        sql = "SELECT oid,proowner,proacl FROM pg_proc WHERE oid='public.fn_contract_repayment_schedule(character varying)'::regprocedure"
        before = self.connection.execute(sql).fetchone()
        self.connection.execute((MIGRATIONS / "V082__full_precision_contract_repayment_schedule.sql").read_text())
        self.assertEqual(self.connection.execute(sql).fetchone(), before)
        self.assertEqual(self.connection.execute("SELECT count(*) AS n FROM schedule_dependency").fetchone()["n"], 24)


if __name__ == "__main__":
    # Imported baseline remains a V081 historical regression, not part of this run.
    unittest.main(defaultTest="FullPrecisionScheduleTest", verbosity=2)
