import { useState } from 'react';
import DemandListDetailsDrawer from '../../../components/demand-list/DemandListDetailsDrawer';
import DemandListFilters from '../../../components/demand-list/DemandListFilters';
import { demandListColumns, lineValues } from '../../../components/demand-list/DemandListGrid';
import DemandListPrintView from '../../../components/demand-list/DemandListPrintView';
import DemandListSummary from '../../../components/demand-list/DemandListSummary';
import { fetchDemandList } from '../../../services/demandListService';

const today = new Date().toISOString().slice(0, 10);

const defaultFilters = {
  asOnDate: today,
  areaCode: '',
  contractNumber: '',
  overdueInstallmentCount: '',
};

function hasReportScope(filters) {
  return Boolean(filters?.areaCode?.trim() || filters?.contractNumber?.trim());
}

function demandListErrorMessage(error, fallback) {
  if (error?.response?.status === 401) {
    return 'Demand List API is still rejecting this report request. Please restart the API server so the latest report access change is active.';
  }
  return error?.response?.data?.message || error?.message || fallback;
}

function excelCell(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function firstValue(row, keys) {
  for (const key of keys) {
    if (row[key] !== null && row[key] !== undefined && row[key] !== '') return row[key];
  }
  return '';
}

function alignClass(align) {
  if (align === 'right') return 'text-right';
  if (align === 'center') return 'text-center';
  return 'text-left';
}

function headerAlignClass(align) {
  return align === 'right' ? 'text-right' : 'text-left';
}

function StackCell({ values, align = 'left' }) {
  return (
    <div className={`space-y-0.5 leading-4 ${alignClass(align)}`}>
      {values.map((value, index) => (
        <div key={index} className="min-h-4 whitespace-normal">
          {value}
        </div>
      ))}
    </div>
  );
}

function DemandListTable({ rows, onOpen }) {
  return (
    <div className="no-print min-h-0 flex-1 overflow-auto bg-white">
      <table className="min-w-[1250px] border-collapse text-[12px] text-black">
        <thead className="sticky top-0 z-20 bg-[#e8edf5] text-[12px] font-bold">
          <tr>
            {demandListColumns.map((column) => (
              <th key={column.key} className={`${column.width} border border-black/40 px-2 py-1 align-top ${headerAlignClass(column.align)}`}>
                {column.label.map((line) => <div key={line}>{line}</div>)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => {
            const displayRow = { ...row, serialNumber: index + 1 };
            return (
              <tr key={row.contractId} onDoubleClick={() => onOpen(row)} className={`${index % 2 === 0 ? 'bg-white' : 'bg-slate-50'} hover:bg-blue-50`}>
                {demandListColumns.map((column) => (
                  <td key={column.key} className={`border border-black/30 px-2 py-1 align-top ${alignClass(column.align)}`}>
                    <StackCell values={lineValues(displayRow, column.key)} align={column.align || 'left'} />
                  </td>
                ))}
              </tr>
            );
          })}
          {rows.length === 0 && (
            <tr>
              <td colSpan={demandListColumns.length} className="border border-black/30 px-3 py-6 text-center font-bold uppercase text-black/50">No records found</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export default function DemandListPage() {
  const [filters, setFilters] = useState(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState(null);
  const [rows, setRows] = useState([]);
  const [summary, setSummary] = useState(null);
  const [warnings, setWarnings] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingMessage, setLoadingMessage] = useState('');
  const [message, setMessage] = useState('');
  const [selectedRow, setSelectedRow] = useState(null);
  const [printData, setPrintData] = useState(null);

  const generate = async () => {
    if (!filters.asOnDate) {
      setMessage('As On Date is required.');
      return;
    }
    if (!hasReportScope(filters)) {
      setMessage('Select an Area or enter Contract No before generating.');
      return;
    }
    setLoading(true);
    setLoadingMessage('Generating Demand List from the ageing procedure.');
    setMessage('');
    try {
      const data = await fetchDemandList(filters);
      setRows(data?.rows?.content || []);
      setSummary(data?.summary || null);
      setWarnings(data?.warnings || []);
      setAppliedFilters(filters);
      setPrintData(null);
    } catch (error) {
      setRows([]);
      setSummary(null);
      setWarnings([]);
      setAppliedFilters(null);
      setMessage(demandListErrorMessage(error, 'Unable to load Demand List.'));
    } finally {
      setLoading(false);
      setLoadingMessage('');
    }
  };

  const reset = () => {
    setFilters(defaultFilters);
    setAppliedFilters(null);
    setMessage('');
    setRows([]);
    setSummary(null);
    setWarnings([]);
    setPrintData(null);
  };

  const currentReport = () => {
    if (!appliedFilters) {
      setMessage('Generate the Demand List before printing.');
      return null;
    }
    if (!appliedFilters.asOnDate) {
      setMessage('Generate the Demand List before printing.');
      return null;
    }
    if (!hasReportScope(appliedFilters)) {
      setMessage('Select an Area or enter Contract No before generating.');
      return null;
    }
    return { rows: { content: rows }, summary, warnings };
  };

  const print = () => {
    const data = currentReport();
    if (!data) return;
    setPrintData(data);
    window.setTimeout(() => window.print(), 150);
  };

  const exportExcel = () => {
    const data = currentReport();
    if (!data) return;
    const rowsForExport = data?.rows?.content || [];
    const title = `Demand List as on ${appliedFilters.asOnDate}`;
    const html = `
      <html>
        <head><meta charset="utf-8" /></head>
        <body>
          <table border="1">
            <tr><th colspan="8">${excelCell(title)}</th></tr>
            <tr>
              <th>Sl. No</th>
              <th>Loan No.</th>
              <th>Name of Borrower<br/>Name of Guarantor</th>
              <th>Product Type<br/>Make of Vehicle/<br/>Regn No./Location<br/>No .of Owner<br/>Product usage</th>
              <th>Contract Value<br/>Principal O/S<br/>Interest O/S<br/>Total O/S</th>
              <th>No.of Overdues<br/>O/D Amount<br/>From Date<br/>End Date</th>
              <th>Current Due</th>
              <th>Due Date</th>
            </tr>
            ${rowsForExport.map((row, index) => `
              <tr>
                <td>${index + 1}</td>
                <td>${excelCell(row.loanNumber)}</td>
                <td>${excelCell(row.borrowerName)}<br/>${excelCell(row.guarantorName)}</td>
                <td>${excelCell(row.productType)}<br/>${excelCell(row.assetDescription)}<br/>${excelCell(row.registrationOrLocation)}<br/>${excelCell(row.ownerNumber)}<br/>${excelCell(row.vehicleTypeCode || row.usage)}</td>
                <td style="text-align:right">${excelCell(row.contractValue)}<br/>${excelCell(row.principalOutstanding)}<br/>${excelCell(row.interestOutstanding)}<br/>${excelCell(row.totalOutstanding)}</td>
                <td style="text-align:right">${excelCell(firstValue(row, ['overdueInstallmentCount', 'noOfOverdues', 'numberOfOverdues', 'overdue_count']))}<br/>${excelCell(firstValue(row, ['overdueAmount', 'odAmount', 'overdue_amount']))}<br/>${excelCell(firstValue(row, ['overdueFromDate', 'fromDate', 'overdue_from_date']))}<br/>${excelCell(firstValue(row, ['overdueEndDate', 'endDate', 'overdue_end_date']))}</td>
                <td style="text-align:right">${excelCell(row.currentDueAmount)}</td>
                <td>${excelCell(row.currentDueDate)}</td>
              </tr>
            `).join('')}
          </table>
        </body>
      </html>
    `;
    const blob = new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `demand-list-${appliedFilters.asOnDate}.xls`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="flex h-full min-h-0 flex-col bg-white font-['Segoe_UI',Arial,sans-serif] text-[12px] text-black">
      <style>{`
        @page { size: A4 landscape; margin: 4mm; }
        .demand-print-root { display: none; }
        @media print {
          html, body { margin: 0 !important; padding: 0 !important; background: #fff !important; }
          body * { visibility: hidden !important; }
          .no-print { display: none !important; }
          .demand-print-root, .demand-print-root * { visibility: visible !important; }
          .demand-print-root { display: block !important; position: absolute; left: 0; top: 0; width: 100%; color: #000; font-family: "Segoe UI", Arial, sans-serif; }
          .demand-print-title { margin: 0 0 3mm; font-size: 12px; font-weight: 700; text-align: center; }
          .demand-print-table { width: 100%; border-collapse: collapse; font-size: 7.5px; color: #000; }
          .demand-print-table th, .demand-print-table td { border: 0.2mm solid #999; padding: 1mm 0.8mm; vertical-align: top; }
          .demand-print-table th { text-align: left; font-weight: 700; }
          .demand-print-table thead { display: table-header-group; }
          .demand-print-table tbody tr { break-inside: avoid; page-break-inside: avoid; }
          .demand-print-table .num { text-align: right; white-space: nowrap; }
          .demand-print-table .center { text-align: center; }
          .demand-print-line { min-height: 3.6mm; white-space: normal; }
          .demand-print-total { font-weight: 700; }
        }
      `}</style>

      <div className="no-print border-b border-black/20 bg-[#f8fafc] px-2 py-1">
        <div className="text-[16px] font-bold uppercase text-[#0052CC]">Demand List</div>
      </div>

      <DemandListFilters
        filters={filters}
        loading={loading}
        onChange={setFilters}
        onExportExcel={exportExcel}
        onGenerate={generate}
        onPrint={print}
        onReset={reset}
      />

      {message && <div className="no-print border-b border-red-200 bg-red-50 px-2 py-1 font-bold text-red-700">{message}</div>}
      {warnings.length > 0 && <div className="no-print border-b border-amber-200 bg-amber-50 px-2 py-1 font-bold text-amber-800">{warnings.join(' | ')}</div>}

      <DemandListSummary summary={summary} />
      <DemandListTable rows={rows} onOpen={setSelectedRow} />

      <div className="no-print border-t border-black/20 bg-white px-2 py-1 font-bold">
        {loading ? 'Loading...' : `${rows.length.toLocaleString('en-IN')} records`}
      </div>

      {printData && <DemandListPrintView data={printData} filters={appliedFilters} />}

      <DemandListDetailsDrawer row={selectedRow} onClose={() => setSelectedRow(null)} />

      {loading && (
        <div className="no-print fixed inset-0 z-[100] flex items-center justify-center bg-black/35 px-4">
          <div className="w-full max-w-sm border border-blue-900 bg-white px-5 py-4 text-center shadow-2xl">
            <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-blue-200 border-t-[#0052CC]" />
            <div className="mt-3 text-[14px] font-black uppercase text-[#0052CC]">Loading report</div>
            <div className="mt-1 text-[12px] font-bold text-black">{loadingMessage || 'Please wait while the report is processed.'}</div>
          </div>
        </div>
      )}
    </div>
  );
}
