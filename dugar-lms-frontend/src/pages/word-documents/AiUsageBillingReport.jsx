import { useEffect, useRef, useState } from 'react';
import api from '../../api/apiClient';

const fields = ['fromDate', 'toDate', 'username', 'module', 'feature', 'documentType', 'language', 'contractNumber', 'model', 'apiStatus', 'generationStatus'];
const columns = ['occurred_at', 'username', 'module', 'template_name', 'language_code', 'contract_number', 'model', 'input_tokens', 'cached_input_tokens', 'output_tokens', 'total_tokens', 'estimated_inr', 'multiplier', 'billable_inr', 'api_status', 'generation_status'];
const label = (value) => value.replaceAll('_', ' ').replace(/([a-z])([A-Z])/g, '$1 $2');
const display = (value) => value == null ? '—' : String(value);
export default function AiUsageBillingReport() {
  const [filters, setFilters] = useState({});
  const [applied, setApplied] = useState({});
  const [page, setPage] = useState(0);
  const [report, setReport] = useState({ rows: [], total: 0, summary: {} });
  const [detail, setDetail] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const version = useRef(0);
  useEffect(() => {
    const request = ++version.current;
    setBusy(true); setError('');
    api.get('/document-automation/report', { params: { ...applied, page, size: 50 } })
      .then(({ data }) => { if (version.current === request) setReport(data); })
      .catch((e) => setError(e.response?.data?.message || e.message))
      .finally(() => { if (version.current === request) setBusy(false); });
  }, [applied, page]);
  const action = async (fn) => {
    setBusy(true); setError('');
    try { await fn(); } catch (e) { setError(e.response?.data?.message || e.message); }
    finally { setBusy(false); }
  };
  const download = () => action(async () => {
    const response = await api.get('/document-automation/report-export', { params: applied, responseType: 'blob' });
    const url = URL.createObjectURL(response.data); const a = document.createElement('a'); a.href = url; a.download = 'ai-usage.xlsx'; a.click(); URL.revokeObjectURL(url);
  });
  const print = () => {
    const popup = window.open('', '_blank');
    if (!popup) { setError('Allow the print window to open.'); return; }
    action(async () => {
      const { data } = await api.get('/document-automation/report-print', { params: applied, responseType: 'text' });
      popup.document.write(data);
      popup.document.close(); popup.focus(); popup.print();
    });
  };
  return <div className="h-full overflow-auto bg-white p-4 text-xs space-y-4">
    <p className="text-slate-500">Administration / Reports / AI API Usage & Billing</p>
    <h1 className="text-lg font-black text-blue-700">AI API Usage & Billing</h1>
    <p>Provider usage costs are calculated estimates. Unreported usage has unknown cost and remains available for reconciliation.</p>
    <form onSubmit={(e) => { e.preventDefault(); setPage(0); setApplied({ ...filters }); }} className="flex flex-wrap gap-3">
      {fields.map((f) => <label key={f} className="capitalize">{label(f)}<input aria-label={label(f)} type={f.endsWith('Date') ? 'date' : 'text'} value={filters[f] || ''} onChange={(e) => setFilters((v) => ({ ...v, [f]: e.target.value }))} className="block border p-2" /></label>)}
      <button disabled={busy} className="border px-4 bg-blue-700 text-white">Apply Filters</button>
    </form>
    {error && <p role="alert" className="text-red-700">{error}</p>}
    <div className="flex flex-wrap gap-3">{Object.entries(report.summary).map(([key, value]) => <div key={key} className="border bg-blue-50 p-3"><p className="capitalize">{label(key)}</p><strong>{display(value)}</strong></div>)}</div>
    <div className="flex gap-2"><button disabled={busy} onClick={download} className="border p-2">Export Excel</button><button disabled={busy} onClick={print} className="border p-2">Export PDF / Print</button></div>
    <div className="overflow-auto"><table className="w-full"><thead><tr>{columns.map((c) => <th key={c} className="border p-2 capitalize">{label(c)}</th>)}<th>Details</th></tr></thead><tbody>{report.rows.map((row, i) => <tr key={`${row.generation_id}-${row.usage_id}-${i}`}>{columns.map((c) => <td key={c} className="border p-2 whitespace-nowrap">{display(row[c])}</td>)}<td className="border p-2">{row.generation_id && <button onClick={() => action(async () => setDetail((await api.get(`/document-automation/report/${row.generation_id}`)).data))}>View</button>}</td></tr>)}</tbody></table></div>
    {!busy && report.rows.length === 0 && <p>No matching generation or API records.</p>}
    <div className="flex gap-3"><button disabled={busy || page === 0} onClick={() => setPage((p) => p - 1)}>Previous</button><span>Page {page + 1} | {report.total} records</span><button disabled={busy || (page + 1) * 50 >= report.total} onClick={() => setPage((p) => p + 1)}>Next</button></div>
    {detail && <div role="dialog" className="fixed inset-5 z-50 overflow-auto border bg-white p-5 shadow-2xl"><button onClick={() => setDetail(null)} className="border p-2">Close</button><h2 className="font-bold">Document Audit & API Requests</h2><pre className="whitespace-pre-wrap">{JSON.stringify(detail, null, 2)}</pre></div>}
  </div>;
}
