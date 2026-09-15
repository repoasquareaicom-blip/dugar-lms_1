import { useEffect, useMemo, useState } from 'react';
import { Flag, MessageSquare, X } from 'lucide-react';
import AgingDrilldownDrawer from '../../../components/aging-analysis/AgingDrilldownDrawer';
import DemandListFilters from '../../../components/demand-list/DemandListFilters';
import { lineValues, visibleDemandListColumns } from '../../../components/demand-list/DemandListGrid';
import DemandListPrintView from '../../../components/demand-list/DemandListPrintView';
import DemandListSummary from '../../../components/demand-list/DemandListSummary';
import { fetchAgingContractDetail, fetchAgingContractEmis, fetchAgingContractReceipts, fetchAgingRawVoucher } from '../../../services/agingAnalysisService';
import { addDemandFollowUp, fetchDemandFollowUps, fetchDemandList } from '../../../services/demandListService';

const today = new Date().toISOString().slice(0, 10);

const defaultFilters = {
  asOnDate: today,
  areaCode: '',
  contractNumber: '',
  overdueInstallmentCount: '',
  overdueSort: 'count',
};
const pageSizeOptions = [10, 25, 50, 100];

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

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  }).format(date);
}

function sortedDemandRows(rows, overdueSort) {
  const sorted = [...rows];
  if (overdueSort === 'amount') {
    sorted.sort((a, b) => Number(b.overdueAmount || 0) - Number(a.overdueAmount || 0));
    return sorted;
  }
  sorted.sort((a, b) => Number(b.overdueInstallmentCount || 0) - Number(a.overdueInstallmentCount || 0));
  return sorted;
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

function InfoModal({ title, children, onClose }) {
  return (
    <div className="no-print fixed inset-0 z-[180] flex items-center justify-center bg-black/35 p-4" onMouseDown={onClose}>
      <div className="w-full max-w-md border border-blue-900 bg-white shadow-2xl" onMouseDown={(event) => event.stopPropagation()}>
        <div className="flex items-center justify-between border-b border-black/20 bg-blue-50 px-3 py-2">
          <div className="text-[13px] font-black uppercase text-[#0052CC]">{title}</div>
          <button type="button" onClick={onClose} className="grid h-7 w-7 place-items-center border border-black/20 bg-white">
            <X size={15} />
          </button>
        </div>
        <div className="p-3 text-[12px] text-black">{children}</div>
      </div>
    </div>
  );
}

function PartyDetails({ person }) {
  return (
    <div className="space-y-2">
      <div>
        <div className="font-black uppercase text-black/60">Name</div>
        <div className="font-bold">{person?.name || '-'}</div>
      </div>
      <div>
        <div className="font-black uppercase text-black/60">Phone Number</div>
        <div className="font-bold">{person?.phone || '-'}</div>
      </div>
      <div>
        <div className="font-black uppercase text-black/60">Address</div>
        <div className="font-bold leading-5">{person?.address || '-'}</div>
      </div>
    </div>
  );
}

function DemandListTable({ rows, page, pageSize, onOpen, onParty, onFlag, onFollowUp, showArea }) {
  const columns = visibleDemandListColumns({ showArea });

  const renderCell = (displayRow, column) => {
    if (column.key === 'loanNumber') {
      return (
        <div className="space-y-1">
          <button
            type="button"
            className="block font-bold text-[#0052CC] underline decoration-dotted underline-offset-2 hover:text-[#003A8C]"
            onClick={() => onOpen(displayRow)}
          >
            {displayRow.loanNumber || ''}
          </button>
          {displayRow.agreementDate && <div className="text-[11px] font-bold text-black/70">Agreement: {lineValues(displayRow, 'loanNumber')[1]?.replace('Agreement: ', '')}</div>}
          <div className="flex gap-1">
            <button type="button" title={Number(displayRow.flagCount || 0) > 0 ? 'View marked flags' : 'No flags'} onClick={() => onFlag(displayRow)} className="grid h-6 w-6 place-items-center border border-black/20 bg-white">
              <Flag size={14} className={Number(displayRow.flagCount || 0) > 0 ? 'fill-red-600 text-red-700' : 'text-[#0052CC]'} />
            </button>
            <button type="button" title={Number(displayRow.followUpCount || 0) > 0 ? 'View follow-up comments' : 'No follow-up comments'} onClick={() => onFollowUp(displayRow)} className="grid h-6 w-6 place-items-center border border-black/20 bg-white">
              <MessageSquare size={14} className={Number(displayRow.followUpCount || 0) > 0 ? 'fill-red-100 text-red-700' : 'text-[#0052CC]'} />
            </button>
          </div>
        </div>
      );
    }
    if (column.key === 'party') {
      const people = [
        { role: 'Borrower', name: displayRow.borrowerName, phone: displayRow.borrowerPhone, address: displayRow.borrowerAddress },
        { role: 'Guarantor', name: displayRow.guarantorName, phone: displayRow.guarantorPhone, address: displayRow.guarantorAddress },
        displayRow.guarantor2Code || displayRow.guarantor2Name
          ? { role: 'Guarantor 2', name: displayRow.guarantor2Name, phone: displayRow.guarantor2Phone, address: displayRow.guarantor2Address }
          : null,
      ].filter(Boolean);
      return (
        <div className="space-y-0.5 leading-4">
          {people.map((person) => (
            <button
              key={person.role}
              type="button"
              onClick={() => onParty(person)}
              className="block text-left font-bold text-[#0052CC] underline decoration-dotted underline-offset-2 hover:text-[#003A8C]"
            >
              {person.name || '-'}
            </button>
          ))}
        </div>
      );
    }
    if (column.key === 'flag') {
      return (
        <button type="button" title={Number(displayRow.flagCount || 0) > 0 ? 'View marked flags' : 'No flags'} onClick={() => onFlag(displayRow)} className="mx-auto grid h-7 w-7 place-items-center border border-black/20 bg-white">
          <Flag size={15} className={Number(displayRow.flagCount || 0) > 0 ? 'fill-red-600 text-red-700' : 'text-[#0052CC]'} />
        </button>
      );
    }
    return <StackCell values={lineValues(displayRow, column.key)} align={column.align || 'left'} />;
  };

  return (
    <div className="no-print min-h-0 flex-1 overflow-auto bg-white">
      <table className="min-w-[1250px] border-collapse text-[12px] text-black">
        <thead className="sticky top-0 z-20 bg-[#e8edf5] text-[12px] font-bold">
          <tr>
            {columns.map((column) => (
              <th key={column.key} className={`${column.width} border border-black/40 px-2 py-1 align-top ${headerAlignClass(column.align)}`}>
                {column.label.map((line) => <div key={line}>{line}</div>)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => {
            const displayRow = { ...row, serialNumber: page * pageSize + index + 1 };
            return (
              <tr key={row.contractId} onDoubleClick={() => onOpen(row)} className={`${index % 2 === 0 ? 'bg-white' : 'bg-slate-50'} hover:bg-blue-50`}>
                {columns.map((column) => (
                  <td key={column.key} className={`border border-black/30 px-2 py-1 align-top ${alignClass(column.align)}`}>
                    {renderCell(displayRow, column)}
                  </td>
                ))}
              </tr>
            );
          })}
          {rows.length === 0 && (
            <tr>
              <td colSpan={columns.length} className="border border-black/30 px-3 py-6 text-center font-bold uppercase text-black/50">No records found</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function DemandListPagination({ page, pageSize, totalRecords, loading, onPageChange, onPageSizeChange }) {
  const totalPages = Math.max(1, Math.ceil(totalRecords / pageSize));
  const startRecord = totalRecords === 0 ? 0 : page * pageSize + 1;
  const endRecord = Math.min(totalRecords, (page + 1) * pageSize);
  const firstPage = Math.max(0, Math.min(page - 2, totalPages - 5));
  const pageNumbers = Array.from({ length: Math.min(5, totalPages) }, (_, index) => firstPage + index);

  return (
    <div className="no-print flex flex-wrap items-center justify-between gap-3 border-t border-black/20 bg-white px-2 py-1 font-bold">
      <div className="flex items-center gap-2">
        <span>Rows per page:</span>
        <select
          value={pageSize}
          disabled={loading}
          onChange={(event) => onPageSizeChange(Number(event.target.value))}
          className="h-7 border border-black/30 bg-white px-2 text-[12px] font-bold outline-none disabled:opacity-50"
        >
          {pageSizeOptions.map((option) => (
            <option key={option} value={option}>{option}</option>
          ))}
        </select>
        <span className="text-black/70">{startRecord}-{endRecord} of {totalRecords.toLocaleString('en-IN')}</span>
      </div>
      <div className="flex items-center gap-1">
        <button
          type="button"
          disabled={loading || page === 0}
          onClick={() => onPageChange(page - 1)}
          className="h-7 border border-black/30 bg-white px-3 text-[12px] font-bold disabled:opacity-40"
        >
          Previous
        </button>
        {pageNumbers.map((pageNumber) => (
          <button
            key={pageNumber}
            type="button"
            disabled={loading}
            onClick={() => onPageChange(pageNumber)}
            className={`h-7 min-w-7 border px-2 text-[12px] font-bold disabled:opacity-40 ${pageNumber === page ? 'border-[#0052CC] bg-[#0052CC] text-white' : 'border-black/30 bg-white text-black'}`}
          >
            {pageNumber + 1}
          </button>
        ))}
        <button
          type="button"
          disabled={loading || page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          className="h-7 border border-black/30 bg-white px-3 text-[12px] font-bold disabled:opacity-40"
        >
          Next
        </button>
      </div>
    </div>
  );
}

export default function DemandListPage() {
  const [filters, setFilters] = useState(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState(null);
  const [rows, setRows] = useState([]);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalRecords, setTotalRecords] = useState(0);
  const [summary, setSummary] = useState(null);
  const [warnings, setWarnings] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingMessage, setLoadingMessage] = useState('');
  const [message, setMessage] = useState('');
  const [drilldown, setDrilldown] = useState({ open: false, loading: false, contracts: [], detail: null, emis: [], receipts: [] });
  const [printData, setPrintData] = useState(null);
  const [partyPopup, setPartyPopup] = useState(null);
  const [flagPopup, setFlagPopup] = useState(null);
  const [followUpPopup, setFollowUpPopup] = useState({ open: false, row: null, history: [], loading: false, saving: false, commentText: '', followUpDate: '', message: '' });

  useEffect(() => {
    if (!appliedFilters) return;
    setRows((current) => sortedDemandRows(current, filters.overdueSort));
    setPage(0);
  }, [filters.overdueSort, appliedFilters]);

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
      const content = data?.rows?.content || [];
      setRows(sortedDemandRows(content, filters.overdueSort));
      setPage(0);
      setTotalRecords(Number(data?.rows?.totalElements ?? content.length));
      setSummary(data?.summary || null);
      setWarnings(data?.warnings || []);
      setAppliedFilters(filters);
      setPrintData(null);
    } catch (error) {
      setRows([]);
      setPage(0);
      setTotalRecords(0);
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
    setPage(0);
    setPageSize(10);
    setTotalRecords(0);
    setSummary(null);
    setWarnings([]);
    setPrintData(null);
    setDrilldown({ open: false, loading: false, contracts: [], detail: null, emis: [], receipts: [] });
    setPartyPopup(null);
    setFlagPopup(null);
    setFollowUpPopup({ open: false, row: null, history: [], loading: false, saving: false, commentText: '', followUpDate: '', message: '' });
  };

  const closeDrilldown = () => {
    setDrilldown({ open: false, loading: false, contracts: [], detail: null, emis: [], receipts: [] });
  };

  const loadDrilldownContract = async (contractId, asOnDate) => {
    setDrilldown((current) => ({ ...current, loading: true, message: '', rawVoucher: null, rawVoucherKey: null, rawVoucherMessage: '', rawVoucherLoading: false }));
    try {
      const [detail, emis, receipts] = await Promise.all([
        fetchAgingContractDetail(contractId, { asOnDate }),
        fetchAgingContractEmis(contractId, { asOnDate }),
        fetchAgingContractReceipts(contractId, { asOnDate }),
      ]);
      setDrilldown((current) => ({ ...current, loading: false, detail, emis, receipts }));
    } catch (error) {
      setDrilldown((current) => ({ ...current, loading: false, message: error?.response?.data?.message || error?.message || 'Unable to load contract details.' }));
    }
  };

  const openDemandDrilldown = async (row) => {
    if (!row?.contractId) {
      setMessage('Contract id is missing for this Demand List row.');
      return;
    }
    const asOnDate = appliedFilters?.asOnDate || filters.asOnDate;
    const nextState = {
      open: true,
      loading: true,
      title: `Demand Drilldown - ${row.loanNumber || row.contractId}`,
      bucketLabel: row.overdueInstallmentCount > 0 ? `${row.overdueInstallmentCount} overdue EMI${row.overdueInstallmentCount === 1 ? '' : 's'}` : 'Current',
      contracts: [row],
      detail: null,
      emis: [],
      receipts: [],
      message: '',
      rawVoucher: null,
      rawVoucherKey: null,
    };
    setDrilldown(nextState);
    await loadDrilldownContract(row.contractId, asOnDate);
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

  const pagedRows = useMemo(() => {
    const start = page * pageSize;
    return rows.slice(start, start + pageSize);
  }, [page, pageSize, rows]);
  const showAreaColumn = !appliedFilters?.areaCode?.trim();

  const openFollowUp = async (row) => {
    setFollowUpPopup({ open: true, row, history: [], loading: true, saving: false, commentText: '', followUpDate: '', message: '' });
    try {
      const history = await fetchDemandFollowUps(row.contractId);
      setFollowUpPopup((current) => ({ ...current, history: Array.isArray(history) ? history : [], loading: false }));
    } catch (error) {
      setFollowUpPopup((current) => ({ ...current, loading: false, message: error?.response?.data?.message || error?.message || 'Unable to load follow-up comments.' }));
    }
  };

  const submitFollowUp = async () => {
    const row = followUpPopup.row;
    const commentText = followUpPopup.commentText.trim();
    if (!row?.contractId || !commentText) {
      setFollowUpPopup((current) => ({ ...current, message: 'Comments are mandatory.' }));
      return;
    }
    setFollowUpPopup((current) => ({ ...current, saving: true, message: '' }));
    try {
      await addDemandFollowUp(row.contractId, {
        commentText,
        followUpDate: followUpPopup.followUpDate || null,
      });
      const history = await fetchDemandFollowUps(row.contractId);
      setRows((current) => current.map((item) => (
        item.contractId === row.contractId ? { ...item, followUpCount: Math.max(Number(item.followUpCount || 0), 1) } : item
      )));
      setFollowUpPopup((current) => ({
        ...current,
        history: Array.isArray(history) ? history : [],
        saving: false,
        commentText: '',
        followUpDate: '',
        message: 'Follow-up saved.',
      }));
    } catch (error) {
      setFollowUpPopup((current) => ({ ...current, saving: false, message: error?.response?.data?.message || error?.message || 'Unable to save follow-up.' }));
    }
  };

  const changePageSize = (nextPageSize) => {
    setPageSize(nextPageSize);
    setPage(0);
  };

  const changePage = (nextPage) => {
    const totalPages = Math.max(1, Math.ceil(totalRecords / pageSize));
    setPage(Math.min(Math.max(0, nextPage), totalPages - 1));
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
    const showArea = !appliedFilters?.areaCode?.trim();
    const headerColSpan = showArea ? 10 : 9;
    const html = `
      <html>
        <head><meta charset="utf-8" /></head>
        <body>
          <table border="1">
            <tr><th colspan="${headerColSpan}">${excelCell(title)}</th></tr>
            <tr>
              <th>Sl. No</th>
              <th>Loan No.</th>
              <th>Name of Borrower<br/>Name of Guarantor</th>
              ${showArea ? '<th>Area</th>' : ''}
              <th>Product Type<br/>Make of Vehicle/<br/>Regn No./Location<br/>No .of Owner<br/>Product usage</th>
              <th>Contract Value<br/>Principal O/S<br/>Interest O/S<br/>Total O/S</th>
              <th>No.of Overdues<br/>O/D Amount<br/>From Date<br/>End Date</th>
              <th>Current Due</th>
              <th>Due Date</th>
              <th>Last Paid EMI Date</th>
            </tr>
            ${rowsForExport.map((row, index) => `
              <tr>
                <td>${index + 1}</td>
                <td>${excelCell(row.loanNumber)}<br/>${row.agreementDate ? `Agreement: ${excelCell(lineValues(row, 'loanNumber')[1]?.replace('Agreement: ', ''))}` : ''}</td>
                <td>${excelCell(row.borrowerName)}<br/>${excelCell(row.guarantorName)}${row.guarantor2Name ? `<br/>${excelCell(row.guarantor2Name)}` : ''}</td>
                ${showArea ? `<td>${excelCell(row.areaName ? `${row.areaCode} - ${row.areaName}` : (row.areaCode || row.area || ''))}</td>` : ''}
                <td>${excelCell(row.productType)}<br/>${excelCell(row.assetDescription)}<br/>${excelCell(row.registrationOrLocation)}<br/>${excelCell(row.ownerNumber)}<br/>${excelCell(row.vehicleTypeCode || row.usage)}</td>
                <td style="text-align:right">${excelCell(row.contractValue)}<br/>${excelCell(row.principalOutstanding)}<br/>${excelCell(row.interestOutstanding)}<br/>${excelCell(row.totalOutstanding)}</td>
                <td style="text-align:right">${excelCell(firstValue(row, ['overdueInstallmentCount', 'noOfOverdues', 'numberOfOverdues', 'overdue_count']))}<br/>${excelCell(firstValue(row, ['overdueAmount', 'odAmount', 'overdue_amount']))}<br/>${excelCell(firstValue(row, ['overdueFromDate', 'fromDate', 'overdue_from_date']))}<br/>${excelCell(firstValue(row, ['overdueEndDate', 'endDate', 'overdue_end_date']))}</td>
                <td style="text-align:right">${excelCell(row.currentDueAmount)}</td>
                <td>${excelCell(row.currentDueDate)}</td>
                <td>${excelCell(row.lastPaidEmiDate || '-')}</td>
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
      <DemandListTable
        rows={pagedRows}
        page={page}
        pageSize={pageSize}
        onOpen={openDemandDrilldown}
        onParty={setPartyPopup}
        onFlag={setFlagPopup}
        onFollowUp={openFollowUp}
        showArea={showAreaColumn}
      />

      <DemandListPagination
        page={page}
        pageSize={pageSize}
        totalRecords={totalRecords}
        loading={loading}
        onPageChange={changePage}
        onPageSizeChange={changePageSize}
      />

      {printData && <DemandListPrintView data={printData} filters={appliedFilters} />}

      <AgingDrilldownDrawer state={drilldown} onClose={closeDrilldown} onSelectContract={(contractId) => loadDrilldownContract(contractId, appliedFilters?.asOnDate || filters.asOnDate)} onSelectReceipt={selectDrilldownReceipt} />

      {partyPopup && (
        <InfoModal title={partyPopup.role} onClose={() => setPartyPopup(null)}>
          <PartyDetails person={partyPopup} />
        </InfoModal>
      )}

      {flagPopup && (
        <InfoModal title={`Flags - ${flagPopup.loanNumber || flagPopup.contractId}`} onClose={() => setFlagPopup(null)}>
          {Number(flagPopup.flagCount || 0) > 0 ? (
            <div className="space-y-1">
              {String(flagPopup.flagNames || '').split(',').map((flag) => flag.trim()).filter(Boolean).map((flag) => (
                <div key={flag} className="border border-red-100 bg-red-50 px-2 py-1 font-bold text-red-800">{flag}</div>
              ))}
            </div>
          ) : (
            <div className="font-bold text-black/60">No flags marked for this contract.</div>
          )}
        </InfoModal>
      )}

      {followUpPopup.open && (
        <InfoModal title={`Follow-up - ${followUpPopup.row?.loanNumber || ''}`} onClose={() => setFollowUpPopup({ open: false, row: null, history: [], loading: false, saving: false, commentText: '', followUpDate: '', message: '' })}>
          <div className="space-y-3">
            <div className="border border-black/20 bg-slate-50 p-2">
              <div className="mb-2 font-black uppercase text-[#0052CC]">Add Follow-up</div>
              <label className="block">
                <span className="mb-1 block font-black uppercase text-black/60">Comments *</span>
                <textarea
                  value={followUpPopup.commentText}
                  onChange={(event) => setFollowUpPopup((current) => ({ ...current, commentText: event.target.value, message: '' }))}
                  className="h-20 w-full resize-none border border-black/30 bg-white p-2 text-[12px] font-bold outline-none focus:border-[#0052CC]"
                />
              </label>
              <label className="mt-2 block">
                <span className="mb-1 block font-black uppercase text-black/60">Follow-up Date</span>
                <input
                  type="date"
                  value={followUpPopup.followUpDate}
                  onChange={(event) => setFollowUpPopup((current) => ({ ...current, followUpDate: event.target.value }))}
                  className="h-8 w-full border border-black/30 bg-white px-2 text-[12px] font-bold outline-none focus:border-[#0052CC]"
                />
              </label>
              {followUpPopup.message && <div className="mt-2 font-bold text-[#0052CC]">{followUpPopup.message}</div>}
              <button type="button" disabled={followUpPopup.saving} onClick={submitFollowUp} className="mt-2 border border-[#0052CC] bg-[#0052CC] px-3 py-1.5 text-[12px] font-black uppercase text-white disabled:opacity-50">
                {followUpPopup.saving ? 'Saving...' : 'Submit'}
              </button>
            </div>
            <div>
              <div className="mb-2 font-black uppercase text-black/70">History</div>
              {followUpPopup.loading && <div className="font-bold text-black/60">Loading history...</div>}
              {!followUpPopup.loading && followUpPopup.history.length === 0 && <div className="font-bold text-black/60">No follow-up comments.</div>}
              <div className="max-h-64 space-y-2 overflow-auto">
                {followUpPopup.history.map((item) => (
                  <div key={item.contractFollowUpId} className="border border-black/20 bg-white p-2">
                    <div className="font-black text-black">{formatDateTime(item.createdAt)}</div>
                    <div className="font-bold text-black/70">Follow-up: {lineValues({ lastPaidEmiDate: item.followUpDate }, 'lastPaidEmiDate')[0]}</div>
                    <div className="font-bold text-black/70">{item.createdByUsername || item.createdBy || '-'}</div>
                    <div className="mt-1 whitespace-pre-wrap font-bold leading-5">{item.commentText}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </InfoModal>
      )}

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
