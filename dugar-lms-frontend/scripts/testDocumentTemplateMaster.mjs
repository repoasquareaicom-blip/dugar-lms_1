import { readFile } from 'node:fs/promises';
import test from 'node:test';
import assert from 'node:assert/strict';
import { transformWithOxc } from 'vite';

const source = await readFile(new URL('../src/pages/word-documents/WordTemplateMaster.jsx', import.meta.url), 'utf8');
const { code } = await transformWithOxc(source, 'WordTemplateMaster.jsx');
const compiled = code.replace(/^import .*;$/gm, '').replace('export default function', 'function');
const nodes = (tree) => Array.isArray(tree) ? tree.flatMap(nodes) : tree && typeof tree === 'object' ? [tree, ...nodes(tree.props?.children)] : [];
const text = (tree) => Array.isArray(tree) ? tree.map(text).join('') : tree == null ? '' : typeof tree === 'object' ? text(tree.props?.children) : String(tree);

async function harness() {
  let cursor = 0, tree, dirty = true; const slots = [], pending = [], generated = [], requests = [];
  const useState = (initial) => { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], (value) => { const next = typeof value === 'function' ? value(slots[i]) : value; if (!Object.is(next, slots[i])) { slots[i] = next; dirty = true; } }]; };
  const useRef = (initial) => { const i = cursor++; if (!(i in slots)) slots[i] = { current: initial }; return slots[i]; };
  const useEffect = (effect, deps) => { const i = cursor++; const previous = slots[i]; if (!previous || deps.some((d, n) => !Object.is(previous.deps[n], d))) { previous?.cleanup?.(); const entry = { deps }; slots[i] = entry; pending.push(() => { entry.cleanup = effect(); }); } };
  const memo = (fn, deps) => { const i = cursor++; if (!slots[i] || deps.some((d,n) => !Object.is(slots[i].deps[n],d))) slots[i] = { deps, value: fn() }; return slots[i].value; };
  let aliasRows = [{id:1,alias:"Customer"}];
  const jsx = (type, props) => ({ type, props });
  const deps = {
    useState, useRef, useEffect, useMemo: (fn,d)=>memo(fn,d), useCallback: (fn,d)=>memo(()=>fn,d), _jsx:jsx, _jsxs:jsx,
    CheckCircle2:'icon',Copy:'icon',Download:'icon',Edit3:'icon',FilePlus2:'icon',Loader2:'icon',RefreshCw:'icon',Save:'icon',Search:'icon',Trash2:'icon',Upload:'icon',X:'icon',
    fetchWordTemplates:async()=>[{id:1,templateName:'Legacy',languageCode:'hi',isActive:true,hasFile:true,displayOrder:7}],
    fetchWordPlaceholders:async()=>[{category:'Borrower',fieldName:'Full Name',placeholderCode:'{{BORROWER_FULL_NAME}}'},{category:'Contract',fieldName:'Number',placeholderCode:'{{CONTRACT_CONTRACT_NUMBER}}'}],
    apiClient:{get:async(url,config)=>{requests.push({url,config});return {data:url.endsWith('/fields')?[{canonicalCode:'{{BORROWER_FULL_NAME}}',aliases:aliasRows}]:{contractNumber:'C-41',values:{'{{BORROWER_FULL_NAME}}':'Ramesh Kumar','{{CONTRACT_CONTRACT_NUMBER}}':'C-41'}} };}, post:async(url,payload)=>{requests.push({url,payload});if(payload.alias==='duplicate')throw new Error('Alias is already in use');aliasRows=[{id:payload.id??2,alias:payload.alias}];},delete:async(url)=>{requests.push({url});aliasRows=[];}},
    replaceWordTemplateFile:async(id,file)=>generated.push({kind:'replace',id,file}),setWordTemplateActive:async(id,isActive)=>generated.push({kind:'active',id,isActive}),
  };
  const Page = new Function(...Object.keys(deps), `${compiled}\nreturn WordTemplateMaster;`)(...Object.values(deps));
  const render = () => { cursor = 0; dirty = false; tree = Page(); };
  const flush = async () => { for (let i = 0; i < 15; i++) { if (dirty) render(); while (pending.length) pending.shift()(); await new Promise((r) => setImmediate(r)); if (!dirty && !pending.length) break; } return tree; };
  await flush();
  const all = () => nodes(tree);
  const button = (name) => all().find((n) => n.type === 'button' && text(n).trim() === name);
  return { flush, all, button, generated, requests, text: () => text(tree) };
}


test('field search matches partial case-insensitive category, field, placeholder, alias and previewed values',async()=>{
  const h=await harness();const input=(label)=>h.all().find(n=>n.props?.['aria-label']===label);
  const search=async(value)=>{input('Search available fields').props.onChange({target:{value}});await h.flush();};
  for(const query of ['BORROW','ull na','BORROWER_FULL','usto']) {await search(query);assert.ok(h.text().includes('{{BORROWER_FULL_NAME}}'));assert.ok(!h.text().includes('{{CONTRACT_CONTRACT_NUMBER}}'));}
  input('Contract Number').props.onChange({target:{value:'C-41'}});await h.flush();
  await h.all().find(n=>n.type==='form').props.onSubmit({preventDefault(){}});await h.flush();
  assert.ok(h.text().includes('Ramesh Kumar'));
  await search('MESH KU');assert.ok(h.text().includes('Ramesh Kumar'));assert.ok(!h.text().includes('{{CONTRACT_CONTRACT_NUMBER}}'));
  await search('C-4');assert.ok(h.text().includes('{{CONTRACT_CONTRACT_NUMBER}}'));
  await search('');assert.ok(h.text().includes('Ramesh Kumar'));assert.ok(h.text().includes('C-41'));
  assert.equal(h.requests.filter(r=>r.url.endsWith('/contract-fields')).length,1);
  assert.ok(h.requests.every(r=>['/document-automation/fields','/document-automation/contract-fields'].includes(r.url)));
});
test('master permits only upload/replace and activation of registered definitions',async()=>{
  const h=await harness();
  for(const label of ['Create Template','Edit metadata','Delete','Template Groups','Display Order'])assert.ok(!h.text().includes(label));
  h.button('Replace DOCX').props.onClick();await h.flush();
  const file=h.all().find(n=>n.type==='input'&&n.props.type==='file');assert.equal(file.props.required,true);
  assert.ok(!h.all().some(n=>n.type==='select'));
  const replacement={name:'replacement.docx'};file.props.onChange({target:{files:[replacement]}});await h.flush();
  await h.all().filter(n=>n.type==='form').at(-1).props.onSubmit({preventDefault(){}});await h.flush();
  assert.deepEqual(h.generated[0],{kind:'replace',id:1,file:replacement});
  await h.button('Deactivate').props.onClick();await h.flush();
  assert.deepEqual(h.generated[1],{kind:'active',id:1,isActive:false});
});

test('inline alias save supports creation, edit, clearing and errors without a conversion popup',async()=>{
  const h=await harness(); const aliasInput=()=>h.all().find(n=>n.type==='input'&&n.props['aria-label']?.startsWith('Alias for {{BORROWER_FULL_NAME}}'));
  const save=async(value)=>{aliasInput().props.onChange({target:{value}});await h.flush();const row=h.all().find(n=>n.type==='tr'&&nodes(n).includes(aliasInput()));await nodes(row).find(n=>n.type==='button'&&text(n).endsWith('Save')).props.onClick();await h.flush();};
  assert.ok(!h.text().includes('Aliases &'));assert.ok(!h.text().includes('Conversion'));
  await save('{{add1}}');assert.equal(h.requests.find(r=>r.payload)?.payload.alias,'add1');assert.equal(aliasInput().props.value,'{{add1}}');
  await save('duplicate');assert.ok(h.text().includes('Alias is already in use'));assert.equal(aliasInput().props.value,'duplicate');
  await save('');assert.ok(h.requests.some(r=>r.url==='/document-automation/aliases/1'));assert.equal(aliasInput().props.value,'');
  await save('newAlias');assert.equal(h.requests.filter(r=>r.payload).at(-1).payload.id,null);assert.equal(aliasInput().props.value,'{{newAlias}}');
});
