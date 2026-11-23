"""Regression tests using a disposable PostgreSQL cluster, never application data.

Run: python src/test/python/test_reminder_financial_mapping.py
Uses the existing V059 ageing procedure for real FIFO allocation, then V078/V083.
The unchanged V059 and live _test_2 financial SELECT bodies were compared during
the read-only investigation. No financial formula is reproduced in this fixture.
"""
from decimal import Decimal as D
from pathlib import Path
import os
import shutil
import socket
import subprocess
import tempfile
import unittest

import psycopg
from psycopg.rows import dict_row

API = Path(__file__).resolve().parents[3]
MIGRATIONS = API / "src/main/resources/db/migration"
CORRECTION = MIGRATIONS / "V083__correct_reminder_overdue_and_partial_emi.sql"


class ReminderFinancialMappingTest(unittest.TestCase):
    @classmethod
    def command(cls, *args):
        with (cls.root / "commands.log").open("a") as output:
            result = subprocess.run(args, stdout=output, stderr=output, timeout=60,
                                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if result.returncode:
            raise RuntimeError((cls.root / "commands.log").read_text())

    @classmethod
    def stop_cluster(cls):
        if cls.started:
            cls.command("pg_ctl", "-D", str(cls.root / "data"), "-m", "fast", "-w", "stop")
        print("Disposable cluster logs:", cls.root)

    @classmethod
    def setUpClass(cls):
        cls.root = Path(tempfile.mkdtemp(prefix="dugar-reminder-financial-test-"))
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
        cls.command("pg_ctl", "-D", str(cls.root / "data"), "-l", str(cls.root / "postgres.log"),
                    "-o", f"-h 127.0.0.1 -p {cls.port}", "-w", "start")
        cls.started = True
        with psycopg.connect(host="127.0.0.1", port=cls.port, user="postgres",
                             dbname="postgres", autocommit=True) as admin:
            admin.execute("CREATE DATABASE reminder_financial_test")
            # Optional existing Java/Flyway suites use a separate disposable database.
            admin.execute("CREATE DATABASE document_menu_test")
        cls.db = psycopg.connect(host="127.0.0.1", port=cls.port, user="postgres",
                                dbname="reminder_financial_test", autocommit=True,
                                row_factory=dict_row, options="-c statement_timeout=15000")
        cls.addClassCleanup(cls.db.close)
        cls.db.execute("""
            CREATE TABLE contracts(contract_id BIGINT PRIMARY KEY,contract_number TEXT,
              contract_type TEXT,area_code TEXT,borrower_code TEXT,guarantor_code TEXT,
              guarantor_2_code TEXT,contract_date DATE,first_emi_date DATE,vehicle_make TEXT,
              registration_number TEXT,manufacturing_year INTEGER,category TEXT,
              total_contract_value NUMERIC,finance_charges NUMERIC,updated_by TEXT,
              is_active BOOLEAN DEFAULT TRUE,is_draft BOOLEAN DEFAULT FALSE,
              status TEXT DEFAULT 'Y',loan_close_date DATE);
            CREATE TABLE users(user_id BIGINT,user_group TEXT);
            CREATE TABLE party_masters(party_code TEXT,full_name TEXT,salutation TEXT,swd_name TEXT,
              address_line_1 TEXT,address_line_2 TEXT,area TEXT,city TEXT,state TEXT,pin_code TEXT);
            CREATE TABLE g_others(other_code TEXT,other_desc TEXT);
            CREATE TABLE assets(asset_id BIGINT,contract_id BIGINT,vehicle_make TEXT,version TEXT,owner_serial_no TEXT);
            CREATE TABLE contract_repayment_structures(contract_id BIGINT,sequence_no INTEGER,
              number_of_installments INTEGER,installment_amount NUMERIC);
            CREATE TABLE voucher_headers(voucher_header_id BIGINT, voucher_date DATE,status TEXT,voucher_type TEXT);
            CREATE TABLE voucher_details(voucher_header_id BIGINT,sub_ledger_code TEXT,ledger_code TEXT,
              debit_amount NUMERIC,credit_amount NUMERIC);
            -- Sentinel proves which numeric amount is sent to the existing formatter.
            CREATE FUNCTION fn_doc_inr_words(n NUMERIC) RETURNS TEXT LANGUAGE SQL AS
              $$ SELECT 'words for ' || n::text $$;
            INSERT INTO contracts(contract_id,contract_number,first_emi_date,total_contract_value,finance_charges)
              VALUES(2136,'13161','2023-08-25',638000,238000),
                    (1887,'12755','2023-04-16',1218000,0);
            INSERT INTO contract_repayment_structures VALUES
              (2136,1,41,15200),(2136,2,1,14800),(1887,1,46,26000),(1887,2,1,22000);
            INSERT INTO voucher_headers VALUES(1,'2025-08-02','AUTHORISED','BR'),(2,'2026-02-16','AUTHORISED','BR');
            INSERT INTO voucher_details VALUES(1,'13161','3001',0,441800),(2,'12755','3001',0,898000);
        """)
        cls.db.execute((MIGRATIONS / "V059__add_last_payment_date_to_branch_wise_ageing.sql").read_text(encoding="utf-8-sig"))
        cls.db.execute((API / "docs/reminder-letter-function-before-v078.sql").read_text(encoding="utf-8-sig"))
        cls.db.execute((MIGRATIONS / "V078__reminder_letter_contract_placeholders.sql").read_text(encoding="utf-8-sig"))
        cls.db.execute(CORRECTION.read_text(encoding="utf-8-sig"))
        cls.db.execute((MIGRATIONS / "V084__validate_reminder_financial_mapping_compatibility.sql").read_text(encoding="utf-8-sig"))
        print("Disposable PostgreSQL port:", cls.port)

    def setUp(self):
        self.db.execute("BEGIN")

    def tearDown(self):
        self.db.execute("ROLLBACK")

    def calculate(self, date="2026-10-24"):
        self.db.execute("CALL public.sp_branch_wise_ageing_test(%s,NULL::varchar,'admin'::varchar)", (date,))

    def letter(self, contract_id, date="2026-10-24"):
        return self.db.execute("SELECT * FROM fn_doc_reminder_letter(%s,%s)", (contract_id, date)).fetchone()

    def test_reference_contracts_use_fifo_balance_and_demand_overdue_amount(self):
        self.calculate()
        for contract_id, emi, partial, overdue in [(2136,15200,14200,135800),(1887,26000,12000,220000)]:
            with self.subTest(contract_id=contract_id):
                report = self.db.execute("SELECT * FROM tmp_contract_report WHERE contract_id=%s", (contract_id,)).fetchone()
                letter = self.letter(contract_id)
                self.assertEqual(len(letter), 13)
                self.assertEqual(letter["EMI"], D(emi))
                self.assertEqual(letter["CURRENT_DUE"], D(partial))
                self.assertEqual(letter["NO_OF_OVERDUES"], report["overdue_emi_count"])
                self.assertEqual(letter["NO_OF_OVERDUES"], 9)
                self.assertEqual(letter["TOTAL_OS"], report["overdue_amount"])
                self.assertEqual(letter["TOTAL_OS"], D(overdue))
                self.assertNotEqual(letter["TOTAL_OS"], report["total_outstanding"])
                self.assertEqual(letter["TOTAL_OS_WORDS"], f"words for {overdue}.00")
        # Demand List current_due retains its original next-EMI meaning.
        self.assertEqual(self.db.execute("SELECT current_due FROM tmp_contract_report WHERE contract_id=1887").fetchone()["current_due"], D(26000))

    def test_fully_paid_emi_excluded_next_fully_unpaid_emi_retains_full_balance(self):
        self.db.execute("UPDATE voucher_details SET credit_amount=910000 WHERE sub_ledger_code='12755'")
        self.calculate()
        self.assertEqual(self.letter(1887)["CURRENT_DUE"], D(26000))
        self.assertEqual(self.letter(1887)["NO_OF_OVERDUES"], 8)
        self.assertEqual(self.letter(1887)["TOTAL_OS"], D(208000))

    def test_decimal_partial_balance_is_preserved_after_fifo(self):
        self.db.execute("UPDATE voucher_details SET credit_amount=898000.25 WHERE sub_ledger_code='12755'")
        self.calculate()
        letter = self.letter(1887)
        self.assertEqual(letter["CURRENT_DUE"], D("11999.75"))
        self.assertEqual(letter["TOTAL_OS"], D("219999.75"))
        self.assertEqual(letter["TOTAL_OS_WORDS"], "words for 219999.75")

    def test_zero_overdues_with_future_balance_returns_zero_partial_and_overdue(self):
        self.calculate("2023-04-16")  # EMI due today is not overdue.
        letter = self.letter(1887,"2023-04-16")
        self.assertEqual(letter["EMI"], D(26000))
        self.assertEqual(letter["CURRENT_DUE"], D(0))
        self.assertEqual(letter["TOTAL_OS"], D(0))
        self.assertEqual(letter["TOTAL_OS_WORDS"], "words for 0.00")
        self.assertEqual(letter["NO_OF_OVERDUES"], 0)

    def test_future_partial_emi_is_not_selected(self):
        self.db.execute("UPDATE voucher_headers SET voucher_date='2023-04-15' WHERE voucher_header_id=2")
        self.db.execute("UPDATE voucher_details SET credit_amount=14000 WHERE sub_ledger_code='12755'")
        self.calculate("2023-04-15")
        self.assertEqual(self.letter(1887,"2023-04-15")["CURRENT_DUE"], D(0))

    def test_fully_paid_contract_returns_zero(self):
        self.db.execute("UPDATE voucher_details SET credit_amount=1218000 WHERE sub_ledger_code='12755'")
        self.calculate()
        letter = self.letter(1887)
        self.assertEqual(letter["CURRENT_DUE"], D(0))
        self.assertEqual(letter["TOTAL_OS"], D(0))
        self.assertEqual(letter["NO_OF_OVERDUES"], 0)

    def test_as_on_date_controls_overdue_selection(self):
        self.calculate("2026-02-16")
        self.assertEqual(self.letter(1887,"2026-02-16")["CURRENT_DUE"], D(0))
        self.calculate("2026-02-17")
        letter = self.letter(1887,"2026-02-17")
        self.assertEqual(letter["CURRENT_DUE"], D(12000))
        self.assertEqual(letter["TOTAL_OS"], D(12000))
        self.assertEqual(letter["NO_OF_OVERDUES"], 1)

    def test_missing_ageing_tables_fail_instead_of_returning_fabricated_zero(self):
        with self.assertRaisesRegex(psycopg.errors.RaiseException,"Aging Analysis has not been run"):
            self.letter(1887)

    def test_java_document_suites(self):
        if not os.environ.get("RUN_REMINDER_JAVA_TESTS"):
            self.skipTest("Set RUN_REMINDER_JAVA_TESTS=1 to run existing Java/Flyway/render/cache suites")
        env = dict(os.environ, DOCUMENT_MENU_TEST_URL=f"jdbc:postgresql://127.0.0.1:{self.port}/document_menu_test")
        result = subprocess.run([shutil.which("mvn"), "-q",
            "-Dtest=" + os.environ.get("REMINDER_JAVA_TEST_CLASSES", "ReminderLetterFunctionMigrationTest,ReminderLetterFinancialMigrationTest,ReminderLetterCompatibilityMigrationTest,DocumentEnhancementIntegrationTest,DocumentAutomationAuthorizationTest,DocumentDocxRoundTripTest"),
            "test"], cwd=API, env=env, timeout=240,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        self.assertEqual(result.returncode,0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
