import { readFile } from 'node:fs/promises';
import test from 'node:test';
import assert from 'node:assert/strict';
import { transformWithOxc } from 'vite';

const source = await readFile(new URL('../src/pages/word-documents/GenerateWordDocument.jsx', import.meta.url), 'utf8');
const { code } = await transformWithOxc(source, 'GenerateWordDocument.jsx');
const compiled = code.replace(/^import .*;$/gm, '').replace('export default function', 'function');
const nodes = (tree) => Array.isArray(tree) ? tree.flatMap(nodes) : tree && typeof tree === 'object' ? [tree, ...nodes(tree.props?.children)] : [];
const text = (tree) => Array.isArray(tree) ? tree.map(text).join('') : tree == null ? '' : typeof tree === 'object' ? text(tree.props?.children) : String(tree);

const gridCode = (await transformWithOxc(await readFile(new URL('../src/pages/word-documents/ReminderLetterGrid.jsx', import.meta.url), 'utf8'), 'ReminderLetterGrid.jsx')).code.replace(/^import .*;$/gm, '').replace('export default function', 'function');

async function harness({ hindiAvailable = true, reminderGrid = false, snapshotRequest } = {}) {
  let cursor = 0, tree, dirty = true; const slots = [], pending = [], generated = [], requests = [];
  const useState = (initial) => { const i = cursor++; if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial; return [slots[i], (value) => { const next = typeof value === 'function' ? value(slots[i]) : value; if (!Object.is(next, slots[i])) { slots[i] = next; dirty = true; } }]; };
  const useRef = (initial) => { const i = cursor++; if (!(i in slots)) slots[i] = { current: initial }; return slots[i]; };
  const useEffect = (effect, deps) => { const i = cursor++; const previous = slots[i]; if (!previous || deps.some((d, n) => !Object.is(previous.deps[n], d))) { previous?.cleanup?.(); const entry = { deps }; slots[i] = entry; pending.push(() => { entry.cleanup = effect(); }); } };
  const memo = (fn, deps) => { const i = cursor++; if (!slots[i] || deps.some((d,n) => !Object.is(slots[i].deps[n],d))) slots[i] = { deps, value: fn() }; return slots[i].value; };
  const events = new EventTarget(); const catalogueRequests = []; const extra = [];
  const saved = new Map(); events.sessionStorage = { getItem: (key) => saved.get(key), setItem: (key, value) => saved.set(key, value), removeItem: (key) => saved.delete(key) };
  const timers = new Map(); let timerId = 0;
  events.setTimeout = (callback) => { timers.set(++timerId, callback); return timerId; }; events.clearTimeout = (id) => timers.delete(id); events.confirm = () => true;
  const jsx = (type, props) => ({ type, props });
  const gridQueries = []; const gridProps = { asOnDate: '2026-10-17', pendingOnly: true, documentSelection: { onQueryChange: (p) => gridQueries.push(p), onResults() {}, isSelected: () => false, onToggle() {}, renderActions() {} } };
  const deps = {
    useState, useRef, useEffect, useMemo: (fn, deps) => memo(fn, deps), useCallback: (fn, deps) => memo(() => fn, deps),
    window: events, document: { visibilityState: 'visible', addEventListener() {}, removeEventListener() {} }, DOCUMENT_TEMPLATES_CHANGED: 'document-templates-changed',
    ServerDataTable: 'ServerDataTable', ReminderLetterGrid: 'ReminderLetterGrid', ContractGrid: 'ContractGrid', downloadWordTemplate: async () => {}, getApiBaseUrl: () => '/api', getStoredAuthToken: () => 'fake', URL: { createObjectURL: () => 'blob:test', revokeObjectURL() {} }, Download: 'Download', FileText: 'FileText', Loader2: 'Loader2', RefreshCw: 'RefreshCw', Search: 'Search', _jsx: jsx, _jsxs: jsx, _Fragment: 'Fragment',
    fetchWordTemplates: async (params) => { catalogueRequests.push(params); return [{ id: 11, templateName: 'English reminder', languageCode: 'en', isActive: true }, ...(hindiAvailable ? [{ id: 12, templateName: 'Hindi reminder', languageCode: 'hi', isActive: true }] : []), ...extra]; },
    searchWordContracts: async () => [{ contractId: 41, contractNumber: 'C-41', borrowerName: 'Test' }],
    validateWordTemplate: async () => ({ recognized: ['{{name}}'], unknown: [] }),
    generateWordDocument: async (r) => generated.push(r),
    api: { get: async (url, config) => { requests.push({ url, config }); return { data: { id: 'job-1', status: 'COMPLETED', total_count: 1, success_count: 1, failure_count: 0, errors: [] } }; }, post: async (url, payload) => { requests.push({ url, payload }); if (url.endsWith('/reminder/snapshots')) return snapshotRequest ? snapshotRequest(payload) : { data: { id: 'snapshot-1' } }; return { data: { id: 'job-1', status: 'QUEUED', total_count: payload.allMatching ? 501 : payload.contractIds.length, success_count: 0, failure_count: 0 } }; } },
  };
  const Page = new Function(...Object.keys(deps), `${reminderGrid ? gridCode : compiled}\nreturn ${reminderGrid ? 'ReminderLetterGrid' : 'GenerateWordDocument'};`)(...Object.values(deps));
  const render = () => { cursor = 0; dirty = false; tree = reminderGrid ? Page(gridProps) : Page(); };
  const flush = async () => { for (let i = 0; i < 15; i++) { if (dirty) render(); while (pending.length) pending.shift()(); await new Promise((r) => setImmediate(r)); if (!dirty && !pending.length) break; } return tree; };
  await flush();
  const all = () => nodes(tree);
  const button = (name) => all().find((n) => n.type === 'button' && text(n).trim() === name);
  const language = () => all().find((n) => n.type === 'select' && nodes(n).some((child) => child.type === 'option' && child.props.value === 'hi'));
  return { gridQueries, setGridProps: (props) => { Object.assign(gridProps, props); dirty = true; }, api: deps.api, timers, runTimer: () => { const [id, callback] = timers.entries().next().value; timers.delete(id); return callback(); }, flush, all, button, language, generated, requests, events, extra, catalogueRequests, grid: () => all().find((n) => ['ContractGrid', 'ReminderLetterGrid'].includes(n.type)).props.documentSelection, selectDocument: (id) => all().find((n) => n.props?.['aria-label'] === 'Document').props.onChange({ target: { value: String(id) } }), text: () => text(tree) };
}

const params = { keyword: 'Ramesh', filters: { branch: 'A1' }, sortColumn: 'contractDate', sortDirection: 'desc' };
const ready = async (h, total = 501) => { h.grid().onQueryChange(params); h.grid().onResults(params, total); await h.flush(); };

test('catalogue labels include name and language and refresh', async () => {
  const h = await harness({ hindiAvailable: false });
  assert.ok(h.text().includes('English reminder (English)'));
  h.extra.push({ id: 42, templateName: 'REMINDER LETTER', languageCode: 'hi', isActive: true }, { id: 43, templateName: 'Inactive', languageCode: 'hi', isActive: false }, { id: 44, templateName: 'Missing DOCX', languageCode: 'hi', isActive: true, hasFile: false });
  h.events.dispatchEvent(new Event('document-templates-changed')); await h.flush();
  assert.ok(!h.text().includes('Missing DOCX')); assert.ok(h.text().includes('REMINDER LETTER (Hindi)')); assert.ok(!h.text().includes('Inactive'));
  assert.ok(h.catalogueRequests.every((p) => p.isActive === true && !('groupId' in p)));
});

test('single Hindi selection submits its matching template and Word output', async () => {
  const h = await harness(); await ready(h); h.selectDocument(12); h.grid().onToggle({ contractId: 41 }); await h.flush();
  await h.button('Generate').props.onClick(); await h.flush();
  const request = h.requests.find((r) => r.url === '/document-exports');
  assert.equal(request.payload.templateId, 12); assert.equal(request.payload.outputFormat, 'DOCX');
  assert.deepEqual(request.payload.contractIds, [41]); assert.equal(request.payload.allMatching, false);
  assert.equal(h.events.sessionStorage.getItem('document-export-job'), 'job-1');
});

test('multiple selection persists across pages and PDF is submitted', async () => {
  const h = await harness(); await ready(h); h.grid().onToggle({ contractId: 41 }); await h.flush();
  h.grid().onQueryChange({ ...params, page: 1 }); h.grid().onResults({ ...params, page: 1 }, 501); await h.flush();
  assert.ok(h.grid().isSelected({ contractId: 41 })); h.grid().onToggle({ contractId: 42 });
  h.all().filter((n) => n.type === 'input' && n.props.type === 'radio')[1].props.onChange(); await h.flush();
  await h.button('Generate').props.onClick(); await h.flush();
  assert.deepEqual(h.requests[0].payload.contractIds, [41, 42]); assert.equal(h.requests[0].payload.outputFormat, 'PDF');
});

test('all matching sends filters without page IDs and requires large confirmation', async () => {
  const h = await harness(); await ready(h);
  nodes(h.grid().renderActions()).find((n) => n.type === 'button' && text(n).startsWith('Select All Matching')).props.onClick(); await h.flush();
  h.events.confirm = () => false; await h.button('Generate').props.onClick(); assert.equal(h.requests.length, 0);
  h.events.confirm = () => true; await h.button('Generate').props.onClick(); await h.flush();
  const request = h.requests[0].payload; assert.equal(request.allMatching, true); assert.equal(request.confirmLarge, true);
  assert.deepEqual(request.contractIds, []); assert.equal(request.criteria.branch, 'A1'); assert.equal(request.criteria.keyword, 'Ramesh');
});

test('filter changes clear selection and generation waits for results', async () => {
  const h = await harness(); await ready(h); h.grid().onToggle({ contractId: 41 }); await h.flush();
  h.grid().onQueryChange({ ...params, keyword: 'Changed' }); await h.flush();
  assert.equal(h.grid().isSelected({ contractId: 41 }), false); assert.equal(h.button('Generate').props.disabled, true);
});

test('template preview uses original-template endpoint without starting generation', async () => {
  const h = await harness(); await h.button('View Template').props.onClick(); await h.flush();
  assert.equal(h.requests[0].url, '/word-templates/11/preview');
  assert.ok(h.all().some((n) => n.type === 'iframe' && n.props.src === 'blob:test'));
  assert.equal(h.requests.filter((r) => r.payload).length, 0);
});

for (const outcome of ['success', 'error']) test(`Clear Result preserves selections and ignores late polling ${outcome}`, async () => {
  const h = await harness(); await ready(h); h.selectDocument(12); h.grid().onToggle({ contractId: 41 }); await h.flush();
  await h.button('Generate').props.onClick(); await h.flush();
  let resolve, reject;
  h.api.get = () => new Promise((yes, no) => { resolve = yes; reject = no; });
  const polling = h.runTimer();
  h.button('Clear Result').props.onClick(); await h.flush();
  assert.equal(h.button('Clear Result'), undefined);
  assert.equal(h.all().some((n) => n.type === 'progress'), false);
  assert.equal(h.events.sessionStorage.getItem('document-export-job'), undefined);
  assert.ok(h.grid().isSelected({ contractId: 41 }));
  assert.equal(h.all().find((n) => n.props?.['aria-label'] === 'Document').props.value, '12');
  if (outcome === 'success') resolve({ data: { id: 'job-1', status: 'RUNNING' } }); else reject(new Error('Late polling error'));
  await polling; await h.flush();
  assert.equal(h.button('Clear Result'), undefined); assert.equal(h.timers.size, 0);
  assert.equal(h.text().includes('Late polling error'), false);
  assert.ok(h.requests.every((r) => !r.url.includes('delete')));
});

test('starting generation clears previous status and errors before the request completes', async () => {
  const h = await harness(); await ready(h); h.grid().onToggle({ contractId: 41 }); await h.flush();
  await h.button('Generate').props.onClick(); await h.flush();
  h.api.get = async () => ({ data: { id: 'job-1', status: 'FAILED', total_count: 1, success_count: 0, failure_count: 1, failure_reason: 'Previous failure', errors: [{ contract_id: 41, error_message: 'Previous contract error' }] } });
  await h.runTimer(); await h.flush(); assert.ok(h.text().includes('Previous contract error'));
  let finish; h.api.post = () => new Promise((resolve) => { finish = resolve; });
  const generating = h.button('Generate').props.onClick(); await h.flush();
  assert.equal(h.button('Clear Result'), undefined); assert.equal(h.text().includes('Previous failure'), false);
  assert.equal(h.text().includes('Previous contract error'), false); assert.equal(h.all().some((n) => n.type === 'progress'), false);
  assert.ok(h.grid().isSelected({ contractId: 41 }));
  finish({ data: { id: 'job-2', status: 'QUEUED' } }); await generating; await h.flush();
  assert.ok(h.text().includes('job-2'));
});

test('Reminder Letter uses existing screen with date, filter and snapshot export payload', async () => {
  const h = await harness();
  h.extra.push({ id: 50, templateName: 'Reminder Letter', documentKey: 'Reminder Letter', languageCode: 'en', isActive: true });
  h.events.dispatchEvent(new Event('document-templates-changed')); await h.flush();h.selectDocument(50);await h.flush();
  const grid=h.all().find((n) => n.type==='ReminderLetterGrid');assert.ok(grid);
  assert.match(grid.props.asOnDate,/^\d{4}-\d{2}-\d{2}$/);assert.equal(grid.props.pendingOnly,true);
  const controls = grid.props.renderControls('refresh');
  assert.ok(text(controls).includes('Pending Due Loans'));assert.ok(text(controls).includes('All Contracts'));
  assert.ok(nodes(controls).some((n) => n.props?.['aria-label'] === 'As-on Date'));
  const query={...params, reminderSnapshotId:'snapshot-1', filters:{asOnDate:'2026-10-17',pendingOnly:true}};
  h.grid().onQueryChange(query);h.grid().onResults(query,10);h.grid().onToggle({contractId:41});await h.flush();
  await h.button('Generate').props.onClick();await h.flush();
  const payload=h.requests.find((r)=>r.url==='/document-exports').payload;
  assert.equal(payload.reminderSnapshotId,'snapshot-1');assert.equal(payload.pendingOnly,true);assert.equal(payload.search,'Ramesh');
  assert.equal(payload.asOnDate,grid.props.asOnDate);
});

test('Reminder grid reuses one calculation for pages/search/filter and recalculates for a changed date', async () => {
  const h=await harness({reminderGrid:true});
  const table=()=>h.all().find((n)=>n.type==='ServerDataTable');
  assert.ok(table());assert.deepEqual(table().props.columns.slice(1).map((c)=>c.headerName),['Loan No.','Borrower Name','EMI','Monthly Part Amount Due','No. of Overdues','Total Overdue EMI Amount']);
  await table().props.fetchPage({page:0,pageSize:25,keyword:'Ramesh'});
  await table().props.fetchPage({page:1,pageSize:25,keyword:'C-41'});
  h.setGridProps({pendingOnly:false});await h.flush();
  await table().props.fetchPage({page:0,pageSize:25,keyword:''});
  assert.equal(h.requests.filter((r)=>r.url.endsWith('/reminder/snapshots')).length,1);
  assert.equal(h.requests.at(-1).config.params.pendingOnly,false);
  h.setGridProps({asOnDate:'2026-10-18'});await h.flush();
  assert.equal(h.requests.filter((r)=>r.url.endsWith('/reminder/snapshots')).length,2);
});

const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const spinner = (h) => h.all().find((n) => n.props?.className === 'gd-spinner');
const table = (h) => h.all().find((n) => n.type === 'ServerDataTable');
const busyRegion = (h) => h.all().find((n) => n.props?.['aria-busy'] !== undefined);

test('initial calculations show accessible spinner until the actual request completes', async () => {
  const pending = deferred();
  const h = await harness({ reminderGrid: true, snapshotRequest: () => pending.promise });
  assert.ok(spinner(h)); assert.equal(spinner(h).props['aria-hidden'], 'true');
  assert.equal(busyRegion(h).props['aria-busy'], true);
  assert.ok(h.text().includes('Calculating Reminder Letter values...'));
  assert.ok(h.text().includes('Loading loan details and financial calculations'));
  assert.equal(h.button('Refresh Calculations').props.disabled, true);
  assert.equal(table(h), undefined);
  h.button('Refresh Calculations').props.onClick(); await h.flush();
  assert.equal(h.requests.length, 1);
  pending.resolve({ data: { id: 'initial-snapshot' } }); await h.flush();
  assert.equal(spinner(h), undefined); assert.ok(table(h));
  assert.equal(busyRegion(h).props['aria-busy'], false);
  assert.equal(h.button('Refresh Calculations').props.disabled, false);
});

test('manual refresh blocks repeated clicks, clears stale selection readiness and restores the grid', async () => {
  const h = await harness({ reminderGrid: true });
  const pending = deferred(); let calls = 0;
  h.api.post = () => { calls++; return pending.promise; };
  const refresh = h.button('Refresh Calculations').props.onClick;
  refresh(); refresh();
  assert.equal(h.gridQueries.at(-1).filters.refresh, 1);
  await h.flush();
  assert.equal(calls, 1); assert.ok(spinner(h)); assert.equal(table(h), undefined);
  h.button('Refresh Calculations').props.onClick(); await h.flush(); assert.equal(calls, 1);
  pending.resolve({ data: { id: 'refreshed-snapshot' } }); await h.flush();
  assert.ok(table(h)); assert.equal(spinner(h), undefined);
  await table(h).props.fetchPage({ page: 0, pageSize: 25, keyword: '' });
  assert.ok(h.requests.at(-1).url.endsWith('/refreshed-snapshot'));
});

test('calculation failure ends loading, shows the server error and allows retry', async () => {
  const pending = deferred();
  const h = await harness({ reminderGrid: true, snapshotRequest: () => pending.promise });
  pending.reject({ response: { data: { message: 'Calculation service unavailable' } } }); await h.flush();
  assert.equal(spinner(h), undefined); assert.equal(busyRegion(h).props['aria-busy'], false);
  assert.ok(h.all().some((n) => n.props?.role === 'alert'));
  assert.ok(h.text().includes('Calculation service unavailable'));
  const retry = deferred(); h.api.post = () => retry.promise;
  h.button('Retry calculations').props.onClick(); await h.flush();
  assert.ok(spinner(h)); assert.ok(!h.text().includes('Calculation service unavailable'));
  retry.resolve({ data: { id: 'retry-snapshot' } }); await h.flush();
  assert.ok(table(h)); assert.equal(h.button('Retry calculations'), undefined);
});

test('date changes ignore stale calculation responses and an empty date never spins', async () => {
  const first = deferred(), second = deferred();
  const h = await harness({ reminderGrid: true, snapshotRequest: () => first.promise });
  h.api.post = () => second.promise;
  h.setGridProps({ asOnDate: '2026-10-18' }); await h.flush();
  first.resolve({ data: { id: 'stale-snapshot' } }); await h.flush();
  assert.ok(spinner(h)); assert.equal(table(h), undefined);
  second.resolve({ data: { id: 'current-snapshot' } }); await h.flush();
  await table(h).props.fetchPage({ page: 0, pageSize: 25, keyword: '' });
  assert.ok(h.requests.at(-1).url.endsWith('/current-snapshot'));
  h.setGridProps({ asOnDate: '' }); await h.flush();
  assert.equal(spinner(h), undefined); assert.equal(table(h), undefined);
  assert.ok(h.text().includes('Select an As-on Date.'));
  assert.equal(h.button('Refresh Calculations').props.disabled, true);
});

test('grid loading errors preserve their message and offer a calculation retry', async () => {
  const h = await harness({ reminderGrid: true });
  h.api.get = async () => { throw { response: { data: { message: 'Loan details unavailable' } } }; };
  await assert.rejects(table(h).props.fetchPage({ page: 0, pageSize: 25, keyword: '' })); await h.flush();
  assert.ok(table(h)); assert.equal(spinner(h), undefined);
  assert.ok(h.text().includes('Loan details unavailable')); assert.ok(h.button('Retry calculations'));
});
