import { useEffect, useMemo, useState } from 'react';
import DemandListDetailsDrawer from '../../../components/demand-list/DemandListDetailsDrawer';
import DemandListFilters from '../../../components/demand-list/DemandListFilters';
import DemandListGrid from '../../../components/demand-list/DemandListGrid';
import DemandListPrintView from '../../../components/demand-list/DemandListPrintView';
import DemandListSummary from '../../../components/demand-list/DemandListSummary';
import { fetchDemandList, fetchDemandListPrint } from '../../../services/demandListService';

const today = new Date().toISOString().slice(0, 10);

const defaultFilters = {
  asOnDate: today,
  contractNumber: '',
  overdueInstallmentCount: '',
};

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

const pageSizes = [25, 50, 100, 250];

export default function DemandListPage() {
  const [filters, setFilters] = useState(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState(null);
  const [rows, setRows] = useState([]);
  const [summary, setSummary] = useState(null);
  const [warnings, setWarnings] = useState([]);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [totalElements, setTotalElements] = useState(0);
  const [sortColumn, setSortColumn] = useState('loanNumber');
  const [sortDirection, setSortDirection] = useState('asc');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [selectedRow, setSelectedRow] = useState(null);
  const [printData, setPrintData] = useState(null);

  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));

  useEffect(() => {
    let active = true;

    async function load() {
      if (!appliedFilters) {
        return;
      }
      if (!appliedFilters.asOnDate) {
        setMessage('As On Date is required.');
        return;
      }

      setLoading(true);
      setMessage('');
      try {
        const data = await fetchDemandList(appliedFilters, { page, size: pageSize, sortColumn, sortDirection });
        if (!active) return;
        setRows(data?.rows?.content || []);
        setSummary(data?.summary || null);
        setWarnings(data?.warnings || []);
        setTotalElements(Number(data?.rows?.totalElements || 0));
      } catch (error) {
        if (!active) return;
        setRows([]);
        setTotalElements(0);
        setMessage(demandListErrorMessage(error, 'Unable to load Demand List.'));
      } finally {
        if (active) setLoading(false);
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [appliedFilters, page, pageSize, sortColumn, sortDirection]);

  const generate = () => {
    if (!filters.asOnDate) {
      setMessage('As On Date is required.');
      return;
    }
    setPage(0);
    setAppliedFilters(filters);
  };

  const reset = () => {
    setFilters(defaultFilters);
    setAppliedFilters(null);
    setPage(0);
    setMessage('');
    setRows([]);
    setSummary(null);
    setWarnings([]);
    setTotalElements(0);
    setPrintData(null);
  };

  const changeSort = (field) => {
    setPage(0);
    if (sortColumn !== field) {
      setSortColumn(field);
      setSortDirection('asc');
      return;
    }
    setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'));
  };

  const loadPrint = async () => {
    if (!appliedFilters) {
      setMessage('Generate the Demand List before printing.');
      return null;
    }
    if (!appliedFilters.asOnDate) {
      setMessage('Generate the Demand List before printing.');
      return null;
    }
    setLoading(true);
    setMessage('');
    try {
      const data = await fetchDemandListPrint(appliedFilters, { sortColumn, sortDirection });
      if (data?.printLimitExceeded) {
        setMessage(data.message || 'Demand List is too large to print.');
        return null;
      }
      setPrintData(data);
      return data;
    } catch (error) {
      setMessage(demandListErrorMessage(error, 'Unable to load print data.'));
      return null;
    } finally {
      setLoading(false);
    }
  };

  const print = async () => {
    const data = await loadPrint();
    if (!data) return;
    window.setTimeout(() => window.print(), 150);
  };

  const exportExcel = async () => {
    const data = await loadPrint();
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
                <td style="text-align:right">${excelCell(row.overdueInstallmentCount)}<br/>${excelCell(row.overdueAmount)}<br/>${excelCell(row.overdueFromDate)}<br/>${excelCell(row.overdueEndDate)}</td>
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

  const rangeText = useMemo(() => {
    if (totalElements === 0) return '0 of 0';
    const start = page * pageSize + 1;
    const end = Math.min(totalElements, (page + 1) * pageSize);
    return `${start}-${end} of ${totalElements}`;
  }, [page, pageSize, totalElements]);

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
      <DemandListGrid rows={rows} page={page} pageSize={pageSize} sortColumn={sortColumn} sortDirection={sortDirection} onSort={changeSort} onOpen={setSelectedRow} />

      <div className="no-print flex items-center justify-between border-t border-black/20 bg-white px-2 py-1">
        <div className="font-bold">{loading ? 'Loading...' : rangeText}</div>
        <div className="flex items-center gap-2">
          <select className="h-7 border border-black/30 px-2" value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(0); }}>
            {pageSizes.map((size) => <option key={size} value={size}>{size}</option>)}
          </select>
          <button type="button" className="h-7 border border-black/30 px-3 font-bold disabled:opacity-40" disabled={page <= 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>Prev</button>
          <span className="font-bold">Page {page + 1} / {totalPages}</span>
          <button type="button" className="h-7 border border-black/30 px-3 font-bold disabled:opacity-40" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}>Next</button>
        </div>
      </div>

      {printData && <DemandListPrintView data={printData} filters={appliedFilters} />}

      <DemandListDetailsDrawer row={selectedRow} onClose={() => setSelectedRow(null)} />
    </div>
  );
}
