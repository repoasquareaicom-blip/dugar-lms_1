// Exercise the page's handlers and rendered loading states without a live backend.
// Vite compiles the actual JSX; a small hook harness controls asynchronous requests.
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import test from 'node:test';
import { transformWithOxc } from 'vite';

const pageUrl = new URL('../src/pages/Credit/reports/StockStatementPage.jsx', import.meta.url);
const { code } = await transformWithOxc(await readFile(pageUrl, 'utf8'), 'StockStatementPage.jsx');
const compiled = code.replace(/^import .*;$/gm, '').replace('export default function', 'function');
const report = { bankId: 1, bankCode: 'UBI', bankName: 'UNION BANK OF INDIA', businessDate: '2026-10-15',
  fingerprint: 'preview', contractCount: 1, rows: [{ contractId: 1, contractNumber: '15691', contractDate: '2025-10-16',
    hirerName: 'Borrower\nAddress', vehicleDetails: 'Make\nRegistration', loanAmount: 100, principalPerInstallment: 10,
    totalEmi: 10, balanceEmi: 5, principalOutstanding: 50 }] };
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
const event = { preventDefault() {} };

function nodes(tree) {
  if (Array.isArray(tree)) return tree.flatMap(nodes);
  if (!tree || typeof tree !== 'object') return [];
  return [tree, ...nodes(tree.props?.children)];
}
function text(tree) {
  if (Array.isArray(tree)) return tree.map(text).join('');
  if (tree == null || typeof tree === 'boolean') return '';
  return typeof tree === 'object' ? text(tree.props?.children) : String(tree);
}

async function harness() {
  let cursor = 0;
  const slots = [], effects = [];
  const state = {
    generateCalls: 0, downloadCalls: 0, clicks: 0,
    generate: async () => report, download: async () => new Blob(['xlsx']),
  };
  const useState = (initial) => {
    const index = cursor++;
    if (!(index in slots)) slots[index] = initial;
    return [slots[index], (value) => { slots[index] = value; }];
  };
  const useRef = (initial) => {
    const index = cursor++;
    if (!(index in slots)) slots[index] = { current: initial };
    return slots[index];
  };
  const useEffect = (effect) => {
    const index = cursor++;
    if (!(index in slots)) { slots[index] = true; effects.push(effect); }
  };
  const jsx = (type, props, key) => ({ type, props, key });
  const dependencies = {
    useState, useRef, useEffect, AnimatePresence: 'AnimatePresence', Motion: { div: 'motion.div' },
    FileSpreadsheet: 'FileSpreadsheet', LoaderCircle: 'LoaderCircle', styles: {}, _jsx: jsx, _jsxs: jsx,
    fetchActiveBankMasters: async () => [{ id: 1, bankName: 'UNION BANK OF INDIA' }],
    fetchStockStatementDate: async () => '2026-10-15',
    fetchStockStatement: async (...args) => { state.generateCalls++; return state.generate(...args); },
    downloadStockStatement: async (...args) => { state.downloadCalls++; return state.download(...args); },
    URL: { createObjectURL: () => 'blob:test', revokeObjectURL() {} },
    window: { setTimeout(callback) { callback(); } },
    document: { createElement: () => ({ click() { state.clicks++; state.onClick?.(); } }) },
  };
  const Page = new Function(...Object.keys(dependencies), `${compiled}\nreturn StockStatementPage;`)(...Object.values(dependencies));
  const render = () => { cursor = 0; return Page(); };
  render(); effects.forEach((effect) => effect());
  await new Promise((resolve) => setImmediate(resolve));
  nodes(render()).find((node) => node.type === 'select').props.onChange({ target: { value: '1' } });
  const form = () => nodes(render()).find((node) => node.type === 'form');
  const buttons = () => nodes(render()).filter((node) => node.type === 'button');
  const status = () => nodes(render()).find((node) => node.props?.role === 'status' && node.props?.['aria-live']);
  const seed = async () => { await form().props.onSubmit(event); };
  return { state, render, form, buttons, status, seed };
}

test('generation shows immediate blue loading state, preserves grid, blocks duplicate requests and restores buttons', async () => {
  const h = await harness(); await h.seed();
  const pending = deferred(); h.state.generate = () => pending.promise;
  const submit = h.form().props.onSubmit;
  const request = submit(event); await submit(event);
  assert.equal(h.state.generateCalls, 2); // Initial report plus exactly one refresh.
  assert.match(text(h.buttons()[0]), /Generating\.\.\./);
  assert.match(text(h.buttons()[1]), /Download Excel/);
  assert.ok(h.buttons().every((button) => button.props.disabled));
  assert.match(text(h.status()), /Preparing Stock Statement\.\.\./);
  assert.match(h.status().props.className, /blue/);
  assert.ok(nodes(h.render()).some((node) => node.type === 'LoaderCircle' && node.props.className.includes('animate-spin')));
  assert.ok(nodes(h.render()).some((node) => node.type === 'table'));
  pending.resolve({ ...report, fingerprint: 'refreshed' }); await request;
  assert.equal(h.status(), undefined);
  assert.equal(text(h.buttons()[0]), 'Generate Report');
  assert.ok(h.buttons().every((button) => !button.props.disabled));
});

test('generation failure stops animation, retains preview and permits retry', async () => {
  const h = await harness(); await h.seed();
  h.state.generate = async () => { throw new Error('Network Error'); };
  await h.form().props.onSubmit(event);
  assert.equal(h.status(), undefined);
  assert.match(text(h.render()), /Unable to generate Stock Statement\. Please try again\./);
  assert.ok(nodes(h.render()).some((node) => node.type === 'table'));
  assert.ok(h.buttons().every((button) => !button.props.disabled));
  h.state.generate = async () => report;
  await h.form().props.onSubmit(event);
  assert.ok(!text(h.render()).includes('Unable to generate'));
});

test('Excel uses green loading state through download initiation and prevents duplicates', async () => {
  const h = await harness(); await h.seed();
  const pending = deferred(); h.state.download = (data) => { assert.equal(data, report); return pending.promise; };
  const download = h.buttons()[1].props.onClick;
  const request = download(); await download();
  assert.equal(h.state.downloadCalls, 1);
  assert.equal(text(h.buttons()[0]), 'Generate Report');
  assert.match(text(h.buttons()[1]), /Preparing Excel\.\.\./);
  assert.match(text(h.status()), /Preparing your Excel file\.\.\./);
  assert.match(h.status().props.className, /green/);
  assert.ok(h.buttons().every((button) => button.props.disabled));
  assert.ok(nodes(h.render()).some((node) => node.type === 'table'));
  h.state.onClick = () => assert.match(text(h.buttons()[1]), /Preparing Excel/);
  pending.resolve(new Blob(['xlsx'])); await request;
  assert.equal(h.state.clicks, 1);
  assert.equal(h.status(), undefined);
  assert.match(text(h.buttons()[1]), /Download Excel/);
  assert.ok(h.buttons().every((button) => !button.props.disabled));
});

test('Excel failure restores controls and shows a friendly error; stale-report behavior is retained', async () => {
  const h = await harness(); await h.seed();
  h.state.download = async () => { throw new Error('Network Error'); };
  await h.buttons()[1].props.onClick();
  assert.equal(h.status(), undefined);
  assert.equal(h.state.clicks, 0);
  assert.match(text(h.render()), /Unable to prepare your Excel file\. Please try again\./);
  assert.ok(h.buttons().every((button) => !button.props.disabled));
  h.state.download = async () => { throw { response: { status: 409, data: { message: 'Generate the report again.' } } }; };
  await h.buttons()[1].props.onClick();
  assert.equal(h.status(), undefined);
  assert.match(text(h.render()), /Generate the report again\./);
  assert.equal(h.buttons()[1].props.disabled, true);
  assert.equal(h.buttons()[0].props.disabled, false);
});


test('selected date invalidates preview and is used for preview and Excel', async () => {
 const h = await harness();
 const dateInput = () => nodes(h.render()).find((n) => n.type === 'input' && n.props.type === 'date');
 assert.equal(dateInput().props.value, '2026-10-15');
 await h.seed();
 dateInput().props.onChange({ target: { value: '2026-09-30' } });
 assert.equal(h.buttons()[1].props.disabled, true);
 assert.ok(!nodes(h.render()).some((n) => n.type === 'table'));
 h.state.generate = async (bank, date) => {
  assert.equal(bank, '1'); assert.equal(date, '2026-09-30');
  return { ...report, businessDate: date };
 };
 await h.form().props.onSubmit(event);
 assert.match(text(h.render()), /STOCK STATEMENT AS ON 30.09.2026/);
 h.state.download = async (data) => { assert.equal(data.businessDate, '2026-09-30'); return new Blob(['xlsx']); };
 await h.buttons()[1].props.onClick();
});
