import { Printer, RotateCcw, Search, Sheet } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import AgingDrilldownDrawer from '../../../components/aging-analysis/AgingDrilldownDrawer';
import { fetchAgingAnalysis, fetchAgingContractDetail, fetchAgingContractEmis, fetchAgingContractReceipts, fetchAgingContracts, fetchAgingRawVoucher } from '../../../services/agingAnalysisService';
import { fetchContractAreaOptions } from '../../../services/contractsService';

const today = new Date().toISOString().slice(0, 10);
const defaultFilters = { asOnDate: today, areaCode: '' };
const fieldClass = 'h-8 w-full border-0 bg-white px-2 text-[12px] text-black outline-none focus:bg-blue-50';

const branchColumns = [
  ['area', 'Area', 'left'],
  ['noOfAccounts', 'No.A/Cs', 'right'],
  ['aum', 'AUM (Rs Lakhs)', 'right', 'lakhs'],
  ['current', 'Current (Rs Lakhs)', 'right', 'lakhs'],
  ['bucket1To30', '1--30 (Rs Lakhs)', 'right', 'lakhs'],
  ['bucket31To60', '31--60 (Rs Lakhs)', 'right', 'lakhs'],
  ['bucket61To90', '61--90 (Rs Lakhs)', 'right', 'lakhs'],
  ['bucket91To120', '91--120 (Rs Lakhs)', 'right', 'lakhs'],
  ['bucket121To150', '121--150 (Rs Lakhs)', 'right', 'lakhs'],
  ['bucket151To180', '151--180 (Rs Lakhs)', 'right', 'lakhs'],
  ['bucketAbove180', '180 & above (Rs Lakhs)', 'right', 'lakhs'],
  ['total', 'Total Outstanding (Rs Lakhs)', 'right', 'lakhs'],
];

const consolidatedColumns = [
  ['label', 'Ageing Bucket', 'left'],
  ['noOfAccounts', 'No.Of A/cs', 'right'],
  ['principalOutstanding', 'O/S Principal (Rs Lakhs)', 'right', 'lakhs'],
  ['interestOutstanding', 'O/S Interest (Rs Lakhs)', 'right', 'lakhs'],
  ['total', 'Total (Rs Lakhs)', 'right', 'lakhs'],
  ['portfolioPercent', '% of portfolio', 'right'],
];

function formatMoney(value) {
  if (value === null || value === undefined || value === '') return '';
  return Number(value).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatLakhs(value) {
  if (value === null || value === undefined || value === '') return '';
  return (Number(value || 0) / 100000).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatPercent(value) {
  if (value === null || value === undefined || value === '') return '';
  return `${Number(value).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
}

function excelCell(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function errorMessage(error, fallback) {
  if (error?.code === 'ECONNABORTED') {
    return 'Aging Analysis is taking too long. Select a smaller Area and try again.';
  }
  return error?.response?.data?.message || error?.message || fallback;
}

function displayValue(row, key, format) {
  if (key === 'area') return areaDisplay(row);
  if (key === 'portfolioPercent') return formatPercent(row[key]);
  if (format === 'lakhs') return formatLakhs(row[key]);
  if (key !== 'area' && key !== 'label' && key !== 'noOfAccounts') return formatMoney(row[key]);
  if (key === 'noOfAccounts') {
    if (row[key] === null || row[key] === undefined || row[key] === '') return '';
    return Number(row[key] || 0).toLocaleString('en-IN');
  }
  return row[key] || '';
}

function areaLabel(area) {
  if (!area?.areaCode) return '';
  return area.areaName ? `${area.areaCode} - ${area.areaName}` : area.areaCode;
}

function areaDisplay(row) {
  const areaCode = row?.areaCode || row?.area || row?.branchCode || row?.fieldCode || '';
  const areaName = row?.areaName || row?.branchName || row?.fieldName || '';
  return areaName ? `${areaCode} - ${areaName}` : areaCode;
}

const branchBucketByKey = {
  area: 'all',
  noOfAccounts: 'all',
  current: 'current',
  bucket1To30: '1_30',
  bucket31To60: '31_60',
  bucket61To90: '61_90',
  bucket91To120: '91_120',
  bucket121To150: '121_150',
  bucket151To180: '151_180',
  bucketAbove180: 'above_180',
  total: 'all',
};

function AgingFilters({ filters, loading, onChange, onExportExcel, onGenerate, onPrint, onReset }) {
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
    <div className="no-print border-b border-black/20 bg-white px-2 py-2 text-[12px] text-black">
      <table className="w-full max-w-2xl border-collapse border border-black/30">
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
                value={areaInputValue}
                onFocus={() => setAreaOpen(true)}
                onChange={(event) => {
                  setSelectedArea(null);
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

function ReportTable({ columns, rows, footerRow, onCellClick }) {
  return (
    <div className="min-h-0 flex-1 overflow-auto bg-white">
      <table className="min-w-[900px] border-collapse text-[12px] text-black">
        <thead className="sticky top-0 z-20 bg-[#e8edf5] text-[12px] font-bold">
          <tr>
            {columns.map(([key, label, align]) => (
              <th key={key} className={`border border-black/40 px-2 py-1 ${align === 'right' ? 'text-right' : 'text-left'}`}>{label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={`${row.areaCode || row.area || row.bucket || row.label}-${index}`} className={`${index % 2 === 0 ? 'bg-white' : 'bg-slate-50'} hover:bg-blue-50`}>
              {columns.map(([key, , align, format]) => (
                <td key={key} className={`border border-black/30 px-2 py-1 ${align === 'right' ? 'text-right' : 'text-left'}`}>
                  {onCellClick && branchBucketByKey[key] ? (
                    <button
                      type="button"
                      className="w-full text-inherit underline decoration-dotted underline-offset-2 hover:text-[#0052CC]"
                      onClick={() => onCellClick(row, key)}
                    >
                      {displayValue(row, key, format)}
                    </button>
                  ) : displayValue(row, key, format)}
                </td>
              ))}
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={columns.length} className="border border-black/30 px-3 py-6 text-center font-bold uppercase text-black/50">No records found</td>
            </tr>
          )}
        </tbody>
        {footerRow && rows.length > 0 && (
          <tfoot className="sticky bottom-0 z-10 bg-[#e8edf5] font-black">
            <tr>
              {columns.map(([key, , align, format]) => (
                <td key={key} className={`border border-black/40 px-2 py-1 ${align === 'right' ? 'text-right' : 'text-left'}`}>
                  {displayValue(footerRow, key, format)}
                </td>
              ))}
            </tr>
          </tfoot>
        )}
      </table>
    </div>
  );
}

function SummaryStrip({ summary }) {
  const items = [
    ['No. of A/cs', Number(summary?.noOfAccounts || 0).toLocaleString('en-IN')],
    ['AUM (Rs Lakhs)', formatLakhs(summary?.aum)],
    ['Current (Rs Lakhs)', formatLakhs(summary?.current)],
    ['Total Overdue (Rs Lakhs)', formatLakhs(summary?.totalOverdue)],
    ['Total (Rs Lakhs)', formatLakhs(summary?.total)],
  ];

  return (
    <div className="no-print grid grid-cols-2 border-b border-black/20 bg-[#f8fafc] text-[12px] sm:grid-cols-5">
      {items.map(([label, value]) => (
        <div key={label} className="border-r border-black/10 px-3 py-2">
          <div className="font-bold uppercase text-black/60">{label}</div>
          <div className="text-[14px] font-black text-black">{value}</div>
        </div>
      ))}
    </div>
  );
}

function consolidatedTotalRow(rows) {
  if (!rows.length) return null;
  const total = rows.reduce((sum, row) => sum + Number(row.total || 0), 0);
  const hasAccountCounts = rows.some((row) => row.noOfAccounts !== null && row.noOfAccounts !== undefined && row.noOfAccounts !== '');
  return {
    label: 'Total',
    noOfAccounts: hasAccountCounts ? rows.reduce((sum, row) => sum + Number(row.noOfAccounts || 0), 0) : null,
    principalOutstanding: rows.reduce((total, row) => total + Number(row.principalOutstanding || 0), 0),
    interestOutstanding: rows.reduce((total, row) => total + Number(row.interestOutstanding || 0), 0),
    total,
    portfolioPercent: total > 0 ? 100 : 0,
  };
}

function branchTotalRow(rows, summary) {
  if (!rows.length) return null;
  return {
    area: 'TOTAL',
    noOfAccounts: summary?.noOfAccounts ?? rows.reduce((sum, row) => sum + Number(row.noOfAccounts || 0), 0),
    aum: summary?.aum ?? rows.reduce((total, row) => total + Number(row.aum || 0), 0),
    current: summary?.current ?? rows.reduce((total, row) => total + Number(row.current || 0), 0),
    bucket1To30: rows.reduce((total, row) => total + Number(row.bucket1To30 || 0), 0),
    bucket31To60: rows.reduce((total, row) => total + Number(row.bucket31To60 || 0), 0),
    bucket61To90: rows.reduce((total, row) => total + Number(row.bucket61To90 || 0), 0),
    bucket91To120: rows.reduce((total, row) => total + Number(row.bucket91To120 || 0), 0),
    bucket121To150: rows.reduce((total, row) => total + Number(row.bucket121To150 || 0), 0),
    bucket151To180: rows.reduce((total, row) => total + Number(row.bucket151To180 || 0), 0),
    bucketAbove180: rows.reduce((total, row) => total + Number(row.bucketAbove180 || 0), 0),
    total: summary?.total ?? rows.reduce((total, row) => total + Number(row.total || 0), 0),
  };
}

export default function AgingAnalysisPage({ initialTab = 'branch' }) {
  const [filters, setFilters] = useState(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState(null);
  const [data, setData] = useState(null);
  const [activeTab, setActiveTab] = useState(initialTab === 'consolidated' ? 'consolidated' : 'branch');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [drilldown, setDrilldown] = useState({ open: false, loading: false, contracts: [], detail: null, emis: [], receipts: [] });

  useEffect(() => {
    setActiveTab(initialTab === 'consolidated' ? 'consolidated' : 'branch');
  }, [initialTab]);

  const generate = async () => {
    if (!filters.asOnDate) {
      setMessage('As On Date is required.');
      return;
    }
    setLoading(true);
    setMessage('');
    try {
      const response = await fetchAgingAnalysis(filters);
      setAppliedFilters(filters);
      setData(response);
    } catch (error) {
      setData(null);
      setMessage(errorMessage(error, 'Unable to load Aging Analysis.'));
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setFilters(defaultFilters);
    setAppliedFilters(null);
    setData(null);
    setMessage('');
    setActiveTab('branch');
    setDrilldown({ open: false, loading: false, contracts: [], detail: null, emis: [], receipts: [] });
  };

  const openBranchDrilldown = async (row, key) => {
    const bucket = branchBucketByKey[key] || 'all';
    const drilldownAreaCode = row.areaCode || row.area || filters.areaCode;
    const title = `Contracts - ${areaDisplay(row) || filters.areaCode}`;
    const nextState = { open: true, loading: true, title, bucketLabel: key === 'area' ? 'All' : branchColumns.find(([columnKey]) => columnKey === key)?.[1], contracts: [], detail: null, emis: [], receipts: [], message: '', rawVoucher: null, rawVoucherKey: null };
    setDrilldown(nextState);
    try {
      const contracts = await fetchAgingContracts({ areaCode: drilldownAreaCode, bucket, asOnDate: titleDate });
      setDrilldown({ ...nextState, loading: false, contracts });
    } catch (error) {
      setDrilldown({ ...nextState, loading: false, message: error?.response?.data?.message || error?.message || 'Unable to load contracts.' });
    }
  };

  const selectDrilldownContract = async (contractId) => {
    setDrilldown((current) => ({ ...current, loading: true, message: '', rawVoucher: null, rawVoucherKey: null, rawVoucherMessage: '', rawVoucherLoading: false }));
    try {
      const [detail, emis, receipts] = await Promise.all([
        fetchAgingContractDetail(contractId, { asOnDate: titleDate }),
        fetchAgingContractEmis(contractId, { asOnDate: titleDate }),
        fetchAgingContractReceipts(contractId, { asOnDate: titleDate }),
      ]);
      setDrilldown((current) => ({ ...current, loading: false, detail, emis, receipts }));
    } catch (error) {
      setDrilldown((current) => ({ ...current, loading: false, message: error?.response?.data?.message || error?.message || 'Unable to load contract details.' }));
    }
  };

  const selectDrilldownReceipt = async (receipt) => {
    const contractId = drilldown.detail?.contractId;
    if (!contractId || !receipt?.voucherNumber) return;
    const rawVoucherKey = `${receipt.voucherType || ''}:${receipt.voucherNumber || ''}`;
    setDrilldown((current) => ({ ...current, rawVoucherLoading: true, rawVoucherMessage: '', rawVoucherKey, rawVoucher: null }));
    try {
      const rawVoucher = await fetchAgingRawVoucher(contractId, {
        voucherNumber: receipt.voucherNumber,
        voucherType: receipt.voucherType,
      });
      setDrilldown((current) => ({ ...current, rawVoucherLoading: false, rawVoucher }));
    } catch (error) {
      setDrilldown((current) => ({ ...current, rawVoucherLoading: false, rawVoucherMessage: error?.response?.data?.message || error?.message || 'Unable to load raw voucher details.' }));
    }
  };

  const print = async () => {
    if (data) {
      window.setTimeout(() => window.print(), 150);
      return;
    }
    if (!filters.asOnDate) {
      setMessage('As On Date is required.');
      return;
    }
    setLoading(true);
    setMessage('');
    try {
      const response = await fetchAgingAnalysis(filters);
      setAppliedFilters(filters);
      setData(response);
      window.setTimeout(() => window.print(), 150);
    } catch (error) {
      setMessage(errorMessage(error, 'Unable to print Aging Analysis.'));
    } finally {
      setLoading(false);
    }
  };

  const exportExcel = async () => {
    let report = data;
    if (!report) {
      if (!filters.asOnDate) {
        setMessage('As On Date is required.');
        return;
      }
      setLoading(true);
      try {
        report = await fetchAgingAnalysis(filters);
        setAppliedFilters(filters);
        setData(report);
      } catch (error) {
        setMessage(errorMessage(error, 'Unable to export Aging Analysis.'));
        return;
      } finally {
        setLoading(false);
      }
    }

    const title = `Aging Analysis as on ${report.asOnDate || appliedFilters?.asOnDate || filters.asOnDate}`;
    const tableHtml = (caption, columns, rows, footerRow = null) => `
      <table border="1">
        <tr><th colspan="${columns.length}">${excelCell(caption)}</th></tr>
        <tr>${columns.map(([, label]) => `<th>${excelCell(label)}</th>`).join('')}</tr>
        ${rows.map((row) => `
          <tr>${columns.map(([key, , , format]) => `<td>${excelCell(displayValue(row, key, format))}</td>`).join('')}</tr>
        `).join('')}
        ${footerRow ? `<tr>${columns.map(([key, , , format]) => `<td><strong>${excelCell(displayValue(footerRow, key, format))}</strong></td>`).join('')}</tr>` : ''}
      </table>
    `;
    const html = `
      <html>
        <head><meta charset="utf-8" /></head>
        <body>
          ${tableHtml(`Branch Wise ${title}`, branchColumns, report.branchWise || [], branchTotalRow(report.branchWise || [], report.summary))}
          <br/>
          ${tableHtml(`Consolidated ${title}`, consolidatedColumns, report.consolidated || [], consolidatedTotalRow(report.consolidated || []))}
        </body>
      </html>
    `;
    const blob = new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `aging-analysis-${report.asOnDate || filters.asOnDate}.xls`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const branchRows = data?.branchWise || [];
  const consolidatedRows = data?.consolidated || [];
  const branchFooter = branchTotalRow(branchRows, data?.summary);
  const consolidatedFooter = consolidatedTotalRow(consolidatedRows);
  const titleDate = data?.asOnDate || appliedFilters?.asOnDate || filters.asOnDate;

  return (
    <div className="flex h-full min-h-0 flex-col bg-white font-['Segoe_UI',Arial,sans-serif] text-[12px] text-black">
      <style>{`
        @page { size: A4 landscape; margin: 6mm; }
        .aging-print-root { display: none; }
        @media print {
          html, body { margin: 0 !important; padding: 0 !important; background: #fff !important; }
          body * { visibility: hidden !important; }
          .no-print { display: none !important; }
          .aging-print-root, .aging-print-root * { visibility: visible !important; }
          .aging-print-root { display: block !important; position: absolute; left: 0; top: 0; width: 100%; color: #000; font-family: "Segoe UI", Arial, sans-serif; }
          .aging-print-title { margin: 0 0 3mm; font-size: 12px; font-weight: 700; text-align: center; }
          .aging-print-table { width: 100%; border-collapse: collapse; font-size: 8px; color: #000; margin-bottom: 6mm; }
          .aging-print-table th, .aging-print-table td { border: 0.2mm solid #999; padding: 1mm 0.8mm; vertical-align: top; }
          .aging-print-table th { text-align: left; font-weight: 700; }
          .aging-print-table thead { display: table-header-group; }
          .aging-print-table tbody tr { break-inside: avoid; page-break-inside: avoid; }
          .right { text-align: right; white-space: nowrap; }
        }
      `}</style>
      <div className="no-print border-b border-black/20 bg-[#f8fafc] px-2 py-1">
        <div className="text-[16px] font-bold uppercase text-[#0052CC]">Aging Analysis</div>
      </div>
      <AgingFilters filters={filters} loading={loading} onChange={setFilters} onExportExcel={exportExcel} onGenerate={generate} onPrint={print} onReset={reset} />
      {message && <div className="no-print border-b border-red-200 bg-red-50 px-2 py-1 font-bold text-red-700">{message}</div>}
      {data?.warnings?.length > 0 && <div className="no-print border-b border-amber-200 bg-amber-50 px-2 py-1 font-bold text-amber-800">{data.warnings.join(' | ')}</div>}
      <SummaryStrip summary={data?.summary} />
      <div className="no-print flex gap-1 border-b border-black/20 bg-white px-2 py-2">
        <button type="button" onClick={() => setActiveTab('branch')} className={`h-8 border px-3 text-[12px] font-bold uppercase ${activeTab === 'branch' ? 'border-[#0052CC] bg-[#0052CC] text-white' : 'border-black/30 bg-white text-black'}`}>Branch Wise</button>
        <button type="button" onClick={() => setActiveTab('consolidated')} className={`h-8 border px-3 text-[12px] font-bold uppercase ${activeTab === 'consolidated' ? 'border-[#0052CC] bg-[#0052CC] text-white' : 'border-black/30 bg-white text-black'}`}>Consolidated</button>
      </div>
      <div className="no-print flex min-h-0 flex-1">
        {activeTab === 'branch'
          ? <ReportTable columns={branchColumns} rows={branchRows} footerRow={branchFooter} onCellClick={openBranchDrilldown} />
          : <ReportTable columns={consolidatedColumns} rows={consolidatedRows} footerRow={consolidatedFooter} />}
      </div>
      <AgingDrilldownDrawer state={drilldown} onClose={() => setDrilldown({ open: false, loading: false, contracts: [], detail: null, emis: [], receipts: [] })} onSelectContract={selectDrilldownContract} onSelectReceipt={selectDrilldownReceipt} />
      <div className="aging-print-root">
        <div className="aging-print-title">Branch Wise Aging Analysis As on {titleDate}</div>
        <table className="aging-print-table">
          <thead><tr>{branchColumns.map(([, label, align]) => <th key={label} className={align === 'right' ? 'right' : ''}>{label}</th>)}</tr></thead>
          <tbody>
            {branchRows.map((row, index) => <tr key={`print-branch-${index}`}>{branchColumns.map(([key, , align, format]) => <td key={key} className={align === 'right' ? 'right' : ''}>{displayValue(row, key, format)}</td>)}</tr>)}
          </tbody>
          {branchFooter && (
            <tfoot>
              <tr>{branchColumns.map(([key, , align, format]) => <td key={key} className={align === 'right' ? 'right' : ''}><strong>{displayValue(branchFooter, key, format)}</strong></td>)}</tr>
            </tfoot>
          )}
        </table>
        <div className="aging-print-title">Consolidated Aging Analysis As on {titleDate}</div>
        <table className="aging-print-table">
          <thead><tr>{consolidatedColumns.map(([, label, align]) => <th key={label} className={align === 'right' ? 'right' : ''}>{label}</th>)}</tr></thead>
          <tbody>
            {consolidatedRows.map((row, index) => <tr key={`print-consolidated-${index}`}>{consolidatedColumns.map(([key, , align, format]) => <td key={key} className={align === 'right' ? 'right' : ''}>{displayValue(row, key, format)}</td>)}</tr>)}
          </tbody>
          {consolidatedFooter && (
            <tfoot>
              <tr>{consolidatedColumns.map(([key, , align, format]) => <td key={key} className={align === 'right' ? 'right' : ''}><strong>{displayValue(consolidatedFooter, key, format)}</strong></td>)}</tr>
            </tfoot>
          )}
        </table>
      </div>
      {loading && (
        <div className="no-print fixed inset-0 z-[100] flex items-center justify-center bg-black/35 px-4">
          <div className="w-full max-w-sm border border-blue-900 bg-white px-5 py-4 text-center shadow-2xl">
            <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-blue-200 border-t-[#0052CC]" />
            <div className="mt-3 text-[14px] font-black uppercase text-[#0052CC]">Loading report</div>
            <div className="mt-1 text-[12px] font-bold text-black">Please wait while aging buckets are calculated.</div>
          </div>
        </div>
      )}
    </div>
  );
}
