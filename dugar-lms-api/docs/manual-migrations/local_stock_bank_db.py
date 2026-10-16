"""Local-development-only connection helper; never prints credentials."""
import os
from pathlib import Path
from urllib.parse import urlparse
import psycopg
ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent

def connect():
    source = (ROOT / 'src/main/resources/application.properties').read_text()
    def prop(name):
        value = next(line.split('=', 1)[1].strip() for line in source.splitlines() if line.startswith(name + '='))
        if value.startswith('${'):
            key, default = value[2:-1].split(':', 1)
            return os.environ.get(key, default)
        return value
    url = urlparse(prop('spring.datasource.url').removeprefix('jdbc:'))
    if url.hostname not in ('127.0.0.1', 'localhost', '::1') or url.path != '/dugar_lms' or url.port != 5434:
        raise RuntimeError('Refusing database other than documented local development dugar_lms.')
    return psycopg.connect(host=url.hostname, port=url.port or 5432, dbname=url.path[1:],
        user=prop('spring.datasource.username'), password=prop('spring.datasource.password'), connect_timeout=5)

if __name__ == '__main__':
    import json
    from psycopg.rows import dict_row
    with connect() as c:
        c.read_only = True
        with c.cursor(row_factory=dict_row) as q:
            result = {}
            queries = {
                'database': "SELECT current_database() AS database, inet_server_addr()::text AS address, inet_server_port() AS port, pg_is_in_recovery() AS recovery",
                'columns': "SELECT table_name,column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('bank_masters','contract_details') ORDER BY table_name,ordinal_position",
                'constraints': "SELECT conrelid::regclass::text AS table_name,conname,pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conrelid IN ('bank_masters'::regclass,'contract_details'::regclass)",
                'counts': "SELECT COUNT(*) AS detail_rows,COUNT(DISTINCT contract_id) AS contracts,COUNT(*) FILTER(WHERE NULLIF(BTRIM(stock_marked_to_bank),'') IS NOT NULL) AS named_rows,COUNT(DISTINCT contract_id) FILTER(WHERE NULLIF(BTRIM(stock_marked_to_bank),'') IS NOT NULL) AS named_contracts FROM contract_details",
                'nameMappings': "SELECT cd.stock_marked_to_bank,COUNT(*) AS detail_rows,(SELECT COUNT(*) FROM bank_masters b WHERE LOWER(BTRIM(b.bank_name))=LOWER(BTRIM(cd.stock_marked_to_bank))) AS master_matches,(SELECT ARRAY_AGG(b.id ORDER BY b.id) FROM bank_masters b WHERE LOWER(BTRIM(b.bank_name))=LOWER(BTRIM(cd.stock_marked_to_bank))) AS bank_ids FROM contract_details cd WHERE NULLIF(BTRIM(cd.stock_marked_to_bank),'') IS NOT NULL GROUP BY cd.stock_marked_to_bank ORDER BY cd.stock_marked_to_bank",
                'duplicateBankNames': "SELECT LOWER(BTRIM(bank_name)) AS normalized_name,COUNT(*) AS count,ARRAY_AGG(id) AS ids FROM bank_masters GROUP BY LOWER(BTRIM(bank_name)) HAVING COUNT(*)>1",
                'duplicateDetails': "SELECT contract_id,COUNT(*) AS count FROM contract_details GROUP BY contract_id HAVING COUNT(*)>1",
                'roles': "SELECT role_id,role_code,role_name,is_active FROM roles WHERE UPPER(TRIM(role_code)) IN ('SUPER_ADMIN','CREDIT')",
                'menus': "SELECT menu_id,parent_id,menu_code,menu_name,url_path,is_active,is_visible FROM menus WHERE UPPER(TRIM(menu_name)) IN ('CREDIT','TRANSACTION','TRANSACTIONS','STOCK MARKING TO BANK') OR menu_code='CREDIT_TRANSACTION_STOCK_BANK_MARKING' ORDER BY menu_id",
                'procedureHashes': "SELECT oid::regprocedure::text AS signature,md5(pg_get_functiondef(oid)) AS hash FROM pg_proc WHERE proname='sp_branch_wise_ageing_test'"
            }
            for key, sql in queries.items():
                q.execute(sql); result[key] = q.fetchall()
            result['idColumnExists'] = any(r['column_name']=='stock_marked_bank_id' for r in result['columns'])
            if result['idColumnExists']:
                q.execute('SELECT COUNT(stock_marked_bank_id) AS assigned FROM contract_details'); result['existingAssignments']=q.fetchall()
            (OUT/'stock-bank-preflight-results.json').write_text(json.dumps(result,indent=2,default=str))
            print(json.dumps(result,indent=2,default=str))
