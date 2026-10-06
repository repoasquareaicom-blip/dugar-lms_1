import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, Flag, MessageSquare, X } from 'lucide-react';
import AgingDrilldownDrawer from '../../../components/aging-analysis/AgingDrilldownDrawer';
import DemandListFilters from '../../../components/demand-list/DemandListFilters';
import { lineValues, visibleDemandListColumns } from '../../../components/demand-list/DemandListGrid';
import DemandListPrintView from '../../../components/demand-list/DemandListPrintView';
import DemandListSummary from '../../../components/demand-list/DemandListSummary';
import ReceiptVoucher from '../../accounts/transactions/ReceiptVoucher';
import { fetchAgingContractDetail, fetchAgingContractEmis, fetchAgingContractReceipts, fetchAgingRawVoucher } from '../../../services/agingAnalysisService';
import { addDemandComment, addDemandPtp, fetchDemandComments, fetchDemandList, fetchDemandPtps } from '../../../services/demandListService';
import { fetchContractFlagMaster, fetchContractFlags, saveContractFlags } from '../../../services/contractsService';

const today = new Date().toISOString().slice(0, 10);

const defaultFilters = {
  asOnDate: today,
  areaCode: '',
  contractNumber: '',
  overdueInstallmentCount: '',
  sortBy: 'contractNumber',
  sortOrder: 'desc',
  reportType: 'CONSOLIDATED',
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

function VoucherEditReportModal({ voucherNumber, onClose }) {
  if (!voucherNumber) return null;
  return (
    <div className="no-print fixed inset-0 z-[2100] flex items-center justify-center bg-black/45 p-3">
      <div className="relative flex h-[94vh] w-[96vw] max-w-[1500px] flex-col overflow-hidden border-2 border-blue-950 bg-white shadow-2xl">
        <button type="button" onClick={onClose} className="absolute right-[6px] top-[6px] z-[80] grid h-8 w-8 place-items-center border border-red-300 bg-white text-red-700 shadow hover:bg-red-50" title="Close Voucher Edit" aria-label="Close Voucher Edit">
          <X size={16} />
        </button>
        <div className="flex items-center justify-between border-b border-black/20 bg-blue-50 px-3 py-2">
          <div className="text-[13px] font-black uppercase text-[#0052CC]">Voucher Edit - {voucherNumber}</div>
          <div className="h-8 w-8" />
        </div>
        <div className="min-h-0 flex-1 overflow-hidden">
          <ReceiptVoucher embedded initialVoucherNumber={voucherNumber} onClose={onClose} />
        </div>
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

function splitFlags(row) {
  return String(row.flagNames || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
}

function DemandListTable({ rows, page, pageSize, onOpen, onParty, onFlag, onComment, onPtp, showArea, reportType }) {
  const columns = visibleDemandListColumns({ showArea, reportType });

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
    if (column.key === 'loanFlag') {
      const flags = splitFlags(displayRow);
      const hasFlags = flags.length > 0;
      const hasComment = Boolean(String(displayRow.latestComment || '').trim());
      const hasPtp = Boolean(displayRow.latestPtpDate);
      return (
        <div className="space-y-1 text-left font-bold leading-4">
          <button type="button" title={hasFlags ? 'Edit loan flags' : 'Add loan flag'} onClick={() => onFlag(displayRow)} className="w-full space-y-1 text-left hover:text-[#0052CC]">
            {(hasFlags ? flags : ['Not Flagged']).map((flag) => (
              <span key={flag} className="flex items-start gap-1">
                <Flag size={13} className={`mt-0.5 shrink-0 ${hasFlags ? 'fill-red-600 text-red-700' : 'text-[#0052CC]'}`} />
                <span className="break-words">{flag}</span>
              </span>
            ))}
          </button>
          <div className="border-t border-black/10" />
          <button type="button" title={hasComment ? 'View your comments' : 'Add comment'} onClick={() => onComment(displayRow)} className="flex w-full items-start gap-1 text-left hover:text-[#0052CC]">
            <MessageSquare size={14} className={`mt-0.5 shrink-0 ${hasComment ? 'fill-red-100 text-red-700' : 'text-[#0052CC]'}`} />
            <span className="line-clamp-3 break-words">{hasComment ? displayRow.latestComment : 'No Comment'}</span>
          </button>
          <div className="border-t border-black/10" />
          <button type="button" title={hasPtp ? 'View your PTPs' : 'Add PTP'} onClick={() => onPtp(displayRow)} className="flex w-full items-center gap-1 text-left hover:text-[#0052CC]">
            <CalendarDays size={14} className={`shrink-0 ${hasPtp ? 'text-red-700' : 'text-[#0052CC]'}`} />
            <span>{hasPtp ? lineValues({ lastPaidEmiDate: displayRow.latestPtpDate }, 'lastPaidEmiDate')[0] : 'No PTP'}</span>
          </button>
        </div>
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
  const [flagPopup, setFlagPopup] = useState({ open: false, row: null, master: [], selected: {}, loading: false, saving: false, message: '' });
  const [commentPopup, setCommentPopup] = useState({ open: false, row: null, history: [], loading: false, saving: false, commentText: '', message: '' });
  const [ptpPopup, setPtpPopup] = useState({ open: false, row: null, history: [], loading: false, saving: false, ptpDate: '', message: '' });
  const [voucherEditModal, setVoucherEditModal] = useState({ open: false, voucherNumber: '' });

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
      setRows(content);
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

  useEffect(() => {
    if (!appliedFilters) return;
    if (filters.sortBy === appliedFilters.sortBy && filters.sortOrder === appliedFilters.sortOrder) return;

    let active = true;
    const nextFilters = {
      ...appliedFilters,
      sortBy: filters.sortBy,
      sortOrder: filters.sortOrder,
    };

    setLoading(true);
    setLoadingMessage('Sorting Demand List.');
    setMessage('');
    fetchDemandList(nextFilters)
      .then((data) => {
        if (!active) return;
        const content = data?.rows?.content || [];
        setRows(content);
        setPage(0);
        setTotalRecords(Number(data?.rows?.totalElements ?? content.length));
        setSummary(data?.summary || null);
        setWarnings(data?.warnings || []);
        setAppliedFilters(nextFilters);
        setPrintData(null);
      })
      .catch((error) => {
        if (active) setMessage(demandListErrorMessage(error, 'Unable to sort Demand List.'));
      })
      .finally(() => {
        if (!active) return;
        setLoading(false);
        setLoadingMessage('');
      });

    return () => {
      active = false;
    };
  }, [filters.sortBy, filters.sortOrder, appliedFilters]);

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
    setFlagPopup({ open: false, row: null, master: [], selected: {}, loading: false, saving: false, message: '' });
    setCommentPopup({ open: false, row: null, history: [], loading: false, saving: false, commentText: '', message: '' });
    setPtpPopup({ open: false, row: null, history: [], loading: false, saving: false, ptpDate: '', message: '' });
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

  const openVoucherEdit = (receipt) => {
    const voucherNumber = String(receipt?.voucherNumber || '').trim();
    if (!voucherNumber) return;
    setVoucherEditModal({ open: true, voucherNumber });
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

  const openComment = async (row) => {
    setCommentPopup({ open: true, row, history: [], loading: true, saving: false, commentText: '', message: '' });
    try {
      const history = await fetchDemandComments(row.contractId);
      setCommentPopup((current) => ({ ...current, history: Array.isArray(history) ? history : [], loading: false }));
    } catch (error) {
      setCommentPopup((current) => ({ ...current, loading: false, message: error?.response?.data?.message || error?.message || 'Unable to load comments.' }));
    }
  };

  const submitComment = async () => {
    const row = commentPopup.row;
    const commentText = commentPopup.commentText.trim();
    if (!row?.contractId || !commentText) {
      setCommentPopup((current) => ({ ...current, message: 'Comment is mandatory.' }));
      return;
    }
    setCommentPopup((current) => ({ ...current, saving: true, message: '' }));
    try {
      const saved = await addDemandComment(row.contractId, {
        commentText,
      });
      const history = await fetchDemandComments(row.contractId);
      setRows((current) => current.map((item) => (
        item.contractId === row.contractId ? { ...item, latestComment: saved?.commentText || commentText, latestCommentCreatedAt: saved?.createdAt || null } : item
      )));
      setCommentPopup((current) => ({
        ...current,
        history: Array.isArray(history) ? history : [],
        saving: false,
        commentText: '',
        message: 'Comment saved.',
      }));
    } catch (error) {
      setCommentPopup((current) => ({ ...current, saving: false, message: error?.response?.data?.message || error?.message || 'Unable to save comment.' }));
    }
  };

  const openPtp = async (row) => {
    setPtpPopup({ open: true, row, history: [], loading: true, saving: false, ptpDate: row.latestPtpDate || '', message: '' });
    try {
      const history = await fetchDemandPtps(row.contractId);
      setPtpPopup((current) => ({ ...current, history: Array.isArray(history) ? history : [], loading: false }));
    } catch (error) {
      setPtpPopup((current) => ({ ...current, loading: false, message: error?.response?.data?.message || error?.message || 'Unable to load PTP history.' }));
    }
  };

  const submitPtp = async () => {
    const row = ptpPopup.row;
    if (!row?.contractId || !ptpPopup.ptpDate) {
      setPtpPopup((current) => ({ ...current, message: 'PTP date is required.' }));
      return;
    }
    setPtpPopup((current) => ({ ...current, saving: true, message: '' }));
    try {
      const saved = await addDemandPtp(row.contractId, { ptpDate: ptpPopup.ptpDate });
      const history = await fetchDemandPtps(row.contractId);
      setRows((current) => current.map((item) => (
        item.contractId === row.contractId ? { ...item, latestPtpDate: saved?.ptpDate || ptpPopup.ptpDate } : item
      )));
      setPtpPopup((current) => ({
        ...current,
        history: Array.isArray(history) ? history : [],
        saving: false,
        ptpDate: saved?.ptpDate || current.ptpDate,
        message: 'PTP saved.',
      }));
    } catch (error) {
      setPtpPopup((current) => ({ ...current, saving: false, message: error?.response?.data?.message || error?.message || 'Unable to save PTP.' }));
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

  const openFlagEditor = async (row) => {
    setFlagPopup({ open: true, row, master: [], selected: {}, loading: true, saving: false, message: '' });
    try {
      const [master, existing] = await Promise.all([
        fetchContractFlagMaster(),
        fetchContractFlags(row.contractId),
      ]);
      const selected = {};
      (existing.flags || []).forEach((flag) => {
        selected[flag.contractFlagMasterId] = true;
      });
      setFlagPopup({ open: true, row, master, selected, loading: false, saving: false, message: '' });
    } catch (error) {
      setFlagPopup((current) => ({ ...current, loading: false, message: error?.response?.data?.message || error?.message || 'Unable to load contract flags.' }));
    }
  };

  const toggleFlag = (flagId) => {
    setFlagPopup((current) => {
      const selected = { ...current.selected };
      if (selected[flagId]) {
        delete selected[flagId];
      } else {
        selected[flagId] = true;
      }
      return { ...current, selected, message: '' };
    });
  };

  const saveFlags = async () => {
    const row = flagPopup.row;
    if (!row?.contractId) return;
    const flags = Object.entries(flagPopup.selected)
      .filter(([, checked]) => checked)
      .map(([flagId]) => ({
        contractFlagMasterId: Number(flagId),
      }));
    setFlagPopup((current) => ({ ...current, saving: true, message: '' }));
    try {
      const response = await saveContractFlags(row.contractId, flags);
      const savedFlags = Array.isArray(response.flags) ? response.flags : [];
      const flagNames = savedFlags.map((flag) => flag.flagName).filter(Boolean).join(', ');
      const flagCodes = savedFlags.map((flag) => flag.flagCode).filter(Boolean).join(', ');
      const flagRemarks = savedFlags
        .filter((flag) => String(flag.remarks || '').trim())
        .map((flag) => `${flag.flagName}: ${String(flag.remarks).trim()}`)
        .join('\n');
      setRows((current) => current.map((item) => (
        item.contractId === row.contractId
          ? { ...item, flagCount: savedFlags.length, flagNames, flagCodes, flagRemarks }
          : item
      )));
      setFlagPopup({ open: false, row: null, master: [], selected: {}, loading: false, saving: false, message: '' });
    } catch (error) {
      setFlagPopup((current) => ({ ...current, saving: false, message: error?.response?.data?.message || error?.message || 'Unable to save contract flags.' }));
    }
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
    const title = `Demand List - ${String(appliedFilters.reportType || 'CONSOLIDATED').replaceAll('_', ' ')} as on ${appliedFilters.asOnDate}`;
    const showArea = !appliedFilters?.areaCode?.trim();
    const columns = visibleDemandListColumns({ showArea, reportType: appliedFilters.reportType });
    const headerColSpan = columns.length;
    const headerCells = columns.map((column) => `<th>${column.label.map(excelCell).join('<br/>')}</th>`).join('');
    const bodyRows = rowsForExport.map((row, index) => {
      const displayRow = { ...row, serialNumber: index + 1 };
      return `<tr>${columns.map((column) => `<td>${lineValues(displayRow, column.key).map(excelCell).join('<br/>')}</td>`).join('')}</tr>`;
    }).join('');
    const html = `
      <html>
        <head><meta charset="utf-8" /></head>
        <body>
          <table border="1">
            <tr><th colspan="${headerColSpan}">${excelCell(title)}</th></tr>
            <tr>${headerCells}</tr>
            ${bodyRows}
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
        onFlag={openFlagEditor}
        onComment={openComment}
        onPtp={openPtp}
        showArea={showAreaColumn}
        reportType={appliedFilters?.reportType || filters.reportType}
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

      <AgingDrilldownDrawer state={drilldown} onClose={closeDrilldown} onSelectContract={(contractId) => loadDrilldownContract(contractId, appliedFilters?.asOnDate || filters.asOnDate)} onSelectReceipt={selectDrilldownReceipt} onOpenVoucherEdit={openVoucherEdit} />

      {voucherEditModal.open && (
        <VoucherEditReportModal
          voucherNumber={voucherEditModal.voucherNumber}
          onClose={() => setVoucherEditModal({ open: false, voucherNumber: '' })}
        />
      )}

      {partyPopup && (
        <InfoModal title={partyPopup.role} onClose={() => setPartyPopup(null)}>
          <PartyDetails person={partyPopup} />
        </InfoModal>
      )}

      {flagPopup.open && (
        <InfoModal title={`Flags - ${flagPopup.row?.loanNumber || flagPopup.row?.contractId}`} onClose={() => setFlagPopup({ open: false, row: null, master: [], selected: {}, loading: false, saving: false, message: '' })}>
          {flagPopup.loading ? (
            <div className="font-bold text-black/60">Loading flags...</div>
          ) : (
            <div className="space-y-2">
              {flagPopup.master.map((flag) => {
                const checked = Boolean(flagPopup.selected[flag.contractFlagMasterId]);
                return (
                  <div key={flag.contractFlagMasterId} className={`border p-2 ${checked ? 'border-blue-300 bg-blue-50' : 'border-black/20 bg-white'}`}>
                    <label className="flex items-center gap-2 font-black uppercase">
                      <input type="checkbox" checked={checked} onChange={() => toggleFlag(flag.contractFlagMasterId)} />
                      {flag.flagName}
                    </label>
                  </div>
                );
              })}
              {flagPopup.message && <div className="font-bold text-red-700">{flagPopup.message}</div>}
              <button type="button" disabled={flagPopup.saving} onClick={saveFlags} className="border border-[#0052CC] bg-[#0052CC] px-3 py-1.5 text-[12px] font-black uppercase text-white disabled:opacity-50">
                {flagPopup.saving ? 'Saving...' : 'Save Flags'}
              </button>
            </div>
          )}
        </InfoModal>
      )}

      {commentPopup.open && (
        <InfoModal title={`Comment - ${commentPopup.row?.loanNumber || ''}`} onClose={() => setCommentPopup({ open: false, row: null, history: [], loading: false, saving: false, commentText: '', message: '' })}>
          <div className="space-y-3">
            <div className="border border-black/20 bg-slate-50 p-2">
              <div className="mb-2 font-black uppercase text-[#0052CC]">Add Comment</div>
              <label className="block">
                <span className="mb-1 block font-black uppercase text-black/60">Comment *</span>
                <textarea
                  value={commentPopup.commentText}
                  onChange={(event) => setCommentPopup((current) => ({ ...current, commentText: event.target.value, message: '' }))}
                  className="h-20 w-full resize-none border border-black/30 bg-white p-2 text-[12px] font-bold outline-none focus:border-[#0052CC]"
                />
              </label>
              {commentPopup.message && <div className="mt-2 font-bold text-[#0052CC]">{commentPopup.message}</div>}
              <button type="button" disabled={commentPopup.saving} onClick={submitComment} className="mt-2 border border-[#0052CC] bg-[#0052CC] px-3 py-1.5 text-[12px] font-black uppercase text-white disabled:opacity-50">
                {commentPopup.saving ? 'Saving...' : 'Save'}
              </button>
            </div>
            <div>
              <div className="mb-2 font-black uppercase text-black/70">History</div>
              {commentPopup.loading && <div className="font-bold text-black/60">Loading history...</div>}
              {!commentPopup.loading && commentPopup.history.length === 0 && <div className="font-bold text-black/60">No comments.</div>}
              <div className="max-h-64 space-y-2 overflow-auto">
                {commentPopup.history.map((item) => (
                  <div key={item.contractFollowUpId} className="border border-black/20 bg-white p-2">
                    <div className="font-black text-black">{formatDateTime(item.createdAt)}</div>
                    <div className="mt-1 whitespace-pre-wrap font-bold leading-5">{item.commentText}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </InfoModal>
      )}

      {ptpPopup.open && (
        <InfoModal title={`PTP - ${ptpPopup.row?.loanNumber || ''}`} onClose={() => setPtpPopup({ open: false, row: null, history: [], loading: false, saving: false, ptpDate: '', message: '' })}>
          <div className="space-y-3">
            <div className="border border-black/20 bg-slate-50 p-2">
              <div className="mb-2 font-black uppercase text-[#0052CC]">Add PTP</div>
              <label className="block">
                <span className="mb-1 block font-black uppercase text-black/60">PTP Date *</span>
                <input
                  type="date"
                  value={ptpPopup.ptpDate}
                  onChange={(event) => setPtpPopup((current) => ({ ...current, ptpDate: event.target.value, message: '' }))}
                  className="h-8 w-full border border-black/30 bg-white px-2 text-[12px] font-bold outline-none focus:border-[#0052CC]"
                />
              </label>
              {ptpPopup.message && <div className="mt-2 font-bold text-[#0052CC]">{ptpPopup.message}</div>}
              <button type="button" disabled={ptpPopup.saving} onClick={submitPtp} className="mt-2 border border-[#0052CC] bg-[#0052CC] px-3 py-1.5 text-[12px] font-black uppercase text-white disabled:opacity-50">
                {ptpPopup.saving ? 'Saving...' : 'Save PTP'}
              </button>
            </div>
            <div>
              <div className="mb-2 font-black uppercase text-black/70">History</div>
              {ptpPopup.loading && <div className="font-bold text-black/60">Loading history...</div>}
              {!ptpPopup.loading && ptpPopup.history.length === 0 && <div className="font-bold text-black/60">No PTP.</div>}
              <div className="max-h-64 space-y-2 overflow-auto">
                {ptpPopup.history.map((item) => (
                  <div key={item.contractPtpId} className="border border-black/20 bg-white p-2">
                    <div className="font-black text-black">{lineValues({ lastPaidEmiDate: item.ptpDate }, 'lastPaidEmiDate')[0]}</div>
                    <div className="font-bold text-black/70">{formatDateTime(item.createdAt)}</div>
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
