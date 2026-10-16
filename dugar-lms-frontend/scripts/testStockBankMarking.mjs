import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import test from 'node:test';
import { transformWithOxc } from 'vite';
const { code } = await transformWithOxc(await readFile(new URL('../src/pages/Credit/transaction/StockBankMarkingPage.jsx', import.meta.url), 'utf8'), 'StockBankMarkingPage.jsx');
const compiled = code.replace(/^import .*;$/gm, '').replace('export default function', 'function');
const nodes = (tree) => Array.isArray(tree) ? tree.flatMap(nodes) : tree && typeof tree === 'object' ? [tree, ...nodes(tree.props?.children)] : [];
const text = (tree) => Array.isArray(tree) ? tree.map(text).join('') : tree == null ? '' : typeof tree === 'object' ? text(tree.props?.children) : String(tree);
const event = { preventDefault() {} };
const contract = { contractId: 7, contractNumber: 'L-7', borrowerName: 'Borrower', loanAmount: 50000, bankId: 2, bankName: 'Inactive bank', status: 'L', vehicleMake: 'Make', registrationNumber: 'Reg', contractDate: '2026-09-01' };
async function harness() {
 let cursor = 0; const slots = [], effects = [];
 const state = { writes: 0, get: async () => ({ data: contract }), put: async () => ({ data: { ...contract, bankId: 1, bankName: 'Active bank' } }) };
 const useState = (initial) => { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], (value) => { slots[i] = value; }]; };
 const useRef = (initial) => { const i = cursor++; if (!(i in slots)) slots[i] = { current: initial }; return slots[i]; };
 const useEffect = (effect) => { const i = cursor++; if (!(i in slots)) { slots[i] = true; effects.push(effect); } };
 const jsx = (type, props) => ({ type, props });
 const dependencies = { useState, useRef, useEffect, LoaderCircle: 'LoaderCircle', _jsx: jsx, _jsxs: jsx,
  fetchActiveBankMasters: async () => [{ id: 1, bankName: 'Active bank' }],
  apiClient: { get: (...args) => state.get(...args), put: (...args) => { state.writes++; return state.put(...args); } } };
 const Page = new Function(...Object.keys(dependencies), `${compiled}\nreturn StockBankMarkingPage;`)(...Object.values(dependencies));
 const render = () => { cursor = 0; return Page(); };
 render(); effects.forEach((e) => e()); await new Promise((resolve) => setImmediate(resolve));
 const all = () => nodes(render()); const forms = () => all().filter((n) => n.type === 'form');
 const input = () => all().find((n) => n.type === 'input');
 input().props.onChange({ target: { value: 'L-7' } });
 return { state, render, all, forms, input, search: () => forms()[0].props.onSubmit(event) };
}
test('search displays contract details and preserves current inactive bank', async () => {
 const h = await harness();
 h.state.get = async (url) => { assert.equal(url, '/stock-bank-marking/contracts/L-7'); return { data: contract }; };
 await h.search();
 assert.match(text(h.render()), /Borrower/); assert.match(text(h.render()), /50,000.00/);
 assert.match(text(h.render()), /Make/); assert.match(text(h.render()), /Reg/);
 assert.equal(h.all().find((n) => n.type === 'select').props.value, '2');
 assert.match(text(h.render()), /Inactive bank.*inactive/);
 h.input().props.onChange({ target: { value: 'Other' } });
 assert.equal(h.forms().length, 1);
});
test('save uses only bank ID, prevents duplicate submission and refreshes bank', async () => {
 const h = await harness(); await h.search();
 h.all().find((n) => n.type === 'select').props.onChange({ target: { value: '1' } });
 let finish;
 h.state.put = (url, payload) => { assert.equal(url, '/stock-bank-marking/contracts/7'); assert.deepEqual(payload, { bankId: 1 }); return new Promise((resolve) => { finish = resolve; }); };
 const save = h.forms()[1].props.onSubmit; const pending = save(event); await save(event);
 assert.equal(h.state.writes, 1); assert.match(text(h.render()), /Saving bank assignment/);
 assert.ok(h.all().filter((n) => n.type === 'button').every((n) => n.props.disabled));
 finish({ data: { ...contract, bankId: 1, bankName: 'Active bank' } }); await pending;
 assert.match(text(h.render()), /saved successfully/);
 assert.equal(h.all().find((n) => n.type === 'select').props.value, '1');
});
test('failed search and save surface errors and allow retry', async () => {
 const h = await harness(); h.state.get = async () => { throw { response: { data: { message: 'Not accessible' } } }; };
 await h.search(); assert.match(text(h.render()), /Not accessible/); assert.equal(h.forms().length, 1);
 h.state.get = async () => ({ data: contract }); await h.search();
 h.state.put = async () => { throw { response: { data: { message: 'Bank inactive' } } }; };
 await h.forms()[1].props.onSubmit(event); assert.match(text(h.render()), /Bank inactive/);
 assert.ok(h.all().filter((n) => n.type === 'button').every((n) => !n.props.disabled));
});
