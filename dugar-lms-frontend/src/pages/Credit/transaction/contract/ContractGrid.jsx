import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { Eye, FilePenLine, FilePlus2, Flag, Loader2, Printer, RotateCcw, Save, X } from 'lucide-react';
import ServerDataTable from '../../../../components/common/ServerDataTable';
import {
  fetchContractFlagMaster,
  fetchContractFlags,
  fetchContractsPage,
  saveContractFlags,
} from '../../../../services/contractsService';

const PAGE_SIZE_OPTIONS = [25, 50, 100, 250];
const DEFAULT_FILTERS = {
  branch: '',
  status: '',
  product: '',
  customerName: '',
  contractDateFrom: '',
  contractDateTo: '',
  minimumLoanAmount: '',
  maximumLoanAmount: '',
};
const DEFAULT_HIDDEN_COLUMNS = {
  contractId: false,
  legacyContractNumber: false,
  vehicleMake: false,
  engineNumber: false,
  chassisNumber: false,
  manufactureYear: false,
  vehicleFinanceType: false,
  vehicleValue: false,
  vehicleSecurityOffered: false,
  vehicleOwnerSerialNo: false,
  vehicleRegistrationDate: false,
  customerCode: false,
  customerMobile: false,
  customerEmail: false,
  customerAddress: false,
  customerCity: false,
  customerState: false,
  customerPinCode: false,
  customerPanNumber: false,
  customerOccupation: false,
  guarantorCode: false,
  guarantorMobile: false,
  guarantorEmail: false,
  guarantorAddress: false,
  guarantorCity: false,
  guarantorPanNumber: false,
  guarantorState: false,
  guarantorPinCode: false,
  guarantorOccupation: false,
};

function displayFallback(value) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function formatIndianNumber(value) {
  if (value === null || value === undefined || value === '') return '-';
  const number = Number(value);
  return Number.isFinite(number) ? number.toLocaleString('en-IN') : String(value);
}

function formatDateDDMMYYYY(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat('en-GB').format(date);
}

function codeName(code, name) {
  const resolvedCode = displayFallback(code);
  const resolvedName = displayFallback(name);
  return resolvedName === '-' ? resolvedCode : `${resolvedCode} - ${resolvedName}`;
}

function numericValue(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function rowValue(row, ...keys) {
  for (const key of keys) {
    if (row[key] !== null && row[key] !== undefined && row[key] !== '') {
      return row[key];
    }
  }

  return null;
}

function parseRepaymentSchedule(value) {
  if (!value) return [];

  return String(value)
    .split(';')
    .map((entry) => {
      const [sequenceNo, installments, amount] = entry.split('|');
      return { amount, installments, sequenceNo };
    })
    .filter((entry) => entry.sequenceNo && entry.installments && entry.amount);
}

function TotalContractValueCell({ row }) {
  const loanAmount = rowValue(row, 'loanAmount', 'loan_amount');
  const insuranceDeposit = rowValue(row, 'insuranceDeposit', 'insurance_deposit');
  const bpfcAmount = rowValue(row, 'bpfcAmount', 'bpfc_amount');
  const promptPaymentRebate = rowValue(row, 'promptPaymentRebate', 'prompt_payment_rebate');
  const totalContractValue = rowValue(row, 'totalContractValue', 'total_contract_value');
  const storedFinanceCharges = rowValue(row, 'financeCharges', 'finance_charges');
  const derivedFinanceCharges = numericValue(storedFinanceCharges) === null
    && numericValue(totalContractValue) !== null
    && numericValue(loanAmount) !== null
    ? numericValue(totalContractValue)
      - numericValue(loanAmount)
      - (numericValue(insuranceDeposit) || 0)
      - (numericValue(bpfcAmount) || 0)
      + (numericValue(promptPaymentRebate) || 0)
    : storedFinanceCharges;

  return (
    <SmartTooltipCell
      tone="cyan"
      primary={formatIndianNumber(totalContractValue)}
      title="Total Contract Value"
      details={[
        { label: 'Loan Amount', value: formatIndianNumber(loanAmount) },
        { label: 'Finance Charges', value: formatIndianNumber(derivedFinanceCharges) },
        { label: 'Insurance Deposit', value: formatIndianNumber(insuranceDeposit) },
        { label: 'BPFC Amount', value: formatIndianNumber(bpfcAmount) },
        { label: 'Less Rebate', value: formatIndianNumber(promptPaymentRebate) },
        { label: 'Total Value', value: formatIndianNumber(totalContractValue) },
      ]}
    />
  );
}

function TenureCell({ row }) {
  const schedule = parseRepaymentSchedule(row.repaymentSchedule);

  return (
    <SmartTooltipCell
      tone="violet"
      primary={displayFallback(row.tenureMonths)}
      title="Repayment Schedule"
      details={schedule.length > 0
        ? schedule.map((entry) => ({
          label: `Slab ${entry.sequenceNo}`,
          value: `${entry.installments} installment(s) - ${formatIndianNumber(entry.amount)}`,
        }))
        : [{ label: 'Schedule', value: 'No repayment schedule available' }]}
    />
  );
}

function TooltipOverlay({ details, position, title }) {
  if (!position) return null;

  return createPortal(
    <div
      className="pointer-events-none fixed z-[99999] w-[340px] max-h-[360px] overflow-hidden rounded-lg border-2 border-black/20 bg-white p-3 text-left shadow-2xl"
      style={{ left: position.left, top: position.top }}
    >
      {title && (
        <div className="border-b border-black/10 pb-2 mb-2 text-[12px] font-black text-[#0052CC] uppercase tracking-wide text-left">
          {title}
        </div>
      )}
      {details.map((detail) => (
        <div key={detail.label} className="grid grid-cols-[120px_1fr] gap-3 border-b border-black/5 py-1.5 last:border-b-0">
          <div className="text-[11px] font-black text-black/50 uppercase text-left">{detail.label}</div>
          <div className="text-[11px] font-black text-black whitespace-normal break-words text-left">
            {displayFallback(detail.value)}
          </div>
        </div>
      ))}
    </div>,
    document.body,
  );
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function getTooltipPosition(pointerX, pointerY, detailCount) {
  const tooltipWidth = 340;
  const tooltipHeight = Math.min(360, 56 + detailCount * 32);
  const gap = 12;
  const maxLeft = Math.max(gap, window.innerWidth - tooltipWidth - gap);
  const maxTop = Math.max(gap, window.innerHeight - tooltipHeight - gap);
  const canRight = window.innerWidth - pointerX - gap >= tooltipWidth;
  const canLeft = pointerX - gap >= tooltipWidth;
  const canBottom = window.innerHeight - pointerY - gap >= tooltipHeight;
  const canTop = pointerY - gap >= tooltipHeight;

  if (canRight) {
    return {
      left: pointerX + gap,
      top: clamp(pointerY - tooltipHeight / 2, gap, maxTop),
    };
  }

  if (canLeft) {
    return {
      left: pointerX - tooltipWidth - gap,
      top: clamp(pointerY - tooltipHeight / 2, gap, maxTop),
    };
  }

  if (canBottom) {
    return {
      left: clamp(pointerX - tooltipWidth / 2, gap, maxLeft),
      top: pointerY + gap,
    };
  }

  if (canTop) {
    return {
      left: clamp(pointerX - tooltipWidth / 2, gap, maxLeft),
      top: pointerY - tooltipHeight - gap,
    };
  }

  return {
    left: maxLeft,
    top: maxTop,
  };
}

function SmartTooltipCell({ tone = 'blue', primary, title, details = [], children }) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState(null);
  const triggerRef = useRef(null);
  const toneClass = {
    blue: 'text-[#0052CC] bg-blue-50 border-blue-200',
    green: 'text-emerald-700 bg-emerald-50 border-emerald-200',
    amber: 'text-amber-700 bg-amber-50 border-amber-200',
    violet: 'text-violet-700 bg-violet-50 border-violet-200',
    cyan: 'text-cyan-700 bg-cyan-50 border-cyan-200',
  }[tone];

  useEffect(() => {
    if (!open) return undefined;

    const handlePointerMove = () => {
      setOpen(false);
      setPosition(null);
    };
    const handlePointerDown = (event) => {
      if (event.target !== triggerRef.current && !triggerRef.current?.contains(event.target)) {
        setOpen(false);
        setPosition(null);
      }
    };

    const frame = window.requestAnimationFrame(() => {
      window.addEventListener('pointermove', handlePointerMove, { once: true });
      window.addEventListener('pointerdown', handlePointerDown);
    });

    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerdown', handlePointerDown);
    };
  }, [open]);

  const handleOpen = (event) => {
    event.preventDefault();
    event.stopPropagation();

    const pointer = event.changedTouches?.[0] || event;
    const triggerRect = triggerRef.current?.getBoundingClientRect();
    const pointerX = Number.isFinite(pointer.clientX) ? pointer.clientX : (triggerRect?.left || 0) + (triggerRect?.width || 0) / 2;
    const pointerY = Number.isFinite(pointer.clientY) ? pointer.clientY : (triggerRect?.top || 0) + (triggerRect?.height || 0) / 2;

    setPosition(getTooltipPosition(pointerX, pointerY, details.length));
    setOpen(true);
  };

  const handleKeyDown = (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    handleOpen(event);
  };

  return (
    <>
      <span
        ref={triggerRef}
        role="button"
        tabIndex={0}
        onClick={handleOpen}
        onKeyDown={handleKeyDown}
        className={`inline-flex max-w-[260px] cursor-pointer rounded border px-2 py-0.5 font-black ${toneClass}`}
      >
        <span className="truncate">{children || displayFallback(primary)}</span>
      </span>
      {open && <TooltipOverlay details={details} position={position} title={title} />}
    </>
  );
}

function SummaryCell(props) {
  return <SmartTooltipCell {...props} />;
}

function FlagButtonCell({ row, onOpen }) {
  const flagCount = Number(row.flagCount || 0);
  const hasFlags = flagCount > 0;

  return (
    <button
      type="button"
      onClick={(event) => {
        event.stopPropagation();
        onOpen(row);
      }}
      className={`inline-flex h-7 w-7 items-center justify-center rounded border-2 transition-all ${
        hasFlags
          ? 'border-red-200 bg-red-50 text-red-600 hover:bg-red-100'
          : 'border-blue-200 bg-blue-50 text-[#0052CC] hover:bg-blue-100'
      }`}
      title={hasFlags ? `${flagCount} saved flag(s)` : 'No saved flags'}
    >
      <Flag size={15} fill={hasFlags ? 'currentColor' : 'none'} strokeWidth={3} />
    </button>
  );
}

function transformContractsResponse(data) {
  const rows = Array.isArray(data?.content) ? data.content : [];

  return {
    rows,
    totalElements: Number(data?.totalElements || rows.length),
  };
}

const ContractGrid = ({ isDraft = false, title = 'Active Contracts', workflowStatus = null, showCreate = true, enableFlagging = false }) => {
  const navigate = useNavigate();
  const sourceWorkflow = workflowStatus || (isDraft ? 'DRAFT' : 'ACTIVE');
  const isActiveContracts = !isDraft && !workflowStatus;
  const [flagMaster, setFlagMaster] = useState([]);
  const [flagRowUpdates, setFlagRowUpdates] = useState({});
  const [flagModal, setFlagModal] = useState({
    error: '',
    loading: false,
    open: false,
    row: null,
    saving: false,
    selected: {},
  });

  const openContractForm = (row, mode) => {
    navigate('/credit/trans/contract/form', {
      state: {
        contract: row,
        mode,
        sourceWorkflow,
      },
    });
  };

  const printContract = (row) => {
    navigate('/credit/trans/contract/form', {
      state: {
        contract: row,
        mode: 'view',
        print: true,
        sourceWorkflow,
      },
    });
  };

  const createContract = () => {
    navigate('/credit/trans/contract/form', {
      state: {
        mode: 'create',
      },
    });
  };

  const loadContractsPage = useCallback(
    (params) => fetchContractsPage({ ...params, isDraft, workflowStatus }),
    [isDraft, workflowStatus],
  );

  const openFlagModal = useCallback(async (row) => {
    setFlagModal({
      error: '',
      loading: true,
      open: true,
      row,
      saving: false,
      selected: {},
    });

    try {
      const [master, existing] = await Promise.all([
        flagMaster.length > 0 ? Promise.resolve(flagMaster) : fetchContractFlagMaster(),
        fetchContractFlags(row.contractId),
      ]);
      const selected = {};

      (existing.flags || []).forEach((flag) => {
        selected[flag.contractFlagMasterId] = true;
      });

      setFlagMaster(master);
      setFlagModal({
        error: '',
        loading: false,
        open: true,
        row,
        saving: false,
        selected,
      });
    } catch {
      setFlagModal((current) => ({
        ...current,
        error: 'Unable to load contract flags. Please try again.',
        loading: false,
      }));
    }
  }, [flagMaster]);

  const closeFlagModal = () => {
    if (flagModal.saving) return;
    setFlagModal((current) => ({ ...current, open: false }));
  };

  const toggleFlagSelection = (flagId) => {
    setFlagModal((current) => {
      const selected = { ...current.selected, [flagId]: !current.selected[flagId] };
      if (!selected[flagId]) {
        delete selected[flagId];
      }
      return { ...current, selected };
    });
  };

  const clearFlagSelections = () => {
    setFlagModal((current) => ({ ...current, selected: {} }));
  };

  const saveFlagSelections = async () => {
    const row = flagModal.row;
    if (!row?.contractId) return;

    const flags = Object.keys(flagModal.selected)
      .filter((flagId) => flagModal.selected[flagId])
      .map((flagId) => ({
        contractFlagMasterId: Number(flagId),
      }));

    setFlagModal((current) => ({ ...current, error: '', saving: true }));
    try {
      const response = await saveContractFlags(row.contractId, flags);
      const flagCount = Array.isArray(response.flags) ? response.flags.length : flags.length;
      setFlagRowUpdates((current) => ({
        ...current,
        [row.contractId]: { flagCount },
      }));
      setFlagModal((current) => ({ ...current, open: false, saving: false }));
    } catch {
      setFlagModal((current) => ({
        ...current,
        error: 'Unable to save contract flags. Please try again.',
        saving: false,
      }));
    }
  };

  const filterFields = useMemo(
    () => [
      { field: 'branch', label: 'Branch' },
      { field: 'status', label: 'Status' },
      { field: 'product', label: 'Product' },
      { field: 'customerName', label: 'Customer Name' },
      { field: 'contractDateFrom', label: 'Date From', type: 'date' },
      { field: 'contractDateTo', label: 'Date To', type: 'date' },
      { field: 'minimumLoanAmount', label: 'Loan From', type: 'number' },
      { field: 'maximumLoanAmount', label: 'Loan To', type: 'number' },
    ],
    [],
  );

  const columns = useMemo(
    () => [
      { field: 'serialNumber', headerName: 'S.No', minWidth: 70, align: 'center', headerAlign: 'center' },
      { field: 'contractId', headerName: 'Contract ID', minWidth: 110, align: 'right', headerAlign: 'right', sortable: true },
      { field: 'contractNumber', headerName: 'Contract No', minWidth: 135, sortable: true },
      ...(enableFlagging ? [{
        field: 'flagAction',
        headerName: 'Flag',
        minWidth: 70,
        align: 'center',
        headerAlign: 'center',
        renderCell: (row) => <FlagButtonCell row={row} onOpen={openFlagModal} />,
      }] : []),
      { field: 'legacyContractNumber', headerName: 'Legacy Contract No', minWidth: 165, sortable: true },
      { field: 'product', headerName: 'Product', minWidth: 120, sortable: true },
      { field: 'branch', headerName: 'Branch', minWidth: 115, sortable: true },
      { field: 'contractDate', headerName: 'Contract Date', minWidth: 125, align: 'center', headerAlign: 'center', sortable: true, formatter: formatDateDDMMYYYY },
      { field: 'loanAmount', headerName: 'Loan Amount', minWidth: 135, align: 'right', headerAlign: 'right', sortable: true, formatter: formatIndianNumber },
      { field: 'totalContractValue', headerName: 'Total Contract Value', minWidth: 170, align: 'right', headerAlign: 'right', sortable: true, renderCell: (row) => <TotalContractValueCell row={row} /> },
      { field: 'tenureMonths', headerName: 'Tenure', minWidth: 90, align: 'right', headerAlign: 'right', sortable: true, renderCell: (row) => <TenureCell row={row} /> },
      { field: 'firstEmiDate', headerName: 'First EMI Date', minWidth: 130, align: 'center', headerAlign: 'center', sortable: true, formatter: formatDateDDMMYYYY },
      { field: 'irrRate', headerName: 'IRR', minWidth: 90, align: 'right', headerAlign: 'right', sortable: true },
      {
        field: 'borrowerSummary',
        headerName: 'Borrower',
        minWidth: 250,
        renderCell: (row) => (
          <SummaryCell
            title="Borrower Details"
            primary={codeName(row.customerCode, row.customerName)}
            details={[
              { label: 'Code', value: row.customerCode },
              { label: 'Name', value: row.customerName },
              { label: 'Mobile', value: row.customerMobile },
              { label: 'Email', value: row.customerEmail },
              { label: 'Address', value: row.customerAddress },
              { label: 'City', value: row.customerCity },
              { label: 'State', value: row.customerState },
              { label: 'PIN', value: row.customerPinCode },
              { label: 'PAN', value: row.customerPanNumber },
            ]}
          />
        ),
      },
      {
        field: 'guarantorSummary',
        headerName: 'Guarantor',
        minWidth: 250,
        renderCell: (row) => (
          <SummaryCell
            title="Guarantor Details"
            tone="green"
            primary={codeName(row.guarantorCode, row.guarantorName)}
            details={[
              { label: 'Code', value: row.guarantorCode },
              { label: 'Name', value: row.guarantorName },
              { label: 'Mobile', value: row.guarantorMobile },
              { label: 'Email', value: row.guarantorEmail },
              { label: 'Address', value: row.guarantorAddress },
              { label: 'City', value: row.guarantorCity },
              { label: 'State', value: row.guarantorState },
              { label: 'PIN', value: row.guarantorPinCode },
              { label: 'PAN', value: row.guarantorPanNumber },
            ]}
          />
        ),
      },
      {
        field: 'vehicleRegistrationNumber',
        headerName: 'Vehicle Code',
        minWidth: 150,
        sortable: true,
        renderCell: (row) => (
          <SummaryCell
            title="Vehicle Details"
            tone="amber"
            primary={row.vehicleRegistrationNumber}
            details={[
              { label: 'Code', value: row.vehicleRegistrationNumber },
              { label: 'Type', value: row.vehicleTypeCode },
              { label: 'Make', value: row.vehicleMake },
              { label: 'Engine', value: row.engineNumber },
              { label: 'Chassis', value: row.chassisNumber },
              { label: 'Value', value: formatIndianNumber(row.vehicleValue) },
            ]}
          />
        ),
      },
      { field: 'vehicleTypeCode', headerName: 'Vehicle Type', minWidth: 120, sortable: true },
      { field: 'vehicleMake', headerName: 'Vehicle Make', minWidth: 135, sortable: true },
      { field: 'engineNumber', headerName: 'Engine No', minWidth: 150, sortable: true },
      { field: 'chassisNumber', headerName: 'Chassis No', minWidth: 150, sortable: true },
      { field: 'manufactureYear', headerName: 'Mfg Year', minWidth: 95, align: 'center', headerAlign: 'center', sortable: true },
      { field: 'vehicleFinanceType', headerName: 'Vehicle Finance Type', minWidth: 165, sortable: true },
      { field: 'vehicleValue', headerName: 'Vehicle Value', minWidth: 130, align: 'right', headerAlign: 'right', sortable: true, formatter: formatIndianNumber },
      { field: 'vehicleSecurityOffered', headerName: 'Vehicle Security', minWidth: 180 },
      { field: 'vehicleOwnerSerialNo', headerName: 'Owner Serial No', minWidth: 145, sortable: true },
      { field: 'vehicleRegistrationDate', headerName: 'Vehicle Reg Date', minWidth: 140, align: 'center', headerAlign: 'center', sortable: true, formatter: formatDateDDMMYYYY },
      { field: 'customerCode', headerName: 'Customer Code', minWidth: 130, sortable: true },
      { field: 'customerMobile', headerName: 'Customer Mobile', minWidth: 140, sortable: true },
      { field: 'customerEmail', headerName: 'Customer Email', minWidth: 190, sortable: true },
      { field: 'customerAddress', headerName: 'Customer Address', minWidth: 260, sortable: true },
      { field: 'customerCity', headerName: 'Customer City', minWidth: 130, sortable: true },
      { field: 'customerState', headerName: 'Customer State', minWidth: 140, sortable: true },
      { field: 'customerPinCode', headerName: 'Customer PIN', minWidth: 120, sortable: true },
      { field: 'customerPanNumber', headerName: 'Customer PAN', minWidth: 130, sortable: true },
      { field: 'customerOccupation', headerName: 'Customer Occupation', minWidth: 165, sortable: true },
      { field: 'guarantorCode', headerName: 'Guarantor Code', minWidth: 135, sortable: true },
      { field: 'guarantorMobile', headerName: 'Guarantor Mobile', minWidth: 150, sortable: true },
      { field: 'guarantorEmail', headerName: 'Guarantor Email', minWidth: 190, sortable: true },
      { field: 'guarantorAddress', headerName: 'Guarantor Address', minWidth: 260, sortable: true },
      { field: 'guarantorCity', headerName: 'Guarantor City', minWidth: 140, sortable: true },
      { field: 'guarantorState', headerName: 'Guarantor State', minWidth: 145, sortable: true },
      { field: 'guarantorPinCode', headerName: 'Guarantor PIN', minWidth: 125, sortable: true },
      { field: 'guarantorPanNumber', headerName: 'Guarantor PAN', minWidth: 140, sortable: true },
      { field: 'guarantorOccupation', headerName: 'Guarantor Occupation', minWidth: 175, sortable: true },
    ],
    [enableFlagging, openFlagModal],
  );

  const viewContextMenuItem = (row) => ({
      label: 'View',
      icon: <Eye size={14} />,
      onClick: () => openContractForm(row, 'view'),
  });

  const editContextMenuItem = (row) => ({
    label: 'Edit',
    icon: <FilePenLine size={14} />,
    onClick: () => openContractForm(row, 'edit'),
  });

  const contextMenuItems = (row) => {
    if (isActiveContracts) {
      return [
        viewContextMenuItem(row),
        editContextMenuItem(row),
      ];
    }

    if (isDraft) {
      return [
        viewContextMenuItem(row),
        editContextMenuItem(row),
        {
          label: 'Print Contract',
          icon: <Printer size={14} />,
          onClick: () => printContract(row),
        },
      ];
    }

    return [viewContextMenuItem(row)];
  };

  return (
    <div className="w-full h-full min-h-0 bg-white" style={{ fontFamily: 'Calibri, sans-serif' }}>
      <ServerDataTable
        columns={columns}
        defaultFilters={DEFAULT_FILTERS}
        defaultHiddenColumns={DEFAULT_HIDDEN_COLUMNS}
        defaultPageSize={25}
        defaultSortColumn="contractDate"
        defaultSortDirection="desc"
        fetchPage={loadContractsPage}
        filterFields={filterFields}
        getContextMenuItems={contextMenuItems}
        getRowId={(row) => row.contractId}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        rowUpdates={flagRowUpdates}
        searchPlaceholder="Quick search contracts..."
        title={title}
        titleAction={showCreate ? (
          <button
            type="button"
            onClick={createContract}
            className="inline-flex items-center gap-2 rounded-lg border-2 border-[#0052CC] bg-[#0052CC] px-3 py-1.5 text-[11px] font-black uppercase text-white shadow-sm transition-all hover:bg-blue-700"
          >
            <FilePlus2 size={14} strokeWidth={3} />
            Create New Contract
          </button>
        ) : null}
        transformResponse={transformContractsResponse}
        onRowDoubleClick={(row) => openContractForm(row, enableFlagging ? 'view' : 'edit')}
      />
      {flagModal.open && createPortal(
        <div className="fixed inset-0 z-[99999] flex items-center justify-center bg-black/30 px-4 py-6">
          <div className="w-full max-w-2xl overflow-hidden rounded-lg border-2 border-black/20 bg-white shadow-2xl">
            <div className="flex items-center justify-between gap-3 border-b-2 border-black/10 bg-[#dfe7f2] px-4 py-3">
              <div className="min-w-0">
                <div className="text-[13px] font-black uppercase text-[#0052CC]">Request To Flag Loan</div>
                <div className="truncate text-[12px] font-bold text-black/70">
                  {displayFallback(flagModal.row?.contractNumber)}
                </div>
              </div>
              <button
                type="button"
                onClick={closeFlagModal}
                className="inline-flex h-8 w-8 items-center justify-center rounded border border-black/20 bg-white text-black hover:bg-slate-100"
                title="Close"
              >
                <X size={16} strokeWidth={3} />
              </button>
            </div>

            <div className="max-h-[70vh] overflow-auto p-4">
              {flagModal.loading ? (
                <div className="flex items-center justify-center gap-2 py-10 text-[12px] font-black uppercase text-[#0052CC]">
                  <Loader2 size={18} className="animate-spin" />
                  Loading flags
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                  {flagMaster.map((flag) => {
                    const flagId = flag.contractFlagMasterId;
                    const checked = Boolean(flagModal.selected[flagId]);
                    return (
                      <label
                        key={flagId}
                        className={`rounded-lg border-2 p-3 transition-colors ${
                          checked ? 'border-[#0052CC] bg-blue-50' : 'border-black/15 bg-white hover:bg-slate-50'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleFlagSelection(flagId)}
                            className="h-4 w-4"
                          />
                          <span className="text-[12px] font-black uppercase text-black">{flag.flagName}</span>
                        </div>
                      </label>
                    );
                  })}
                </div>
              )}

              {flagModal.error && (
                <div className="mt-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-[12px] font-bold text-red-700">
                  {flagModal.error}
                </div>
              )}
            </div>

            <div className="flex items-center justify-end gap-2 border-t border-black/10 bg-slate-50 px-4 py-3">
              <button
                type="button"
                onClick={clearFlagSelections}
                disabled={flagModal.loading || flagModal.saving}
                className="inline-flex items-center gap-2 rounded border border-black/20 bg-white px-3 py-1.5 text-[11px] font-black uppercase text-black disabled:opacity-50"
              >
                <RotateCcw size={14} strokeWidth={3} />
                Clear
              </button>
              <button
                type="button"
                onClick={saveFlagSelections}
                disabled={flagModal.loading || flagModal.saving}
                className="inline-flex items-center gap-2 rounded bg-[#0052CC] px-4 py-1.5 text-[11px] font-black uppercase text-white disabled:opacity-50"
              >
                {flagModal.saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} strokeWidth={3} />}
                Save
              </button>
            </div>
          </div>
        </div>,
        document.body,
      )}
    </div>
  );
};

export default ContractGrid;
