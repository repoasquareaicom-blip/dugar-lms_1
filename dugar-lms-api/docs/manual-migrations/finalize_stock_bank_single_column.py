"""Authorized final cutover on the confirmed local database; atomic conversion and retirement."""
import csv, hashlib, json
from datetime import datetime, timezone
from local_stock_bank_db import connect, OUT, ROOT

def script(q, filename):
    q.execute('\n'.join(line for line in (OUT/filename).read_text().splitlines() if line.strip() not in ('BEGIN;','COMMIT;')))
    while q.nextset(): pass

def validate(q):
    q.execute((OUT/'stock-bank-single-column-validation.sql').read_text())
    results=[]
    while True:
        results.append({'columns':[x.name for x in q.description], 'rows':q.fetchall()})
        if not q.nextset(): break
    assert results[1]['rows'][0][:2]==(6106,6106), 'Current/backup count mismatch'
    assert all(not results[i]['rows'] for i in (2,3,4,6)), 'Invalid/lost/overwritten/duplicate assignments'
    assert results[5]['rows']==[(51,51)], 'Original 51 contracts no longer on Bank ID 1'
    assert results[7]['rows'][0][1] is True, 'FK not validated'
    return results

# Runtime source dependency audit before any database alteration.
for directory in (ROOT/'src/main/java', ROOT.parent/'dugar-lms-frontend/src'):
    for path in directory.rglob('*'):
        if path.is_file() and path.suffix in ('.java','.js','.jsx','.ts','.tsx'):
            source=path.read_text(encoding='utf-8')
            assert 'stock_marked_bank_id' not in source and 'stockMarkedBankId' not in source, ('Old dependency',str(path))
stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
report={'status':'PREPARING','localConnection':'127.0.0.1:5434/dugar_lms','productionTouched':False,'awsDeployment':False}
with connect() as c:
    with c.cursor() as q:
        q.execute("SET LOCAL lock_timeout='5s'")
        q.execute('SELECT current_database(),inet_server_addr()::text,inet_server_port()')
        report['serverIdentity']=q.fetchone()
        assert report['serverIdentity'][0]=='dugar_lms' and report['serverIdentity'][1]=='127.0.0.1/32'
        q.execute("SELECT pid,application_name,state FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND (state<>'idle' OR xact_start IS NOT NULL OR application_name='PostgreSQL JDBC Driver')")
        assert not q.fetchall(), 'Application/active transactions still connected: stopping'
        # NOWAIT proves there is no existing lock holder/writer; retain exclusion through commit.
        q.execute('LOCK TABLE contract_details IN ACCESS EXCLUSIVE MODE NOWAIT')
        q.execute('LOCK TABLE bank_masters IN SHARE MODE NOWAIT')
        report['writersStoppedAndExclusiveLockHeld']=True
        q.execute('SELECT COUNT(*) FROM contract_details');assert q.fetchone()==(6106,)
        q.execute('SELECT stock_marked_to_bank,stock_marked_bank_id,COUNT(*) FROM contract_details WHERE stock_marked_to_bank IS NOT NULL OR stock_marked_bank_id IS NOT NULL GROUP BY stock_marked_to_bank,stock_marked_bank_id ORDER BY stock_marked_to_bank,stock_marked_bank_id')
        report['beforeMappings']=q.fetchall()
        q.execute('SELECT COUNT(stock_marked_bank_id) FROM contract_details');report['preExistingSourceIDs']=q.fetchone()[0]
        q.execute("SELECT oid::regprocedure::text,md5(pg_get_functiondef(oid)) FROM pg_proc WHERE proname='sp_branch_wise_ageing_test' ORDER BY oid")
        procedures=q.fetchall()
        q.execute('SELECT to_jsonb(b) FROM bank_masters b ORDER BY id');banks=q.fetchall()
        q.execute("SELECT p.oid::regprocedure::text FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.prokind IN('f','p') AND (pg_get_functiondef(p.oid) ILIKE '%stock_marked_bank_id%')")
        assert not q.fetchall(), 'Stored function depends on old source'
        q.execute("SELECT viewname FROM pg_views WHERE schemaname='public' AND definition ILIKE '%stock_marked_bank_id%'")
        assert not q.fetchall(), 'View depends on old source'
        q.execute("SELECT column_name,data_type,column_default,is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='contract_details' ORDER BY ordinal_position")
        columns=q.fetchall()
        backup=OUT/('contract-details-final-cutover-'+stamp+'.csv')
        with backup.open('xb') as f:
            with q.copy('COPY public.contract_details TO STDOUT WITH (FORMAT CSV,HEADER TRUE)') as copy:
                for chunk in copy:f.write(chunk)
        with backup.open(newline='',encoding='utf-8') as f:
            reader=csv.DictReader(f)
            assert {'stock_marked_to_bank','stock_marked_bank_id'}.issubset(reader.fieldnames)
            count=sum(1 for row in reader)
        assert count==6106
        report.update(backupFile=backup.name,backupRows=count,backupSHA256=hashlib.sha256(backup.read_bytes()).hexdigest())
        (OUT/('contract-details-final-schema-'+stamp+'.json')).write_text(json.dumps({'columns':columns,'banks':banks,'procedureHashes':procedures},indent=2,default=str))
        script(q,'stock-bank-single-column-convert.sql')
        report['conversionValidation']=validate(q)
        report['assignedAfterConversion']=report['conversionValidation'][1]['rows'][0][2]
        report['sourceGapsUniquelyResolved']=report['assignedAfterConversion']-report['preExistingSourceIDs']
        script(q,'stock-bank-single-column-retire-source.sql')
        report['finalValidation']=validate(q)
        assert report['finalValidation'][0]['rows']==[('stock_marked_to_bank','bigint')], 'Final bank columns are incorrect'
        q.execute("SELECT COUNT(*) FROM contract_details cd JOIN stock_bank_contract_details_backup_v2 old USING(contract_detail_id) WHERE (to_jsonb(cd)-'stock_marked_to_bank') IS DISTINCT FROM (to_jsonb(old)-'stock_marked_to_bank'-'stock_marked_bank_id')")
        assert q.fetchone()==(0,), 'Unrelated contract fields changed'
        q.execute('SELECT to_jsonb(b) FROM bank_masters b ORDER BY id');assert q.fetchall()==banks
        q.execute("SELECT oid::regprocedure::text,md5(pg_get_functiondef(oid)) FROM pg_proc WHERE proname='sp_branch_wise_ageing_test' ORDER BY oid");assert q.fetchall()==procedures
        q.execute("SELECT l.pid FROM pg_locks l WHERE l.relation='contract_details'::regclass AND l.pid<>pg_backend_pid() AND l.mode IN ('RowExclusiveLock','ShareRowExclusiveLock','ExclusiveLock','AccessExclusiveLock')")
        assert not q.fetchall(), 'Another process attempted a write during maintenance: rollback'
        report.update(status='COMMITTED',orphanIDs=0,lostMappings=0,overwrittenExistingIDs=0,duplicateDetails=0,originalUnionBankOnID1=51,foreignKeyValidated=True,oldSourceColumnRemoved=True,unrelatedFieldsPreserved=True,bankMasterUnchanged=True,demandListProceduresUnchanged=True,errors=[])
    c.commit()
report['completedAtUTC']=datetime.now(timezone.utc).isoformat()
(OUT/'stock-bank-single-column-final-results.json').write_text(json.dumps(report,indent=2,default=str))
print(json.dumps({k:v for k,v in report.items() if k not in ('conversionValidation','finalValidation')},indent=2,default=str))
