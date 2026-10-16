import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion as Motion } from 'framer-motion';
import { FileSpreadsheet, LoaderCircle } from 'lucide-react';
import styles from './StockStatementPage.module.css';
import { fetchActiveBankMasters } from '../../../services/bankMasterService';
import { fetchStockStatement, fetchStockStatementDate, downloadStockStatement } from '../../../services/stockStatementService';

const headings = ['Contract No', 'Contract Date', 'HIRER NAME', 'VEHICLE MAKE', '100% OF the amt',
  'Amount per instalment', 'Total EMI', 'Balance EMI', 'Balance Dues amount'];
const amount = (value) => Number(value || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dateText = (value, separator = '.') => value ? value.split('-').reverse().join(separator) : 'Unavailable';
const errorText = (error, fallback = 'Unable to load Stock Statement. Please try again.') => error?.response?.data?.message || fallback;

export default function StockStatementPage() {
  const [banks, setBanks] = useState([]);
  const [bankId, setBankId] = useState('');
  const [businessDate, setBusinessDate] = useState('');
  const [report, setReport] = useState(null);
  const [operation, setOperation] = useState(null);
  const inFlight = useRef(false);
  const busy = operation !== null;
  const generating = operation === 'generate';
  const downloading = operation === 'download';
  const [initializing, setInitializing] = useState(true);
  const [message, setMessage] = useState('');

  useEffect(() => {
    let active = true;
    Promise.all([fetchActiveBankMasters(), fetchStockStatementDate()])
      .then(([options, date]) => { if (active) { setBanks(options); setBusinessDate(date); } })
      .catch((error) => { if (active) setMessage(errorText(error)); })
      .finally(() => { if (active) setInitializing(false); });
    return () => { active = false; };
  }, []);

  async function generate(event) {
    event.preventDefault();
    if (inFlight.current || initializing) return;
    if (!bankId) { setMessage('Bank selection is required.'); return; }
    if (!businessDate) { setMessage('As On Date is required.'); return; }
    inFlight.current = true;
    setOperation('generate'); setMessage('');
    try {
      const data = await fetchStockStatement(bankId, businessDate);
      setReport(data); setBusinessDate(data.businessDate);
    } catch (error) { setMessage(errorText(error, 'Unable to generate Stock Statement. Please try again.')); }
    finally { inFlight.current = false; setOperation(null); }
  }

  async function download() {
    if (!report || inFlight.current) return;
    inFlight.current = true;
    setOperation('download'); setMessage('');
    try {
      const blob = await downloadStockStatement(report);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `Stock_Statement_${report.bankCode.replace(/[^A-Za-z0-9_-]/g, '_')}_${report.businessDate.split('-').reverse().join('')}.xlsx`;
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      setMessage(errorText(error, 'Unable to prepare your Excel file. Please try again.'));
      if (error.response?.status === 409) setReport(null);
    } finally { inFlight.current = false; setOperation(null); }
  }

  return <div className="flex h-full min-h-0 flex-col gap-4 text-sm">
    <h1 className="text-xl font-bold text-[#0052CC]">Stock Statement</h1>
    <form onSubmit={generate} className="flex flex-wrap items-end gap-4 rounded-lg border border-gray-300 bg-gray-50 p-4">
      <label className="flex min-w-64 flex-col gap-1 font-bold">Bank <span className="sr-only">required</span>
        <select required value={bankId} disabled={busy || initializing} onChange={(event) => {
          setBankId(event.target.value); setReport(null); setMessage('');
        }} className="rounded border border-gray-400 bg-white px-3 py-2 font-normal">
          <option value="">Select bank</option>
          {banks.map((bank) => <option key={bank.id} value={bank.id}>{bank.bankName}</option>)}
        </select>
      </label>
      <label className="flex flex-col gap-1 font-bold">As On Date
        <input type="date" required value={businessDate} disabled={busy || initializing}
          onChange={(event) => { setBusinessDate(event.target.value); setReport(null); setMessage(''); }}
          className="rounded border border-gray-400 bg-white px-3 py-2 font-normal" />
      </label>
      <button disabled={busy || initializing || !bankId || !businessDate} aria-busy={generating} className="inline-flex min-w-40 items-center justify-center gap-2 rounded bg-[#0052CC] px-4 py-2 font-bold text-white transition-shadow enabled:hover:shadow-md disabled:opacity-60">
        {generating && <LoaderCircle size={16} aria-hidden="true" className="animate-spin motion-reduce:animate-none" />}
        {generating ? 'Generating...' : 'Generate Report'}
      </button>
      <button type="button" onClick={download} disabled={!report || busy} aria-busy={downloading} className="inline-flex min-w-44 items-center justify-center gap-2 rounded bg-green-700 px-4 py-2 font-bold text-white transition-shadow enabled:hover:shadow-md disabled:opacity-60">
        {downloading ? <LoaderCircle size={16} aria-hidden="true" className="animate-spin motion-reduce:animate-none" /> : <FileSpreadsheet size={16} aria-hidden="true" />}
        {downloading ? 'Preparing Excel...' : 'Download Excel'}
      </button>
    </form>
    <AnimatePresence initial={false}>
      {busy && <Motion.div key="stock-progress" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.18 }} className="shrink-0 overflow-hidden">
        <div role="status" aria-live="polite" aria-atomic="true" className={`rounded-lg border px-4 py-3 ${generating ? 'border-blue-100 bg-blue-50/70 text-blue-800' : 'border-green-100 bg-green-50/70 text-green-800'}`}>
          <div className="mb-2 flex items-center gap-2 text-xs font-semibold">
            <LoaderCircle size={15} aria-hidden="true" className="animate-spin motion-reduce:animate-none" />
            {generating ? 'Preparing Stock Statement...' : 'Preparing your Excel file...'}
          </div>
          <div aria-hidden="true" className={`${styles.track} ${generating ? styles.blue : styles.green}`}><div className={styles.shimmer} /></div>
        </div>
      </Motion.div>}
    </AnimatePresence>
    {message && <p role="alert" className="text-red-700">{message}</p>}
    {report && <Motion.div key={report.fingerprint} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.18 }} className="flex min-h-0 flex-1 flex-col gap-4">
      <div className="text-center">
        <h2 className="font-bold">DUGAR FINANCE &amp; INVESTMENTS LIMITED</h2>
        <h3 className="font-bold">STOCK STATEMENT AS ON {dateText(report.businessDate)}</h3>
        <p>{report.bankName} | Total contracts: {report.contractCount}</p>
      </div>
      {report.contractCount === 0 && <p role="status">No outstanding contracts found for the selected bank.</p>}
      <div className="min-h-0 flex-1 overflow-auto rounded border border-gray-300">
        <table className="w-full min-w-[1400px] border-collapse">
          <thead className="sticky top-0 bg-blue-100"><tr>{headings.map((heading) => <th key={heading} className="border border-gray-300 px-3 py-2 text-center">{heading}</th>)}</tr></thead>
          <tbody>{report.rows.map((row) => <tr key={row.contractId}>
            {[row.contractNumber, dateText(row.contractDate, '/'), row.hirerName, row.vehicleDetails,
              amount(row.loanAmount), amount(row.principalPerInstallment), row.totalEmi, row.balanceEmi,
              amount(row.principalOutstanding)].map((value, index) => <td key={index} className={`whitespace-pre-line border border-gray-300 px-3 py-2 align-top ${index >= 4 ? 'text-right' : ''}`}>{value}</td>)}
          </tr>)}</tbody>
        </table>
      </div>
    </Motion.div>}
  </div>;
}
