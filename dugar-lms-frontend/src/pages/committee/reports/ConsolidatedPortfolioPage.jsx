import { RotateCcw, Search, Sheet } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { fetchConsolidatedPortfolio } from '../../../services/agingAnalysisService';
import { fetchContractAreaOptions } from '../../../services/contractsService';

const today = new Date().toISOString().slice(0, 10);
const defaultFilters = { asOnDate: today, areaCode: '' };
const fieldClass = 'h-8 w-full border-0 bg-white px-2 text-[12px] text-black outline-none focus:bg-blue-50';

const commonColumns = [
  ['noOfAccounts', 'No. of Accounts', 'right', 'count'],
  ['principalOutstandingCr', 'Principal O/s (Rs Cr)', 'right', 'crore'],
  ['standardCr', 'Standard', 'right', 'crore'],
  ['days0To30Cr', '0-30', 'right', 'crore'],
  ['days31To60Cr', '31-60', 'right', 'crore'],
  ['days61To90Cr', '61-90', 'right', 'crore'],
  ['days91To180Cr', '91-180', 'right', 'crore'],
  ['days181To365Cr', '181-365', 'right', 'crore'],
  ['daysAbove365Cr', '>365', 'right', 'crore'],
];

const reportSections = [
  { key: 'tenorWise', title: 'TENOR-WISE PORTFOLIO', label: 'Tenor-wise', columns: [['label', 'Tenor', 'left'], ...commonColumns] },
  { key: 'ticketSizeWise', title: 'LOAN TICKET SIZE-WISE PORTFOLIO', label: 'Loan Ticket Size-wise', columns: [['label', 'Loan Ticket Size', 'left'], ...commonColumns] },
  { key: 'stateWise', title: 'STATE-WISE PORTFOLIO', label: 'State-wise', columns: [['stateName', 'State', 'left'], ...commonColumns] },
];

function formatCount(value) {
  if (value === null || value === undefined || value === '') return '';
  return Number(value || 0).toLocaleString('en-IN');
}

function formatCrore(value) {
  if (value === null || value === undefined || value === '') return '';
  return Number(value || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function displayValue(row, key, format) {
  if (format === 'count') return formatCount(row[key]);
  if (format === 'crore') return formatCrore(row[key]);
  if (key === 'stateName') return row[key] || 'UNASSIGNED';
  return row[key] || '';
}

function excelCell(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function areaLabel(area) {
  if (!area?.areaCode) return '';
  return area.areaName ? `${area.areaCode} - ${area.areaName}` : area.areaCode;
}

function totalRow(rows, labelKey) {
  if (!rows.length) return null;
  return {
    [labelKey]: 'TOTAL',
    noOfAccounts: rows.reduce((sum, row) => sum + Number(row.noOfAccounts || 0), 0),
    principalOutstandingCr: rows.reduce((sum, row) => sum + Number(row.principalOutstandingCr || 0), 0),
    standardCr: rows.reduce((sum, row) => sum + Number(row.standardCr || 0), 0),
    days0To30Cr: rows.reduce((sum, row) => sum + Number(row.days0To30Cr || 0), 0),
    days31To60Cr: rows.reduce((sum, row) => sum + Number(row.days31To60Cr || 0), 0),
    days61To90Cr: rows.reduce((sum, row) => sum + Number(row.days61To90Cr || 0), 0),
    days91To180Cr: rows.reduce((sum, row) => sum + Number(row.days91To180Cr || 0), 0),
    days181To365Cr: rows.reduce((sum, row) => sum + Number(row.days181To365Cr || 0), 0),
    daysAbove365Cr: rows.reduce((sum, row) => sum + Number(row.daysAbove365Cr || 0), 0),
  };
}

function AreaInput({ filters, onChange }) {
  const [areaOpen, setAreaOpen] = useState(false);
  const [areaOptions, setAreaOptions] = useState([]);
  const [selectedArea, setSelectedArea] = useState(null);
  const [areaLoading, setAreaLoading] = useState(false);
  const areaRef = useRef(null);
  const setField = (field, value) => onChange({ ...filters, [field]: value });
  const areaInputValue = selectedArea?.areaCode === filters.areaCode ? areaLabel(selectedArea) : filters.areaCode;

  useEffect(() => {
    if (!areaOpen) return undefined;
    let active = true;
    const timer = window.setTimeout(async () => {
      setAreaLoading(true);
      try {
        const options = await fetchContractAreaOptions({ keyword: filters.areaCode, limit: 20 });
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
    <td ref={areaRef} className="relative border border-black/30">
      <input
        className={fieldClass}
        value={areaInputValue}
        onFocus={() => setAreaOpen(true)}
        onChange={(event) => {
          setSelectedArea(null);
          setAreaOpen(true);
          setField('areaCode', event.target.value);
        }}
        placeholder="All Areas"
      />
      {areaOpen && (
        <div className="absolute left-0 right-0 top-8 z-40 max-h-56 overflow-auto border border-blue-900 bg-white shadow-xl">
          <button
            type="button"
            className="block w-full border-b border-black/10 px-2 py-1 text-left font-bold hover:bg-blue-50"
            onMouseDown={(event) => {
              event.preventDefault();
              setSelectedArea(null);
              setField('areaCode', '');
              setAreaOpen(false);
            }}
          >
            All Areas
          </button>
          {areaLoading && <div className="px-2 py-2 text-[11px] font-bold text-blue-700">Loading areas...</div>}
          {!areaLoading && areaOptions.length === 0 && <div className="px-2 py-2 text-[11px] font-bold text-black/60">No areas found</div>}
          {!areaLoading && areaOptions.map((area) => (
            <button
              key={area.areaCode}
              type="button"
              className="block w-full border-b border-black/10 px-2 py-1 text-left font-bold hover:bg-blue-50"
              onMouseDown={(event) => {
                event.preventDefault();
                setSelectedArea(area);
                setField('areaCode', area.areaCode);
                setAreaOpen(false);
              }}
            >
              {areaLabel(area)}
            </button>
          ))}
        </div>
      )}
    </td>
  );
}

function SummaryStrip({ rows }) {
  const total = totalRow(rows, 'label');
  const par = total
    ? Number(total.days0To30Cr || 0) + Number(total.days31To60Cr || 0) + Number(total.days61To90Cr || 0) + Number(total.days91To180Cr || 0) + Number(total.days181To365Cr || 0) + Number(total.daysAbove365Cr || 0)
    : 0;
  const items = [
    ['Total Accounts', formatCount(total?.noOfAccounts)],
    ['Principal O/s (Rs Cr)', formatCrore(total?.principalOutstandingCr)],
    ['Standard (Rs Cr)', formatCrore(total?.standardCr)],
    ['PAR > 0 (Rs Cr)', formatCrore(par)],
  ];

  return (
    <div className="no-print grid grid-cols-2 border-b border-black/20 bg-[#f8fafc] text-[12px] md:grid-cols-4">
      {items.map(([label, value]) => (
        <div key={label} className="border-r border-black/10 px-3 py-2">
          <div className="font-bold uppercase text-black/60">{label}</div>
          <div className="text-[14px] font-black text-black">{value}</div>
        </div>
      ))}
    </div>
  );
}

function PortfolioTable({ columns, rows }) {
  const labelKey = columns[0][0];
  const footer = totalRow(rows, labelKey);
  return (
    <div className="w-full overflow-x-auto bg-white">
      <table className="w-full min-w-[1050px] border-collapse text-[12px] text-black">
        <thead className="sticky top-0 z-20 bg-[#e8edf5] font-bold">
          <tr>
            {columns.map(([key, label, align]) => (
              <th key={key} className={`border border-black/40 px-2 py-1 ${align === 'right' ? 'text-right' : 'text-left'}`}>{label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={`${row.label || row.stateName}-${index}`} className={index % 2 === 0 ? 'bg-white' : 'bg-slate-50'}>
              {columns.map(([key, , align, format]) => (
                <td key={key} className={`border border-black/30 px-2 py-1 ${align === 'right' ? 'text-right' : 'text-left'}`}>{displayValue(row, key, format)}</td>
              ))}
            </tr>
          ))}
          {!rows.length && (
            <tr>
              <td colSpan={columns.length} className="border border-black/30 px-3 py-6 text-center font-bold uppercase text-black/50">No records found</td>
            </tr>
          )}
        </tbody>
        {footer && (
          <tfoot className="sticky bottom-0 z-10 bg-[#e8edf5] font-black">
            <tr>
              {columns.map(([key, , align, format]) => (
                <td key={key} className={`border border-black/40 px-2 py-1 ${align === 'right' ? 'text-right' : 'text-left'}`}>{displayValue(footer, key, format)}</td>
              ))}
            </tr>
          </tfoot>
        )}
      </table>
    </div>
  );
}

function PortfolioSection({ section, rows }) {
  return (
    <section className="mb-5 w-full">
      <div className="border-y border-black/30 bg-[#f8fafc] px-2 py-1">
        <h2 className="text-[13px] font-black uppercase text-[#0052CC]">{section.title}</h2>
      </div>
      <PortfolioTable columns={section.columns} rows={rows} />
    </section>
  );
}

export default function ConsolidatedPortfolioPage() {
  const [filters, setFilters] = useState(defaultFilters);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const generate = async () => {
    if (!filters.asOnDate) {
      setMessage('As On Date is required.');
      return;
    }
    setLoading(true);
    setMessage('');
    try {
      const response = await fetchConsolidatedPortfolio(filters);
      setData(response);
    } catch (error) {
      setData(null);
      setMessage(error?.response?.data?.message || error?.message || 'Unable to load Consolidated Portfolio.');
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setFilters(defaultFilters);
    setData(null);
    setMessage('');
  };

  const exportExcel = () => {
    if (!data) {
      setMessage('Generate the report before exporting.');
      return;
    }
    const tableHtml = (caption, columns, rows) => {
      const footer = totalRow(rows, columns[0][0]);
      return `
        <table border="1">
          <tr><th colspan="${columns.length}">${excelCell(caption)}</th></tr>
          <tr><th colspan="${columns.length}">Amounts in Rs Crores</th></tr>
          <tr>${columns.map(([, label]) => `<th>${excelCell(label)}</th>`).join('')}</tr>
          ${rows.map((row) => `<tr>${columns.map(([key, , , format]) => `<td>${excelCell(displayValue(row, key, format))}</td>`).join('')}</tr>`).join('')}
          ${footer ? `<tr>${columns.map(([key, , , format]) => `<td><strong>${excelCell(displayValue(footer, key, format))}</strong></td>`).join('')}</tr>` : ''}
        </table>
      `;
    };
    const html = `
      <html>
        <head><meta charset="utf-8" /></head>
        <body>
          ${reportSections.map((section) => tableHtml(`${section.label} Consolidated Portfolio as on ${data.asOnDate}`, section.columns, data[section.key] || [])).join('<br/>')}
        </body>
      </html>
    `;
    const blob = new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `consolidated-portfolio-${data.asOnDate || filters.asOnDate}.xls`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="flex h-full min-h-0 flex-col bg-white font-['Segoe_UI',Arial,sans-serif] text-[12px] text-black">
      <div className="no-print border-b border-black/20 bg-[#f8fafc] px-2 py-1">
        <div className="text-[16px] font-bold uppercase text-[#0052CC]">Consolidated Portfolio</div>
        <div className="text-[11px] font-bold text-black/60">Amounts in Rs Crores</div>
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
                <input className={fieldClass} type="date" value={filters.asOnDate} onChange={(event) => setFilters({ ...filters, asOnDate: event.target.value })} />
              </td>
              <AreaInput filters={filters} onChange={setFilters} />
            </tr>
          </tbody>
        </table>
        <div className="mt-2 flex flex-wrap gap-2">
          <button type="button" disabled={loading} onClick={generate} className="inline-flex h-8 items-center gap-1 border border-[#0052CC] bg-[#0052CC] px-3 text-[12px] font-bold uppercase text-white disabled:opacity-50">
            <Search size={14} /> Generate Report
          </button>
          <button type="button" disabled={loading} onClick={reset} className="inline-flex h-8 items-center gap-1 border border-black/30 bg-white px-3 text-[12px] font-bold uppercase text-black">
            <RotateCcw size={14} /> Reset
          </button>
          <button type="button" disabled={loading} onClick={exportExcel} className="inline-flex h-8 items-center gap-1 border border-black/30 bg-white px-3 text-[12px] font-bold uppercase text-black disabled:opacity-50">
            <Sheet size={14} /> Export Excel
          </button>
        </div>
      </div>
      {message && <div className="no-print border-b border-red-200 bg-red-50 px-2 py-1 font-bold text-red-700">{message}</div>}
      <SummaryStrip rows={data?.tenorWise || []} />
      <div className="min-h-0 flex-1 overflow-y-auto bg-white px-2 py-3">
        {reportSections.map((section) => (
          <PortfolioSection key={section.key} section={section} rows={data?.[section.key] || []} />
        ))}
      </div>
      {loading && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/35 px-4">
          <div className="w-full max-w-sm border border-blue-900 bg-white px-5 py-4 text-center shadow-2xl">
            <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-blue-200 border-t-[#0052CC]" />
            <div className="mt-3 text-[14px] font-black uppercase text-[#0052CC]">Loading report</div>
            <div className="mt-1 text-[12px] font-bold text-black">Please wait while portfolio buckets are loaded.</div>
          </div>
        </div>
      )}
    </div>
  );
}
