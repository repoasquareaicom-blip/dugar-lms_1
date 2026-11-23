"""Isolated SQL -> repository -> service -> PDF integration, never an application DB."""
import importlib.util
from pathlib import Path
import os
import subprocess

api = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("schedule_fixture", Path(__file__).with_name("test_full_precision_repayment_schedule.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
fixture = module.FullPrecisionScheduleTest
try:
    fixture.setUpClass()
    fixture.connection.execute((api / "src/main/resources/db/migration/V006__create_party_masters.sql").read_text())
    fixture.connection.execute("""
        INSERT INTO public.party_masters(party_code,party_type,full_name)
        VALUES ('TEST17270','BORROWER','Test Borrower 17270');
        UPDATE public.contracts SET borrower_code='TEST17270',contract_date='2026-10-05';
        CREATE TABLE public.users(user_id BIGINT, user_group TEXT, user_type TEXT);
        CREATE TABLE public.user_areas(user_id BIGINT, area_code TEXT);
        CREATE TABLE public.user_contracts(user_id BIGINT, contract_number TEXT);
    """)
    environment = os.environ.copy()
    environment['REPAYMENT_SCHEDULE_TEST_URL'] = f'jdbc:postgresql://127.0.0.1:{fixture.port}/repayment_schedule_stage1_test'
    result = subprocess.run(['mvn.cmd' if os.name == 'nt' else 'mvn', '-q', '-Dtest=RepaymentSchedulePostgresTest', 'test'],
                            cwd=api, env=environment, timeout=180)
    raise SystemExit(result.returncode)
finally:
    fixture.doClassCleanups()
