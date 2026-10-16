"""Rehearse conversion and retirement locally. NEVER commits schema or test data."""
import json
from local_stock_bank_db import connect, OUT

def run(q, filename):
    source = (OUT / filename).read_text()
    q.execute('\n'.join(line for line in source.splitlines() if line.strip() not in ('BEGIN;', 'COMMIT;')))
    while q.nextset(): pass

result = {'committed': False}
with connect() as c:
    try:
        with c.cursor() as q:
            run(q, 'stock-bank-single-column-convert.sql')
            q.execute((OUT / 'stock-bank-single-column-validation.sql').read_text())
            checks=[]
            while True:
                checks.append({'columns':[x.name for x in q.description],'rows':q.fetchall()})
                if not q.nextset(): break
            assert checks[1]['rows'][0][0] == checks[1]['rows'][0][1]
            assert all(not checks[i]['rows'] for i in (2,3,4,6))
            assert checks[5]['rows']==[(51,51)]
            assert checks[7]['rows'][0][1] is True
            result['validation']=checks
            q.execute("SELECT COUNT(*) FROM contract_details cd JOIN stock_bank_contract_details_backup_v2 b USING(contract_detail_id) WHERE b.stock_marked_bank_id IS NULL AND cd.stock_marked_to_bank IS NOT NULL")
            result['uniquelyResolvedSourceGaps']=q.fetchone()[0]
            run(q,'stock-bank-single-column-retire-source.sql')
            # Repeating conversion AFTER source retirement must never recreate it or overwrite new IDs.
            q.execute('SELECT contract_detail_id,stock_marked_to_bank FROM contract_details ORDER BY contract_detail_id'); before=q.fetchall()
            run(q,'stock-bank-single-column-convert.sql')
            q.execute('SELECT contract_detail_id,stock_marked_to_bank FROM contract_details ORDER BY contract_detail_id');assert before==q.fetchall()
            q.execute("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='contract_details' AND column_name='stock_marked_bank_id'")
            assert q.fetchone()==(0,)
            result['sourceRetirementAndRerun']='passed'
    finally: c.rollback()
# Verify fail-closed behavior for a conflicting legacy name, with a complete rollback.
with connect() as c:
    try:
        with c.cursor() as q:
            q.execute("UPDATE contract_details SET stock_marked_to_bank='__UNMAPPED_BANK_REHEARSAL__' WHERE contract_detail_id=(SELECT MIN(contract_detail_id) FROM contract_details WHERE stock_marked_bank_id=1)")
            try:
                run(q,'stock-bank-single-column-convert.sql')
            except Exception as exception:
                assert 'Unmatched, ambiguous or conflicting legacy bank names' in str(exception)
                result['conflictingNameRollback']='passed'
            else: raise AssertionError('Unsafe conversion accepted a conflicting legacy name')
    finally: c.rollback()
with connect() as c:
    c.read_only=True
    with c.cursor() as q:
        q.execute("SELECT column_name,data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='contract_details' AND column_name IN ('stock_marked_to_bank','stock_marked_bank_id') ORDER BY column_name")
        result['unchangedLiveSchema']=q.fetchall()
        assert result['unchangedLiveSchema']==[('stock_marked_bank_id','bigint'),('stock_marked_to_bank','character varying')]
        q.execute("SELECT to_regclass('public.stock_bank_contract_details_backup_v2')")
        assert q.fetchone()==(None,)
        q.execute("SELECT COUNT(*) FROM contract_details WHERE stock_marked_to_bank='__UNMAPPED_BANK_REHEARSAL__' OR updated_by='stock-bank-integration-test'")
        assert q.fetchone()==(0,)
        result['temporaryDDLAndDataRolledBack']=True
(OUT/'stock-bank-single-column-rehearsal.json').write_text(json.dumps(result,indent=2,default=str))
print(json.dumps(result,indent=2,default=str))
