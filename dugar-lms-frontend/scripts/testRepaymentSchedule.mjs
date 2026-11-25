import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { resolveMenuPath } from '../src/utils/menuRouteMap.js';

const source = fs.readFileSync(new URL('../src/services/repaymentScheduleService.js', import.meta.url), 'utf8')
  .replace(/^import .*;\s*$/m, '').replace(/^export /gm, '');
function harness(get = async () => ({ data: new Blob(['pdf'], { type: 'application/pdf' }) })) {
  const calls = [], intervals = new Map();
  const tab = { opener: {}, document: { title: '', body: {} }, closed: false,
    location: { replace(url) { calls.push(['navigate', url]); } }, close() { this.closed = true; } };
  const window = { open: (...args) => { calls.push(['open', ...args]); return tab; },
    setInterval: (fn) => { intervals.set(1, fn); return 1; }, clearInterval: (id) => intervals.delete(id) };
  const URL = { createObjectURL: () => { calls.push(['blob']); return 'blob:repayment'; },
    revokeObjectURL: (url) => calls.push(['revoke', url]) };
  const api = { get: async (...args) => { calls.push(['get', ...args]); return get(...args); } };
  const service = new Function('apiClient', 'window', 'URL', `${source}\nreturn {fetchRepaymentSchedulePdf,fetchNormalRepaymentSchedulePdf,openRepaymentSchedulePdf};`)(api, window, URL);
  return { ...service, calls, intervals, tab, window };
}

test('authenticated API client requests a PDF blob for the selected contract ID', async () => {
  const h = harness(); await h.fetchRepaymentSchedulePdf(6535);
  assert.deepEqual(h.calls[0], ['get', '/contracts/6535/repayment-schedule/pdf', { responseType: 'blob' }]);
});
test('standalone report menus resolve to distinct routes and open their corresponding PDFs', async () => {
  for (const [menuCode, variant, fetcher, suffix] of [
    ['OPERATIONS_TRANSACTIONS_REPAYMENT_SCHEDULE_WITH_INTEREST', 'with-interest', 'fetchRepaymentSchedulePdf', '/pdf'],
    ['OPERATIONS_TRANSACTIONS_REPAYMENT_SCHEDULE_NORMAL', 'normal', 'fetchNormalRepaymentSchedulePdf', '/normal/pdf'],
  ]) {
    const h = harness();
    assert.equal(resolveMenuPath({ menuCode }), `/operations/transactions/repayment-schedule-${variant}`);
    await h.openRepaymentSchedulePdf(42, h[fetcher]);
    assert.deepEqual(h.calls, [
      ['open', '', '_blank'],
      ['get', `/contracts/42/repayment-schedule${suffix}`, { responseType: 'blob' }],
      ['blob'],
      ['navigate', 'blob:repayment'],
    ]);
  }
});
test('opens a tab before awaiting HTTP, displays PDF, revokes URL only after tab closes', async () => {
  let resolve;
  const h = harness(() => new Promise(r => { resolve = r; }));
  const pending = h.openRepaymentSchedulePdf(6535);
  assert.deepEqual(h.calls.map(c => c[0]), ['open', 'get']);
  assert.equal(h.tab.opener, null);
  resolve({ data: new Blob(['pdf'], { type: 'application/pdf' }) }); await pending;
  assert.deepEqual(h.calls.at(-1), ['navigate', 'blob:repayment']);
  h.intervals.get(1)(); assert.equal(h.calls.some(c => c[0] === 'revoke'), false);
  h.tab.closed = true; h.intervals.get(1)();
  assert.deepEqual(h.calls.at(-1), ['revoke', 'blob:repayment']); assert.equal(h.intervals.size, 0);
});
test('blocked pop-up produces a meaningful message without sending a request', async () => {
  const h = harness(); h.window.open = () => null;
  await assert.rejects(h.openRepaymentSchedulePdf(6535), /Allow pop-ups/);
  assert.equal(h.calls.some(c => c[0] === 'get'), false);
});
test('blob JSON API errors close the pending tab and preserve the server message', async () => {
  const h = harness(async () => { throw { response: { status: 422, data: new Blob(['{"message":"Unsupported repayment frequency"}']) } }; });
  await assert.rejects(h.openRepaymentSchedulePdf(6535), /Unsupported repayment frequency/);
  assert.equal(h.tab.closed, true); assert.equal(h.intervals.size, 0);
});
test('session errors have a sign-in message and closing a pending tab does not create a blob URL', async () => {
  const h = harness(async () => { throw { response: { status: 401, data: new Blob(['invalid']) } }; });
  await assert.rejects(h.openRepaymentSchedulePdf(6535), /sign in again/);
  const closed = harness(); closed.tab.closed = true;
  await closed.openRepaymentSchedulePdf(6535);
  assert.equal(closed.calls.some(c => c[0] === 'blob'), false);
});
