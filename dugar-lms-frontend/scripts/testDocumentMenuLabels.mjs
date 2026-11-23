import assert from 'node:assert/strict';
import test from 'node:test';
import { documentMenuLabel } from '../src/utils/documentMenuLabels.js';
import { resolveMenuPath } from '../src/utils/menuRouteMap.js';

test('cached and migrated document menus retain their existing routes', () => {
  for (const [oldName, name, code, path] of [
    ['Word Template Master', 'Document Template Master', 'ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER', '/administration/masters/word-template-master'],
    ['Generate Word Document', 'Generate Document', 'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT', '/operations/transactions/generate-word-document'],
  ]) {
    for (const label of [oldName, name]) {
      assert.equal(documentMenuLabel({ menuName: label, menuCode: code }), name);
      assert.equal(resolveMenuPath({ menuName: label, menuCode: code, urlPath: '#' }), path);
      assert.equal(resolveMenuPath({ menuName: label, urlPath: '#' }), path);
    }
    assert.equal(documentMenuLabel({ menu_name: oldName }), name);
    assert.equal(documentMenuLabel({ menuCode: code }), name);
  }
});

test('unrelated menu labels and routes are preserved', () => {
  const menu = { menuName: 'Word Processing Audit', urlPath: '/audit/word' };
  assert.equal(documentMenuLabel(menu), menu.menuName);
  assert.equal(resolveMenuPath(menu), menu.urlPath);
});

test('retired AI Settings is hidden from cached menu trees while billing remains visible',async()=>{
  const {readFile}=await import('node:fs/promises');
  const source=await readFile(new URL('../src/utils/menuNormalizer.js',import.meta.url),'utf8');
  const compiled=source.replace(/^import .*;$/gm,'').replace('export function normalizeMenuTree','function normalizeMenuTree');
  const normalize=new Function('resolveMenuPath','documentMenuLabel',`${compiled}\nreturn normalizeMenuTree;`)(resolveMenuPath,documentMenuLabel);
  const result=normalize([{menuName:'Administration',menuCode:'ADMINISTRATION',children:[{menuName:'Masters',children:[{menuName:'Document AI Settings',menuCode:'ADMINISTRATION_MASTERS_DOCUMENT_AI_SETTINGS',isActive:true,isVisible:true,urlPath:'/administration/masters/document-ai-settings'}]},{menuName:'Reports',children:[{menuName:'AI API Usage & Billing',menuCode:'ADMINISTRATION_REPORTS_AI_USAGE_BILLING',urlPath:'/administration/reports/ai-usage-billing'}]}]}]);
  const visible=(items)=>items.flatMap(menu=>[menu.menuCode,...visible(menu.subMenus||[])]);
  assert.ok(!visible(result).includes('ADMINISTRATION_MASTERS_DOCUMENT_AI_SETTINGS'));assert.ok(visible(result).includes('ADMINISTRATION_REPORTS_AI_USAGE_BILLING'));
});
