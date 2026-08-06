import { Printer, RotateCcw, Search, Sheet } from 'lucide-react';

const fieldClass = 'h-8 w-full border-0 bg-white px-2 text-[12px] text-black outline-none focus:bg-blue-50';

export default function DemandListFilters({
  filters,
  loading,
  onChange,
  onExportExcel,
  onGenerate,
  onPrint,
  onReset,
}) {
  const setField = (field, value) => onChange({ ...filters, [field]: value });

  return (
    <div className="no-print border-b border-black/20 bg-white px-2 py-2 text-[12px] text-black">
      <table className="w-full max-w-3xl border-collapse border border-black/30">
        <thead>
          <tr className="bg-slate-100">
            <th className="w-1/3 border border-black/30 px-2 py-1 text-left font-bold uppercase">As On Date</th>
            <th className="w-1/3 border border-black/30 px-2 py-1 text-left font-bold uppercase">Contract No</th>
            <th className="w-1/3 border border-black/30 px-2 py-1 text-left font-bold uppercase">No. of Overdues</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td className="border border-black/30">
              <input className={fieldClass} type="date" value={filters.asOnDate} onChange={(event) => setField('asOnDate', event.target.value)} />
            </td>
            <td className="border border-black/30">
              <input className={fieldClass} value={filters.contractNumber} onChange={(event) => setField('contractNumber', event.target.value)} />
            </td>
            <td className="border border-black/30">
              <input className={fieldClass} min="0" type="number" value={filters.overdueInstallmentCount} onChange={(event) => setField('overdueInstallmentCount', event.target.value)} />
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
        <button type="button" disabled={loading} onClick={onExportExcel} className="inline-flex h-8 items-center gap-1 border border-black/30 bg-white px-3 text-[12px] font-bold uppercase text-black disabled:opacity-50">
          <Sheet size={14} /> Export Excel
        </button>
      </div>
    </div>
  );
}
