import { Printer, RotateCcw, Search, Sheet } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { fetchContractAreas } from '../../services/contractsService';

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
  const [areaOpen, setAreaOpen] = useState(false);
  const [areaOptions, setAreaOptions] = useState([]);
  const [areaLoading, setAreaLoading] = useState(false);
  const areaRef = useRef(null);
  const setField = (field, value) => onChange({ ...filters, [field]: value });

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

  return (
    <div className="no-print border-b border-black/20 bg-white px-2 py-2 text-[12px] text-black">
      <table className="w-full max-w-4xl border-collapse border border-black/30">
        <thead>
          <tr className="bg-slate-100">
            <th className="w-1/4 border border-black/30 px-2 py-1 text-left font-bold uppercase">As On Date</th>
            <th className="w-1/4 border border-black/30 px-2 py-1 text-left font-bold uppercase">Area</th>
            <th className="w-1/4 border border-black/30 px-2 py-1 text-left font-bold uppercase">Contract No</th>
            <th className="w-1/4 border border-black/30 px-2 py-1 text-left font-bold uppercase">No. of Overdues</th>
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
