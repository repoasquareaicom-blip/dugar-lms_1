"""Authorized local execution with backup, atomic validation and menu configuration."""
import csv, hashlib, json
from datetime import datetime, timezone
from local_stock_bank_db import connect, OUT

def rows(q, sql):
    q.execute(sql)
    return q.fetchall()

def execute_script(q, filename):
    # Run reviewed SQL inside the caller transaction; commit only after validation.
    source = (OUT / filename).read_text()
    source = '\n'.join(line for line in source.splitlines() if line.strip() not in ('BEGIN;', 'COMMIT;'))
    q.execute(source)
    while q.nextset():
        pass

def validate(q):
    source = (OUT / 'stock-bank-validation.sql').read_text()
    q.execute(source)
    outputs = []
    while True:
        outputs.append({'columns': [col.name for col in q.description], 'rows': q.fetchall()})
        if not q.nextset(): break
    # First result: counts; sixth: complete mapping audit. Others must have no exceptions.
    for index in (1, 2, 6, 7, 8, 9):
        assert not outputs[index]['rows'], ('Validation exceptions', index, outputs[index])
    assert outputs[3]['rows'] == [(0,)], 'Missing backup rows'
    assert outputs[4]['rows'] == [(0,)], 'Legacy text changed'
    assert outputs[0]['rows'][0][3] == 0, 'Unresolved assignment'
    return outputs

stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
with connect() as c:
    with c.cursor() as q:
        q.execute("SET LOCAL lock_timeout = '10s'")
        q.execute('LOCK TABLE contract_details IN SHARE ROW EXCLUSIVE MODE')
        q.execute('LOCK TABLE bank_masters IN SHARE MODE')
        q.execute('LOCK TABLE menus,role_permissions IN SHARE ROW EXCLUSIVE MODE')
        identity = rows(q, 'SELECT current_database(),inet_server_addr()::text,inet_server_port()')
        assert identity[0][0] == 'dugar_lms' and identity[0][1] in ('127.0.0.1/32', '::1/128')
        before = rows(q, "SELECT contract_detail_id,contract_id,stock_marked_to_bank,to_jsonb(cd)->>'stock_marked_bank_id',md5((to_jsonb(cd)-'stock_marked_bank_id')::text) FROM contract_details cd ORDER BY contract_detail_id")
        banks_before = rows(q, 'SELECT to_jsonb(b) FROM bank_masters b ORDER BY id')
        procedures_before = rows(q, "SELECT oid::regprocedure::text,md5(pg_get_functiondef(oid)) FROM pg_proc WHERE proname='sp_branch_wise_ageing_test' ORDER BY oid")
        menus_before = rows(q, 'SELECT to_jsonb(m) FROM menus m ORDER BY menu_id')
        permissions_before = rows(q, 'SELECT to_jsonb(rp) FROM role_permissions rp ORDER BY permission_id')
        name_counts = rows(q, "SELECT stock_marked_to_bank,COUNT(*) FROM contract_details WHERE NULLIF(BTRIM(stock_marked_to_bank),'') IS NOT NULL GROUP BY stock_marked_to_bank ORDER BY stock_marked_to_bank")
        snapshot = OUT / ('stock-bank-mappings-' + stamp + '.csv')
        with snapshot.open('x', newline='', encoding='utf-8') as f:
            w = csv.writer(f); w.writerow(['contract_detail_id','contract_id','stock_marked_to_bank','original_bank_id','other_fields_hash']); w.writerows(before)
        (OUT / ('stock-bank-before-' + stamp + '.json')).write_text(json.dumps({'database':identity,'banks':banks_before,'procedures':procedures_before,'menus':menus_before,'permissions':permissions_before}, indent=2, default=str))
        execute_script(q, 'stock-bank-id.sql')
        validations = validate(q)
        after = rows(q, "SELECT contract_detail_id,contract_id,stock_marked_to_bank,to_jsonb(cd)->>'stock_marked_bank_id',md5((to_jsonb(cd)-'stock_marked_bank_id')::text) FROM contract_details cd ORDER BY contract_detail_id")
        assert len(before) == len(after)
        assert all(b[:3] == a[:3] and b[4] == a[4] for b,a in zip(before,after)), 'Unrelated contract details changed'
        assert all(b[3] is None or b[3] == a[3] for b,a in zip(before,after)), 'Existing ID overwritten'
        assert banks_before == rows(q,'SELECT to_jsonb(b) FROM bank_masters b ORDER BY id'), 'Bank Master changed'
        assert procedures_before == rows(q,"SELECT oid::regprocedure::text,md5(pg_get_functiondef(oid)) FROM pg_proc WHERE proname='sp_branch_wise_ageing_test' ORDER BY oid"), 'Procedure changed'
        q.execute('ALTER TABLE contract_details VALIDATE CONSTRAINT fk_contract_details_stock_bank')
        execute_script(q, 'stock-bank-menu.sql')
        menu = rows(q,"SELECT menu_id,parent_id,menu_code,url_path FROM menus WHERE menu_code='CREDIT_TRANSACTION_STOCK_BANK_MARKING' OR url_path='/credit/transaction/stock-bank-marking'")
        assert len(menu)==1
        assert menu[0][1]==7
        grants = rows(q,"SELECT r.role_code,rp.menu_id,rp.can_view FROM role_permissions rp JOIN roles r USING(role_id) WHERE r.role_code IN ('CREDIT','SUPER_ADMIN') AND rp.menu_id IN (1,7,"+str(menu[0][0])+") ORDER BY r.role_code,rp.menu_id")
        assert len(grants)==6 and all(row[2] for row in grants)
        allowed_menus={1,7,menu[0][0]}
        assert [r for r in menus_before if r[0]['menu_id'] not in allowed_menus] == rows(q, 'SELECT to_jsonb(m) FROM menus m WHERE menu_id NOT IN (1,7,'+str(menu[0][0])+') ORDER BY menu_id'), 'Unrelated menu changed'
        allowed_roles={1,11}
        new_permissions = rows(q,'SELECT to_jsonb(rp) FROM role_permissions rp ORDER BY permission_id')
        def unrelated(data):
            return [r for r in data if not(r[0]['menu_id'] in allowed_menus and r[0]['role_id'] in allowed_roles)]
        assert unrelated(permissions_before)==unrelated(new_permissions), 'Unrelated permissions changed'
        report={'status':'COMMITTED','database':identity,'snapshot':snapshot.name,'snapshotSHA256':hashlib.sha256(snapshot.read_bytes()).hexdigest(),'beforeDetailRows':len(before),'afterDetailRows':len(after),'nameCountsBefore':name_counts,'migratedRows':sum(b[3] is None and a[3] is not None for b,a in zip(before,after)),'preservedExistingIDs':sum(b[3] is not None for b in before),'unmatched':0,'orphanIDs':0,'duplicateDetails':0,'originalTextPreserved':True,'unrelatedContractFieldsPreserved':True,'bankMasterUnchanged':True,'termLoanID10Unchanged':True,'procedureDefinitionsUnchanged':True,'foreignKeyValidated':True,'menu':menu,'permissions':grants,'validationResults':validations,'errors':[]}
    c.commit()
(OUT/'stock-bank-migration-completed.json').write_text(json.dumps(report,indent=2,default=str))
print(json.dumps({k:v for k,v in report.items() if k!='validationResults'},indent=2,default=str))
