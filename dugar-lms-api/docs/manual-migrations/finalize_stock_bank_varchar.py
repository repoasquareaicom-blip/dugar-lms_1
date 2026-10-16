"""Authorized local-only VARCHAR ID finalization; locks, backups, validates, then retires source."""
import csv,hashlib,json,sys
from datetime import datetime,timezone
from local_stock_bank_db import connect,OUT,ROOT
rehearse='--rehearse' in sys.argv
mode='rehearsal' if rehearse else 'final'
stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
def run(q,name):
    q.execute('\n'.join(line for line in (OUT/name).read_text().splitlines() if line.strip() not in ('BEGIN;','COMMIT;')))
    while q.nextset():pass

def validate(q):
    q.execute((OUT/'stock-bank-varchar-validation.sql').read_text());results=[]
    while True:
        results.append({'columns':[x.name for x in q.description],'rows':q.fetchall()})
        if not q.nextset():break
    assert results[1]['rows']==[(6106,75)], 'Expected 6106 details and 75 named assignments'
    assert all(not results[i]['rows'] for i in (2,3,4,6)), 'Unmatched, lost, overwritten source IDs or duplicates'
    assert results[5]['rows']==[(51,51)], 'Original Union Bank contracts not all on ID 1'
    assert results[8]['rows']==[('ck_contract_details_stock_bank_numeric_string',True)]
    return results

for directory in (ROOT/'src/main/java',ROOT.parent/'dugar-lms-frontend/src'):
    for path in directory.rglob('*'):
        if path.is_file() and path.suffix in ('.java','.js','.jsx','.ts','.tsx'):
            source=path.read_text(encoding='utf-8')
            assert 'stock_marked_bank_id' not in source and 'stockMarkedBankId' not in source, str(path)
report={'mode':mode,'committed':False,'connection':'127.0.0.1:5434/dugar_lms','unmatched':[],'errors':[]}
with connect() as c:
    try:
        with c.cursor() as q:
            q.execute("SET LOCAL lock_timeout='5s'")
            q.execute('SELECT current_database(),inet_server_addr()::text,inet_server_port()');report['identity']=q.fetchone()
            assert report['identity'][0]=='dugar_lms' and report['identity'][1]=='127.0.0.1/32'
            q.execute('LOCK TABLE contract_details IN ACCESS EXCLUSIVE MODE NOWAIT')
            q.execute('LOCK TABLE bank_masters IN SHARE MODE NOWAIT')
            report['exclusiveWriteExclusion']=True
            q.execute('SELECT COUNT(*) FROM contract_details');assert q.fetchone()==(6106,)
            q.execute((OUT/'stock-bank-varchar-resolver.sql').read_text())
            resolution=q.fetchall()
            # Resolution tuple: detail ID, original text, trimmed text, optional source ID, target master ID.
            report['unmatched']=[r for r in resolution if (r[2] is not None or r[3] is not None) and r[4] is None]
            report['conflictingSourceIDs']=[r for r in resolution if r[3] is not None and r[3]!=str(r[4])]
            (OUT/('stock-bank-varchar-'+mode+'-preflight.json')).write_text(json.dumps(report,indent=2,default=str))
            assert not report['unmatched'] and not report['conflictingSourceIDs'], 'Unmatched or conflicting values; no migration'
            assert sum(r[4] is not None for r in resolution)==75
            report['verifiedAssignments']=75
            report['valuesRewritten']=sum(r[1]!=(str(r[4]) if r[4] is not None else None) for r in resolution)
            q.execute("SELECT stock_marked_to_bank,COUNT(*) FROM contract_details WHERE NULLIF(BTRIM(stock_marked_to_bank),'') IS NOT NULL GROUP BY stock_marked_to_bank ORDER BY stock_marked_to_bank")
            report['beforeValues']=q.fetchall()
            q.execute("SELECT contract_detail_id,md5((to_jsonb(cd)-'stock_marked_to_bank'-'stock_marked_bank_id')::text) FROM contract_details cd ORDER BY contract_detail_id");other_fields=q.fetchall()
            q.execute('SELECT to_jsonb(b) FROM bank_masters b ORDER BY id');banks=q.fetchall()
            q.execute("SELECT oid::regprocedure::text,md5(pg_get_functiondef(oid)) FROM pg_proc WHERE proname='sp_branch_wise_ageing_test' ORDER BY oid");procedures=q.fetchall()
            q.execute("SELECT p.oid::regprocedure::text FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.prokind IN('f','p') AND pg_get_functiondef(p.oid) ILIKE '%stock_marked_bank_id%'");assert not q.fetchall()
            q.execute("SELECT viewname FROM pg_views WHERE schemaname='public' AND definition ILIKE '%stock_marked_bank_id%'");assert not q.fetchall()
            q.execute("SELECT column_name,data_type,column_default,is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='contract_details' ORDER BY ordinal_position");columns=q.fetchall()
            backup=OUT/('contract-details-varchar-'+mode+'-'+stamp+'.csv')
            with backup.open('xb') as f:
                with q.copy('COPY public.contract_details TO STDOUT WITH (FORMAT CSV,HEADER TRUE)') as copy:
                    for chunk in copy:f.write(chunk)
            with backup.open(newline='',encoding='utf-8') as f:
                reader=csv.DictReader(f);assert {'stock_marked_to_bank','stock_marked_bank_id'}.issubset(reader.fieldnames)
                assert sum(1 for row in reader)==6106
            report.update(backup=backup.name,backupRows=6106,backupSHA256=hashlib.sha256(backup.read_bytes()).hexdigest())
            (OUT/('stock-bank-varchar-'+mode+'-before-'+stamp+'.json')).write_text(json.dumps({'columns':columns,'bankMaster':banks,'procedures':procedures},indent=2,default=str))
            run(q,'stock-bank-varchar-id.sql')
            report['beforeRetirementValidation']=validate(q)
            run(q,'stock-bank-varchar-retire-source.sql')
            report['finalValidation']=validate(q)
            assert report['finalValidation'][0]['rows']==[('stock_marked_to_bank','character varying','YES')]
            q.execute("SELECT contract_detail_id,md5((to_jsonb(cd)-'stock_marked_to_bank')::text) FROM contract_details cd ORDER BY contract_detail_id");assert q.fetchall()==other_fields
            q.execute('SELECT to_jsonb(b) FROM bank_masters b ORDER BY id');assert q.fetchall()==banks
            q.execute("SELECT oid::regprocedure::text,md5(pg_get_functiondef(oid)) FROM pg_proc WHERE proname='sp_branch_wise_ageing_test' ORDER BY oid");assert q.fetchall()==procedures
            q.execute("SELECT pid FROM pg_locks WHERE relation='contract_details'::regclass AND pid<>pg_backend_pid() AND mode IN ('RowExclusiveLock','ShareRowExclusiveLock','ExclusiveLock','AccessExclusiveLock')");assert not q.fetchall(), 'Other client attempted a concurrent write; rollback'
            report.update(unrelatedFieldsPreserved=True,bankMasterAndTermLoanID10Unchanged=True,demandListProceduresUnchanged=True,sourceRetired=True,datatypeChanged=False,orphanIDs=0,lostMappings=0,original51OnBankID1=51)
        if rehearse:c.rollback()
        else:c.commit();report['committed']=True
    except Exception:
        c.rollback();raise
report['completedAtUTC']=datetime.now(timezone.utc).isoformat()
(OUT/('stock-bank-varchar-'+mode+'-results.json')).write_text(json.dumps(report,indent=2,default=str))
print(json.dumps({k:v for k,v in report.items() if k not in ('beforeRetirementValidation','finalValidation')},indent=2,default=str))
