import { Search } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { fetchInterestWiseAging, fetchLoanTicketWiseAging } from '../../../services/agingAnalysisService';
import { fetchContractAreas } from '../../../services/contractsService';

const fieldClass = 'h-8 w-full border-0 bg-white px-2 text-[12px] text-black outline-none focus:bg-blue-50';
const today = new Date().toISOString().slice(0, 10);

const columns = [
  ['label', 'Loan Amount (Rs. lakh)', 'left'],
  ['principalOutstanding', 'Principal O/s', 'right'],
  ['standard', 'Standard', 'right'],
  ['par0To30', '0 - 30', 'right'],
  ['par31To60', '31 - 60', 'right'],
  ['par61To90', '61 - 90', 'right'],
  ['par91To180', '91 - 180', 'right'],
  ['par181To365', '181 - 365', 'right'],
  ['parAbove365', '> 365', 'right'],
];

function formatValue(value, key) {
  if (key === 'label') return value || '';
  return Number(value || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function totalRow(rows, label) {
  if (!rows.length) return null;
  return {
    label,
    principalOutstanding: rows.reduce((sum, row) => sum + Number(row.principalOutstanding || 0), 0),
    standard: rows.reduce((sum, row) => sum + Number(row.standard || 0), 0),
    par0To30: rows.reduce((sum, row) => sum + Number(row.par0To30 || 0), 0),
    par31To60: rows.reduce((sum, row) => sum + Number(row.par31To60 || 0), 0),
    par61To90: rows.reduce((sum, row) => sum + Number(row.par61To90 || 0), 0),
    par91To180: rows.reduce((sum, row) => sum + Number(row.par91To180 || 0), 0),
    par181To365: rows.reduce((sum, row) => sum + Number(row.par181To365 || 0), 0),
    parAbove365: rows.reduce((sum, row) => sum + Number(row.parAbove365 || 0), 0),
  };
}

export default function AgingMatrixReportPage({ type }) {
  const [filters, setFilters] = useState({ asOnDate: today, areaCode: '' });
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [areaOpen, setAreaOpen] = useState(false);
  const [areaOptions, setAreaOptions] = useState([]);
  const [areaLoading, setAreaLoading] = useState(false);
  const areaRef = useRef(null);
  const isInterestWise = type === 'interest';
  const title = isInterestWise ? 'Interest Wise' : 'Loan Ticket Wise';
  const rows = data?.rows || [];
  const footer = totalRow(rows, 'Total');
  const tableColumns = isInterestWise ? [['label', 'Interest Rate', 'left'], ...columns.slice(1)] : columns;
  const setField = (field, value) => setFilters((current) => ({ ...current, [field]: value }));

  useEffect(() => {
    if (!areaOpen) return undefined;
    let active = true;
    const timer = window.setTimeout(async () => {
      setAreaLoading(true);
      try {
        const options = await fetchContractAreas({ keyword: filters.areaCode, limit: 20 });
        if (active) setAreaOptions(options);
      } catch {
        if (active) setAreaOptions([]);
      } finally {
        if (active) setAreaLoading(false);
      }
    }, 200);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [areaOpen, filters.areaCode]);

  useEffect(() => {
    const onPointerDown = (event) => {
      if (!areaRef.current?.contains(event.target)) setAreaOpen(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, []);

  const generate = async () => {
    setLoading(true);
    setMessage('');
    try {
      const response = isInterestWise ? await fetchInterestWiseAging(filters) : await fetchLoanTicketWiseAging(filters);
      setData(response);
    } catch (error) {
      setData(null);
      setMessage(error?.response?.data?.message || error?.message || `Unable to load ${title}.`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col bg-white font-['Segoe_UI',Arial,sans-serif] text-[12px] text-black">
      <div className="no-print border-b border-black/20 bg-[#f8fafc] px-2 py-1">
        <div className="text-[16px] font-bold uppercase text-[#0052CC]">{title}</div>
        <div className="text-[11px] font-bold text-black/60">PAR - days past due (Rs. crore)</div>
      </div>
      <div className="no-print border-b border-black/20 bg-white px-2 py-2 text-[12px] text-black">
        <table className="w-full max-w-xl border-collapse border border-black/30">
          <thead>
            <tr className="bg-slate-100">
              <th className="w-1/2 border border-black/30 px-2 py-1 text-left font-bold uppercase">As On Date</th>
              <th className="w-1/2 border border-black/30 px-2 py-1 text-left font-bold uppercase">Area</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="border border-black/30">
                <input className={fieldClass} type="date" value={filters.asOnDate} onChange={(event) => setField('asOnDate', event.target.value)} />
              </td>
              <td ref={areaRef} className="relative border border-black/30">
                <input
                  className={fieldClass}
                  value={filters.areaCode}
                  onFocus={() => setAreaOpen(true)}
                  onChange={(event) => {
                    setAreaOpen(true);
                    setField('areaCode', event.target.value);
                  }}
                  placeholder="Search area..."
                />
                {areaOpen && (
                  <div className="absolute left-0 right-0 top-8 z-40 max-h-56 overflow-auto border border-blue-900 bg-white shadow-xl">
                    {areaLoading && <div className="px-2 py-2 text-[11px] font-bold text-blue-700">Loading areas...</div>}
                    {!areaLoading && areaOptions.length === 0 && <div className="px-2 py-2 text-[11px] font-bold text-black/60">No areas found</div>}
                    {!areaLoading && areaOptions.map((area) => (
                      <button
                        key={area}
                        type="button"
                        className="block w-full border-b border-black/10 px-2 py-1 text-left font-bold hover:bg-blue-50"
                        onMouseDown={(event) => {
                          event.preventDefault();
                          setField('areaCode', area);
                          setAreaOpen(false);
                        }}
                      >
                        {area}
                      </button>
                    ))}
                  </div>
                )}
              </td>
            </tr>
          </tbody>
        </table>
        <button type="button" disabled={loading} onClick={generate} className="mt-2 inline-flex h-8 items-center gap-1 border border-[#0052CC] bg-[#0052CC] px-3 text-[12px] font-bold uppercase text-white disabled:opacity-50">
          <Search size={14} /> Generate
        </button>
      </div>
      {message && <div className="border-b border-red-200 bg-red-50 px-2 py-1 font-bold text-red-700">{message}</div>}
      <div className="min-h-0 flex-1 overflow-auto bg-white">
        <table className="min-w-[1050px] border-collapse text-[12px] text-black">
          <thead className="sticky top-0 z-20 bg-[#e8edf5] font-bold">
            <tr>
              {tableColumns.map(([key, label, align]) => (
                <th key={key} className={`border border-black/40 px-2 py-1 ${align === 'right' ? 'text-right' : 'text-left'}`}>{label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={`${row.label}-${index}`} className={index % 2 === 0 ? 'bg-white' : 'bg-slate-50'}>
                {tableColumns.map(([key, , align]) => (
                  <td key={key} className={`border border-black/30 px-2 py-1 ${align === 'right' ? 'text-right' : 'text-left'}`}>{formatValue(row[key], key)}</td>
                ))}
              </tr>
            ))}
            {!loading && rows.length === 0 && (
              <tr>
                <td colSpan={tableColumns.length} className="border border-black/30 px-3 py-6 text-center font-bold uppercase text-black/50">No records found</td>
              </tr>
            )}
          </tbody>
          {footer && (
            <tfoot className="sticky bottom-0 z-10 bg-[#e8edf5] font-black">
              <tr>
                {tableColumns.map(([key, , align]) => (
                  <td key={key} className={`border border-black/40 px-2 py-1 ${align === 'right' ? 'text-right' : 'text-left'}`}>{formatValue(footer[key], key)}</td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>
      {loading && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/35 px-4">
          <div className="w-full max-w-sm border border-blue-900 bg-white px-5 py-4 text-center shadow-2xl">
            <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-blue-200 border-t-[#0052CC]" />
            <div className="mt-3 text-[14px] font-black uppercase text-[#0052CC]">Loading report</div>
            <div className="mt-1 text-[12px] font-bold text-black">Please wait while PAR buckets are calculated.</div>
          </div>
        </div>
      )}
    </div>
  );
}
