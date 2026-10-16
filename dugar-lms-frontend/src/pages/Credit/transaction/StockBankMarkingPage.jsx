import { useEffect, useRef, useState } from 'react';
import { LoaderCircle } from 'lucide-react';
import apiClient from '../../../api/apiClient';
import { fetchActiveBankMasters } from '../../../services/bankMasterService';

export default function StockBankMarkingPage() {
  const [number, setNumber] = useState('');
  const [contract, setContract] = useState(null);
  const [banks, setBanks] = useState([]);
  const [bankId, setBankId] = useState('');
  const [operation, setOperation] = useState('initializing');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const busy = operation !== null;
  useEffect(() => {
    let active = true;
    fetchActiveBankMasters().then((data) => { if (active) setBanks(data); })
      .catch(() => { if (active) setError('Unable to load banks. Refresh the page to retry.'); })
      .finally(() => { if (active) setOperation(null); });
    return () => { active = false; };
  }, []);
  async function search(event) {
    event.preventDefault();
    if (inFlight.current || busy || !number.trim()) return;
    inFlight.current = true; setOperation('search'); setError(''); setMessage(''); setContract(null);
    try {
      const { data } = await apiClient.get(`/stock-bank-marking/contracts/${encodeURIComponent(number.trim())}`);
      setContract(data); setBankId(data.bankId == null ? '' : String(data.bankId));
    } catch (failure) { setError(failure.response?.data?.message || 'Unable to find this contract.'); }
    finally { inFlight.current = false; setOperation(null); }
  }
  async function save(event) {
    event.preventDefault();
    if (inFlight.current || busy) return;
    if (!contract || !bankId) { setError('Select a contract and bank.'); return; }
    inFlight.current = true; setOperation('save'); setError(''); setMessage('');
    try {
      const { data } = await apiClient.put(`/stock-bank-marking/contracts/${contract.contractId}`, { bankId: Number(bankId) });
      setContract(data); setBankId(String(data.bankId)); setMessage('Stock marked bank saved successfully.');
    } catch (failure) { setError(failure.response?.data?.message || 'Unable to save the bank assignment.'); }
    finally { inFlight.current = false; setOperation(null); }
  }
  const options = [...banks];
  if (contract?.bankId != null && !options.some((bank) => bank.id === contract.bankId)) {
    options.push({ id: contract.bankId, bankName: `${contract.bankName} (inactive ? current)` });
  }
  const details = contract ? [
    ['Contract Number', contract.contractNumber], ['Contract Date', contract.contractDate?.split('-').reverse().join('.')],
    ['Borrower Name', contract.borrowerName], ['Loan Amount', Number(contract.loanAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })],
    ['Vehicle Make', contract.vehicleMake], ['Vehicle Registration Number', contract.registrationNumber],
    ['Contract Status', contract.status], ['Current Stock Marked Bank', contract.bankName || 'Unassigned'],
  ] : [];
  return <div className="flex h-full flex-col gap-4 overflow-auto p-4 text-sm">
    <h1 className="text-xl font-bold text-[#0052CC]">Stock Marking to Bank</h1>
    <form onSubmit={search} className="flex flex-wrap items-end gap-4 rounded-lg border border-gray-300 bg-gray-50 p-4">
      <label className="flex flex-col gap-1 font-bold">Contract Number
        <input required value={number} disabled={busy} onChange={(event) => { setNumber(event.target.value); setContract(null); setMessage(''); setError(''); }}
          className="rounded border border-gray-400 px-3 py-2 font-normal" />
      </label>
      <button disabled={busy || !number.trim()} className="rounded bg-[#0052CC] px-4 py-2 font-bold text-white disabled:opacity-60">Search</button>
    </form>
    {busy && <div role="status" className="flex items-center gap-2 rounded bg-blue-50 p-3 text-blue-800"><LoaderCircle size={18} className="animate-spin motion-reduce:animate-none" />
      {operation === 'save' ? 'Saving bank assignment...' : operation === 'search' ? 'Loading contract...' : 'Loading banks...'}
    </div>}
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {message && <p role="status" className="text-green-700">{message}</p>}
    {contract && <form onSubmit={save} className="space-y-4 rounded-lg border border-gray-300 bg-white p-4">
      <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{details.map(([label, value]) => <div key={label}>
        <dt className="font-bold text-gray-600">{label}</dt><dd>{value || '?'}</dd>
      </div>)}</dl>
      <label className="flex max-w-md flex-col gap-1 font-bold">Stock Marked to Bank
        <select required value={bankId} disabled={busy} onChange={(event) => { setBankId(event.target.value); setMessage(''); }} className="rounded border border-gray-400 px-3 py-2 font-normal">
          <option value="">Select bank</option>{options.map((bank) => <option key={bank.id} value={bank.id}>{bank.bankName}</option>)}
        </select>
      </label>
      <button disabled={busy || !bankId} aria-busy={operation === 'save'} className="inline-flex items-center gap-2 rounded bg-[#0052CC] px-4 py-2 font-bold text-white disabled:opacity-60">
        {operation === 'save' && <LoaderCircle size={16} className="animate-spin motion-reduce:animate-none" />}{operation === 'save' ? 'Saving...' : 'Save'}
      </button>
    </form>}
  </div>;
}
