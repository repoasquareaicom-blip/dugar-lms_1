import { FileSpreadsheet, Printer, RotateCcw, Search } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { fetchContractsPage } from '../../services/contractsService';

const inputClass = 'h-8 w-full border border-black/30 bg-white px-2 text-[12px] font-bold text-black outline-none focus:border-[#0052CC]';

export default function AfcReportFilters({ filters, loading, onChange, onExportExcel, onGenerate, onPrint, onReset }) {
  const [open, setOpen] = useState(false);
  const [options, setOptions] = useState([]);
  const [lookupLoading, setLookupLoading] = useState(false);
  const wrapperRef = useRef(null);

  useEffect(() => {
    if (!open || !filters.loanNumber?.trim()) {
      setOptions([]);
      return undefined;
    }
    let active = true;
    const timer = window.setTimeout(async () => {
      setLookupLoading(true);
      try {
        const data = await fetchContractsPage({ keyword: filters.loanNumber, pageSize: 10, isDraft: false });
        const contracts = data?.content || data?.items || data?.data || [];
        if (active) setOptions(contracts);
      } catch {
        if (active) setOptions([]);
      } finally {
        if (active) setLookupLoading(false);
      }
    }, 250);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [filters.loanNumber, open]);

  useEffect(() => {
    const onPointerDown = (event) => {
      if (!wrapperRef.current?.contains(event.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, []);

  const setField = (field, value) => onChange({ ...filters, [field]: value });

  return (
    <div className="no-print border-b border-black/20 bg-white px-2 py-2 text-[12px] text-black">
      <table className="w-full max-w-2xl border-collapse border border-black/30">
        <thead>
          <tr className="bg-slate-100">
            <th className="w-1/2 border border-black/30 px-2 py-1 text-left font-bold uppercase">Loan Number *</th>
            <th className="w-1/2 border border-black/30 px-2 py-1 text-left font-bold uppercase">As On Date *</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td ref={wrapperRef} className="relative border border-black/30">
              <input
                className={inputClass}
                value={filters.loanNumber}
                onFocus={() => setOpen(true)}
                onChange={(event) => {
                  setOpen(true);
                  setField('loanNumber', event.target.value);
                }}
              />
              {open && options.length > 0 && (
                <div className="absolute left-0 right-0 top-8 z-40 max-h-60 overflow-auto border border-blue-900 bg-white shadow-xl">
                  {options.map((contract) => {
                    const loanNumber = contract.contractNumber || contract.legacyContractNumber || contract.contractId || '';
                    return (
                      <button
                        key={`${loanNumber}-${contract.contractId}`}
                        type="button"
                        className="block w-full border-b border-black/10 px-2 py-1 text-left hover:bg-blue-50"
                        onClick={() => {
                          setField('loanNumber', loanNumber);
                          setOpen(false);
                        }}
                      >
                        <div className="font-bold">{loanNumber}</div>
                        <div className="text-[11px] text-black/60">{contract.borrowerName || contract.customerName || ''}</div>
                      </button>
                    );
                  })}
                </div>
              )}
              {lookupLoading && <span className="absolute right-2 top-2 text-[10px] font-bold text-blue-700">...</span>}
            </td>
            <td className="border border-black/30">
              <input className={inputClass} type="date" value={filters.asOnDate} onChange={(event) => setField('asOnDate', event.target.value)} />
            </td>
          </tr>
        </tbody>
      </table>
      <div className="mt-2 flex flex-wrap gap-2">
        <button type="button" disabled={loading} onClick={onGenerate} className="inline-flex h-8 items-center gap-1 border border-[#0052CC] bg-[#0052CC] px-3 text-[12px] font-bold uppercase text-white disabled:opacity-50">
          <Search size={14} /> Generate
        </button>
        <button type="button" disabled={loading} onClick={onReset} className="inline-flex h-8 items-center gap-1 border border-black/30 bg-white px-3 text-[12px] font-bold uppercase text-black">
          <RotateCcw size={14} /> Reset
        </button>
        <button type="button" disabled={loading} onClick={onPrint} className="inline-flex h-8 items-center gap-1 border border-black/30 bg-white px-3 text-[12px] font-bold uppercase text-black">
          <Printer size={14} /> Print
        </button>
        <button type="button" disabled={loading} onClick={onExportExcel} className="inline-flex h-8 items-center gap-1 border border-black/30 bg-white px-3 text-[12px] font-bold uppercase text-black">
          <FileSpreadsheet size={14} /> Export Excel
        </button>
      </div>
    </div>
  );
}
