import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { 
  ArrowLeft, RotateCcw, User, 
  Car, IndianRupee, FileText, Share2, Users, ShieldCheck, Copy, CheckCircle,
  ChevronRight, ChevronLeft, RotateCcw as ResetIcon, Plus, Trash2, X, Send, Loader2,
  CalendarDays, AlertCircle
} from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  fetchContractAreaMasterOptions,
  fetchContractAssetDraft,
  fetchContractCoLendingDraft,
  fetchContractDocumentationDraft,
  fetchContractFinancialDraft,
  fetchContractPartyDraft,
  saveContractAssetDraft,
  saveContractCoLendingDraft,
  saveContractDocumentationDraft,
  saveContractFinancialDraft,
  saveContractHeaderDraft,
  saveContractPartyDraft,
  updateContractStatus,
} from '../../../../services/contractsService';

// --- SUB-COMPONENTS ---

function displayValue(value) {
  if (value === null || value === undefined) return '';
  return String(value);
}

function displayContractNumber(contract) {
  return contract?.contractNumber || contract?.legacyContractNumber || contract?.contractId || 'New';
}

function areaLabel(area) {
  if (!area?.areaCode) return '';
  return area.areaName ? `${area.areaCode} - ${area.areaName}` : area.areaCode;
}

function cleanContractAreaCode(value) {
  const areaCode = String(value || '').trim();
  return areaCode && areaCode !== '-' ? areaCode : '';
}

function currentContractAreaCode(contract) {
  return cleanContractAreaCode(contract?.areaCode || contract?.area_code || contract?.branch);
}

function formatDateInput(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toISOString().slice(0, 10);
}

function formatDateDisplay(value) {
  if (/^\d{2}-\d{2}-\d{4}$/.test(String(value || '').trim())) {
    return String(value).trim();
  }
  const isoValue = formatDateInput(value);
  if (!isoValue) return '';
  const [year, month, day] = isoValue.split('-');
  return `${day}-${month}-${year}`;
}

function parseDateDisplay(value, label) {
  if (!value) return null;
  const trimmed = String(value).trim();
  const isoMatch = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  const displayMatch = trimmed.match(/^(\d{2})-(\d{2})-(\d{4})$/);
  const [, year, month, day] = isoMatch || [];
  const [, displayDay, displayMonth, displayYear] = displayMatch || [];
  const nextYear = year || displayYear;
  const nextMonth = month || displayMonth;
  const nextDay = day || displayDay;

  if (!nextYear || !nextMonth || !nextDay) {
    throw new Error(`${label} must be in dd-mm-yyyy format.`);
  }

  const isoValue = `${nextYear}-${nextMonth}-${nextDay}`;
  const date = new Date(`${isoValue}T00:00:00`);
  if (
    Number.isNaN(date.getTime())
    || date.getFullYear() !== Number(nextYear)
    || date.getMonth() + 1 !== Number(nextMonth)
    || date.getDate() !== Number(nextDay)
  ) {
    throw new Error(`${label} must be a valid date.`);
  }
  return isoValue;
}

function dateInputValue(value) {
  if (!value) return '';
  try {
    return parseDateDisplay(value, 'Date') || '';
  } catch {
    return '';
  }
}

function displayDateFromInput(value) {
  if (!value) return '';
  const [year, month, day] = value.split('-');
  return year && month && day ? `${day}-${month}-${year}` : '';
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

function formValue(formData, key) {
  const value = formData.get(key);
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null;
}

function numberFormValue(formData, key) {
  const value = formValue(formData, key);
  return value === null ? null : value.replace(/,/g, '');
}

function numberStateValue(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  return String(value).replace(/,/g, '').trim() || null;
}

function optionsWithCurrentValue(options, value) {
  const currentValue = typeof value === 'string' ? value.trim() : '';
  return currentValue && !options.includes(currentValue) ? [...options, currentValue] : options;
}

function booleanValue(value) {
  if (value === true || value === 'true' || value === 'Y' || value === 'Yes') return true;
  return false;
}

function integerFormValue(formData, key) {
  const value = formValue(formData, key);
  return value === null ? null : Number.parseInt(value, 10);
}

function formValuesFrom(container) {
  const values = new Map();
  container?.querySelectorAll('[name]').forEach((field) => {
    values.set(field.name, field.value);
  });
  return {
    get: (key) => values.get(key),
  };
}

function partyByRole(parties, role) {
  return parties.find((party) => party.role === role) || {};
}

function normalizeCustomerType(value) {
  const normalized = String(value || '').trim().toLowerCase().replace(/[\s_-]+/g, '');
  if (normalized === 'firm') return 'Firm';
  if (normalized === 'partnership' || normalized === 'partner') return 'Partnership';
  if (normalized === 'pvtltd' || normalized === 'privatelimited' || normalized === 'pvtlimited') return 'Pvt Ltd';
  return 'Individual';
}

function toCustomerType(value) {
  return normalizeCustomerType(value);
}

function moratoriumLabel(value) {
  if (value === 1) return 'One Month';
  if (value === 2) return 'Two Month';
  return 'No Moratorium';
}

function moratoriumMonths(value) {
  if (value === 'One Month') return 1;
  if (value === 'Two Month') return 2;
  return 0;
}

function repaymentRowsFrom(financial, fallbackRows = []) {
  const rows = Array.isArray(financial?.repaymentStructures) && financial.repaymentStructures.length > 0
    ? financial.repaymentStructures
    : fallbackRows;

  return rows.map((row, index) => ({
    id: `${Date.now()}-${index}`,
    installmentAmount: displayValue(row.installmentAmount || row.amount),
    numberOfInstallments: displayValue(row.numberOfInstallments || row.installments),
  }));
}

function repaymentRowsEqual(leftRows, rightRows) {
  if (leftRows.length !== rightRows.length) return false;
  return leftRows.every((leftRow, index) => {
    const rightRow = rightRows[index] || {};
    return displayValue(leftRow.installmentAmount) === displayValue(rightRow.installmentAmount)
      && displayValue(leftRow.numberOfInstallments) === displayValue(rightRow.numberOfInstallments);
  });
}

const pendingDocumentNames = [
  'Agreement book',
  'RC Book',
  'HYP Endorsement',
  'Insurance Copy',
  'NOC Certificate',
  'Permit Copy',
];

const STATUS_OPTIONS = {
  create: [
    { value: 'D', label: 'DRAFT' },
    { value: 'E', label: 'SEND FOR EDIT' },
  ],
  edit: [
    { value: 'E', label: 'SEND FOR EDIT' },
    { value: 'Y', label: 'ACTIVE' },
    { value: 'N', label: 'INACTIVE' },
  ],
};

function normalizeContractStatus(value, fallback = 'D') {
  const normalized = String(value || '').trim().toUpperCase();
  if (normalized === 'DRAFT') return 'D';
  if (normalized === 'SEND_FOR_EDIT' || normalized === 'SEND FOR EDIT' || normalized === 'SUBMITTED_FOR_EDIT') return 'E';
  if (normalized === 'ACTIVE') return 'Y';
  if (normalized === 'INACTIVE' || normalized === 'IN ACTIVE') return 'N';
  return ['D', 'E', 'Y', 'N'].includes(normalized) ? normalized : fallback;
}

function fieldLabel(field) {
  const label = field?.closest('div')?.querySelector('label')?.textContent?.replace('*', '').trim();
  return label || field?.name || 'Field';
}

function fieldErrorMessage(field) {
  const label = fieldLabel(field);
  if (field.validity?.valueMissing) return `${label} is required.`;
  if (field.validity?.patternMismatch && field.title) return `${label}: ${field.title}`;
  if (field.validationMessage) return `${label}: ${field.validationMessage}`;
  return `${label} is invalid.`;
}

function validationMessageFor(container, fallbackMessage) {
  const invalidFields = Array.from(container?.querySelectorAll(':invalid') || []);
  if (invalidFields.length === 0) {
    return fallbackMessage;
  }

  const messages = invalidFields.slice(0, 5).map(fieldErrorMessage);
  const remaining = invalidFields.length - messages.length;
  return remaining > 0
    ? `${messages.join(' ')} ${remaining} more field(s) need attention.`
    : messages.join(' ');
}

function errorMessage(error, fallbackMessage) {
  if (typeof error?.response?.data === 'string') {
    return error.response.data;
  }

  return error?.response?.data?.message
    || error?.response?.data?.error
    || error?.message
    || fallbackMessage;
}

function pendingDocumentsFrom(documentation) {
  const saved = new Map(
    (documentation?.pendingDocuments || []).map((item) => [item.documentName, Boolean(item.pending)])
  );
  return pendingDocumentNames.map((documentName) => ({
    documentName,
    pending: saved.get(documentName) || false,
  }));
}

function uploadsFrom(documentation) {
  return (documentation?.documentUploads || []).map((upload, index) => ({
    id: upload.documentUploadId || `${Date.now()}-${index}`,
    documentCategory: upload.documentCategory || '',
    fileName: upload.fileName || '',
    fileSize: upload.fileSize || 0,
    contentType: upload.contentType || '',
    storagePath: upload.storagePath || '',
    previewUrl: '',
    uploadProgress: 100,
  }));
}

function isPdfUpload(upload) {
  return upload.contentType === 'application/pdf' || /\.pdf$/i.test(upload.fileName || '');
}

function isImageUpload(upload) {
  return String(upload.contentType || '').startsWith('image/');
}

function revokeUploadPreview(upload) {
  if (upload?.previewUrl) {
    URL.revokeObjectURL(upload.previewUrl);
  }
}

function parseMoney(value) {
  const parsed = Number.parseFloat(String(value || '').replace(/,/g, ''));
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatMoney(value) {
  return Number.isFinite(value) && value > 0 ? value.toFixed(2) : '';
}

function totalRepaymentValue(rows) {
  return rows.reduce((total, row) => {
    const amount = parseMoney(row.installmentAmount);
    const installments = Number.parseInt(row.numberOfInstallments || '0', 10);
    return total + (amount * (Number.isFinite(installments) ? installments : 0));
  }, 0);
}

function calculateAnnualIrr(loanAmount, rows) {
  const principal = parseMoney(loanAmount);
  if (principal <= 0) return '';

  const cashflows = [-principal];
  rows.forEach((row) => {
    const amount = parseMoney(row.installmentAmount);
    const installments = Number.parseInt(row.numberOfInstallments || '0', 10);
    if (amount > 0 && installments > 0) {
      for (let index = 0; index < installments; index++) {
        cashflows.push(amount);
      }
    }
  });

  if (cashflows.length <= 1) return '';

  const npv = (rate) => cashflows.reduce((total, cashflow, index) => total + (cashflow / ((1 + rate) ** index)), 0);
  let low = -0.9999;
  let high = 1;
  let lowValue = npv(low);
  let highValue = npv(high);

  while (lowValue * highValue > 0 && high < 10) {
    high *= 2;
    highValue = npv(high);
  }

  if (lowValue * highValue > 0) return '';

  for (let index = 0; index < 80; index++) {
    const mid = (low + high) / 2;
    const midValue = npv(mid);
    if (Math.abs(midValue) < 0.000001) {
      return (mid * 12 * 100).toFixed(4);
    }
    if (lowValue * midValue <= 0) {
      high = mid;
      highValue = midValue;
    } else {
      low = mid;
      lowValue = midValue;
    }
  }

  return (((low + high) / 2) * 12 * 100).toFixed(4);
}

const FormField = ({
  label,
  name,
  placeholder,
  type = "text",
  className = "",
  readOnly = false,
  value,
  inputMode,
  maxLength,
  min,
  pattern,
  step,
  title,
  required = false,
  onChange,
}) => {
  const inputValueProps = onChange
    ? { value: displayValue(value), onChange }
    : { defaultValue: displayValue(value) };

  return (
    <div className={`flex flex-col gap-1 ${className}`} style={{ fontFamily: 'Calibri, sans-serif' }}>
      {/* Changed from text-[11px] and slate to text-[14px] and black/90 */}
      <label className="text-[14px] text-black/90 font-normal uppercase tracking-tight leading-tight">
        {label}
        {required && <span className="ml-1 font-black text-red-600">*</span>}
      </label>
      <input
        type={type}
        name={name}
        placeholder={placeholder}
        {...inputValueProps}
        inputMode={inputMode}
        maxLength={maxLength}
        min={min}
        pattern={pattern}
        required={required}
        readOnly={readOnly}
        step={step}
        title={title}
        /* Changed border and text size */
        className={`border border-black/60 px-2 py-1 text-[16px] font-black text-black/90 focus:border-blue-600 outline-none rounded-sm w-full transition-all ${readOnly ? 'bg-slate-100 cursor-not-allowed' : 'bg-white'}`}
      />
    </div>
  );
};

const DatePickerField = ({
  label,
  name,
  value,
  onChange,
  className = '',
  readOnly = false,
  required = false,
  compact = false,
}) => {
  const pickerRef = useRef(null);
  const [localValue, setLocalValue] = useState(displayValue(value));
  const isControlled = typeof onChange === 'function';
  const displayValueForInput = isControlled ? displayValue(value) : localValue;

  useEffect(() => {
    if (!isControlled) setLocalValue(displayValue(value));
  }, [isControlled, value]);

  const setDateValue = (nextValue) => {
    if (isControlled) {
      onChange({ target: { name, value: nextValue } });
    } else {
      setLocalValue(nextValue);
    }
  };

  const openPicker = () => {
    if (readOnly) return;
    const picker = pickerRef.current;
    if (typeof picker?.showPicker === 'function') {
      picker.showPicker();
    } else {
      picker?.click();
    }
  };

  const inputClass = compact
    ? 'h-8 w-36 bg-white px-2 pr-9 text-[13px] font-black text-slate-900 outline-none read-only:bg-slate-100'
    : `border border-black/60 px-2 py-1 pr-10 text-[16px] font-black text-black/90 focus:border-blue-600 outline-none rounded-sm w-full transition-all ${readOnly ? 'bg-slate-100 cursor-not-allowed' : 'bg-white'}`;

  const field = (
    <div className="relative">
      <input
        type="text"
        name={name}
        value={displayValueForInput}
        onChange={(event) => setDateValue(event.target.value)}
        placeholder="dd-mm-yyyy"
        pattern="[0-9]{2}-[0-9]{2}-[0-9]{4}"
        required={required}
        readOnly={readOnly}
        title={`${label} must be in dd-mm-yyyy format.`}
        className={inputClass}
      />
      <input
        ref={pickerRef}
        type="date"
        tabIndex={-1}
        value={dateInputValue(displayValueForInput)}
        onChange={(event) => setDateValue(displayDateFromInput(event.target.value))}
        className="pointer-events-none absolute right-0 top-0 h-px w-px opacity-0"
        aria-hidden="true"
      />
      <button
        type="button"
        onClick={openPicker}
        disabled={readOnly}
        className="absolute right-1 top-1/2 flex h-6 w-7 -translate-y-1/2 items-center justify-center rounded-sm border border-blue-200 bg-blue-50 text-blue-800 hover:bg-blue-100 disabled:cursor-not-allowed disabled:opacity-40"
        title={`Pick ${label}`}
      >
        <CalendarDays size={14} />
      </button>
    </div>
  );

  if (compact) return field;

  return (
    <div className={`flex flex-col gap-1 ${className}`} style={{ fontFamily: 'Calibri, sans-serif' }}>
      <label className="text-[14px] text-black/90 font-normal uppercase tracking-tight leading-tight">
        {label}
        {required && <span className="ml-1 font-black text-red-600">*</span>}
      </label>
      {field}
    </div>
  );
};

const AreaSearchField = ({ value, onChange, readOnly = false }) => {
  const [areaOpen, setAreaOpen] = useState(false);
  const [areaOptions, setAreaOptions] = useState([]);
  const [selectedArea, setSelectedArea] = useState(null);
  const [areaLoading, setAreaLoading] = useState(false);
  const [areaInputValue, setAreaInputValue] = useState(() => cleanContractAreaCode(value));
  const areaRef = useRef(null);
  const areaCode = cleanContractAreaCode(value);
  const committedAreaLabel = selectedArea?.areaCode === areaCode ? areaLabel(selectedArea) : areaCode;

  const restoreCommittedArea = () => {
    setAreaInputValue(committedAreaLabel);
  };

  useEffect(() => {
    if (!areaOpen) restoreCommittedArea();
  }, [areaCode, selectedArea?.areaCode, selectedArea?.areaName, areaOpen]);

  useEffect(() => {
    if (!areaCode) {
      setSelectedArea(null);
      return undefined;
    }
    if (selectedArea?.areaCode === areaCode) return undefined;
    let active = true;
    fetchContractAreaMasterOptions({ keyword: areaCode, limit: 20 })
      .then((options) => {
        if (!active) return;
        const matchingArea = options.find((area) => area.areaCode.toUpperCase() === areaCode.toUpperCase());
        setSelectedArea(matchingArea || null);
      })
      .catch(() => {
        if (active) setSelectedArea(null);
      });
    return () => {
      active = false;
    };
  }, [areaCode, selectedArea?.areaCode]);

  useEffect(() => {
    if (!areaOpen || readOnly) return undefined;
    let active = true;
    const timer = window.setTimeout(async () => {
      setAreaLoading(true);
      try {
        const options = await fetchContractAreaMasterOptions({ keyword: areaInputValue, limit: 20 });
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
  }, [areaOpen, areaInputValue, readOnly]);

  useEffect(() => {
    const onPointerDown = (event) => {
      if (!areaRef.current?.contains(event.target)) {
        setAreaOpen(false);
        restoreCommittedArea();
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, [committedAreaLabel]);

  if (readOnly) {
    return (
      <input
        name="documentation.areaCode"
        value={committedAreaLabel || '-'}
        readOnly
        className="border border-black/60 px-2 py-1 text-[16px] font-black text-black/90 outline-none rounded-sm w-full transition-all bg-slate-100 cursor-not-allowed"
      />
    );
  }

  return (
    <div ref={areaRef} className="relative w-full">
      <input
        name="documentation.areaCode"
        value={areaInputValue}
        onFocus={() => setAreaOpen(true)}
        onChange={(event) => {
          setSelectedArea(null);
          setAreaInputValue(event.target.value);
          setAreaOpen(true);
        }}
        onBlur={restoreCommittedArea}
        placeholder="Search area..."
        className="border border-black/60 px-2 py-1 text-[16px] font-black text-black/90 focus:border-blue-600 outline-none rounded-sm w-full transition-all bg-white"
      />
      {areaOpen && (
        <div className="absolute left-0 right-0 top-full z-[220] max-h-56 overflow-auto border border-blue-900 bg-white shadow-xl">
          {areaLoading && <div className="px-2 py-2 text-[11px] font-bold text-blue-700">Loading areas...</div>}
          {!areaLoading && areaOptions.length === 0 && <div className="px-2 py-2 text-[11px] font-bold text-black/60">No areas found</div>}
          {!areaLoading && areaOptions.map((area) => (
            <button
              key={area.areaCode}
              type="button"
              className="block w-full border-b border-black/10 px-2 py-1 text-left text-[12px] font-bold hover:bg-blue-50"
              onMouseDown={(event) => {
                event.preventDefault();
                setSelectedArea(area);
                setAreaInputValue(areaLabel(area));
                onChange(area.areaCode);
                setAreaOpen(false);
              }}
            >
              {areaLabel(area)}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

const MessageModal = ({ message, onClose }) => {
  const isError = /^unable|required|save|first|contract|borrower|asset|financial|documentation|co lending/i.test(message || '')
    && !/saved|updated/i.test(message || '');
  const Icon = isError ? AlertCircle : CheckCircle;

  return (
    <AnimatePresence>
      {message && (
        <motion.div
          className="fixed inset-0 z-[350] flex items-center justify-center bg-slate-950/35 px-4 backdrop-blur-[2px]"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <motion.div
            initial={{ opacity: 0, y: 28, scale: 0.92, rotateX: -10 }}
            animate={{ opacity: 1, y: 0, scale: 1, rotateX: 0 }}
            exit={{ opacity: 0, y: 18, scale: 0.96 }}
            transition={{ type: 'spring', stiffness: 360, damping: 26 }}
            className="relative w-full max-w-md overflow-hidden rounded-lg border border-white/70 bg-white shadow-[0_24px_70px_rgba(15,23,42,0.35)]"
          >
            <div className={`h-1.5 ${isError ? 'bg-red-600' : 'bg-emerald-600'}`} />
            <div className="absolute inset-x-0 top-0 h-24 bg-gradient-to-b from-blue-50 to-transparent" />
            <div className="relative p-6 text-center">
              <motion.div
                initial={{ scale: 0.7, rotate: -20 }}
                animate={{ scale: 1, rotate: 0 }}
                transition={{ delay: 0.08, type: 'spring', stiffness: 420, damping: 18 }}
                className={`mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full border ${isError ? 'border-red-200 bg-red-50 text-red-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}`}
              >
                <Icon size={30} strokeWidth={2.5} />
              </motion.div>
              <div className={`mb-2 text-[15px] font-black uppercase tracking-wide ${isError ? 'text-red-800' : 'text-emerald-800'}`}>
                {isError ? 'Attention Required' : 'Success'}
              </div>
              <p className="mx-auto max-w-sm text-[14px] font-bold leading-relaxed text-slate-800">
                {message}
              </p>
              <button
                type="button"
                onClick={onClose}
                className="mt-5 inline-flex h-9 min-w-24 items-center justify-center rounded-md border border-blue-700 bg-blue-700 px-5 text-[12px] font-black uppercase tracking-wide text-white shadow-md transition-all hover:bg-blue-900 active:scale-95"
              >
                OK
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
};

const FormSelect = ({ label, name, options, value, onChange, className = "", readOnly = false, required = false }) => {
  const selectProps = onChange
    ? { value: value || '', onChange }
    : { defaultValue: value || '' };

  return (
    <div className={`flex flex-col gap-1 ${className}`} style={{ fontFamily: 'Calibri, sans-serif' }}>
      {/* Changed from text-[11px] and slate to text-[14px] and black/90 */}
      <label className="text-[14px] text-black/90 font-normal uppercase tracking-tight leading-tight">
        {label}
        {required && <span className="ml-1 font-black text-red-600">*</span>}
      </label>
      <select
        {...selectProps}
        name={name}
        disabled={readOnly}
        required={required}
        /* Standardized font size and border opacity */
        className={`border border-black/60 px-2 py-1 text-[16px] font-black text-black/90 focus:border-blue-600 outline-none rounded-sm w-full h-[36px] transition-all ${readOnly ? 'bg-slate-100 cursor-not-allowed' : 'bg-white cursor-pointer'}`}
      >
        <option value="">Select</option>
        {options.map(opt => <option key={opt} value={opt} className="text-[14px]">{opt}</option>)}
      </select>
    </div>
  );
};

const EntityBlock = ({ title, icon: Icon, typeKey, customerType, onTypeChange, indianStates, readOnly = false, data = {} }) => (
 <div 
  className="space-y-3 mb-6"
  style={{ 
    fontFamily: 'Calibri, Candara, Segoe UI, Optima, Arial, sans-serif',
    color: 'rgba(0, 0, 0, 0.9)' 
  }}
>
    {/* Header with Brand Blue Gradient - Text Black 90 */}
    <div className="flex items-center gap-2 px-3 py-1.5 rounded-t-lg border border-black/60 bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] shadow-sm">
      <Icon size={24} className="text-black/90" />
      <span className="text-[16px] font-blacktext-black/90 font-black uppercase tracking-wide">{title}</span>
    </div>

    {/* Identification Section */}
    <div className="border border-black/60 rounded-sm shadow-sm bg-white overflow-hidden">
      <div className="bg-gradient-to-b from-white to-[#F9FAFF] px-3 py-1 border-b border-black/60 font-normal text-[16px] uppercase text-black/90">
        Identification
      </div>
      <div className="p-3 grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-x-4 gap-y-3">
        <FormSelect 
          label="Type of Customer" 
          name={`${typeKey}.customerType`}
          options={["Individual", "Firm", "Partnership", "Pvt Ltd"]} 
          value={customerType} 
          onChange={(e) => onTypeChange(typeKey, e.target.value)} 
          readOnly={readOnly}
          required={typeKey === 'primary'}
        />
        {customerType === 'Individual' ? (
          <>
            <div className="flex gap-2 md:col-span-2">
              <FormSelect label="Salutation" name={`${typeKey}.salutation`} options={["Mr", "Mrs", "M/S"]} value={data.salutation || ''} className="w-1/4" readOnly={readOnly} />
              <FormField label="Full Name" name={`${typeKey}.fullName`} className="w-3/4" value={data.name} readOnly={readOnly} required={typeKey === 'primary'} />
            </div>
            <FormField label="DOB" name={`${typeKey}.dateOfBirth`} type="date" value={data.dateOfBirth} readOnly={readOnly} />
          </>
        ) : (
          <>
            <FormField label="Firm Name" name={`${typeKey}.firmName`} value={data.firmName || data.name} readOnly={readOnly} required={typeKey === 'primary'} />
            <FormField label="Partner 1" name={`${typeKey}.partner1Name`} value={data.partner1Name} readOnly={readOnly} />
            <FormField label="Partner 2" name={`${typeKey}.partner2Name`} value={data.partner2Name} readOnly={readOnly} />
          </>
        )}
        <FormField label="S/W/D Name" name={`${typeKey}.swdName`} value={data.swdName} readOnly={readOnly} />
        <FormField
          label="Pan No"
          name={`${typeKey}.panNumber`}
          value={data.pan}
          maxLength={10}
          pattern="[A-Za-z0-9]{10}"
          title="PAN must be 10 alphanumeric characters."
          readOnly={readOnly}
        />
        <FormField
          label="Aadhar Number"
          name={`${typeKey}.aadhaarNumber`}
          value={data.aadhaarNumber}
          inputMode="numeric"
          maxLength={12}
          pattern="[0-9]{12}"
          title="Aadhaar number must be exactly 12 digits."
          readOnly={readOnly}
        />
      </div>
    </div>

    {/* Residential Address Section */}
    <div className="border border-black/60 rounded-sm shadow-sm bg-white overflow-hidden">
      <div className="bg-gradient-to-b from-white to-[#F9FAFF] px-3 py-1 border-b border-black/60 font-normal text-[14px] uppercase text-black/90 flex justify-between items-center">
        <span>Residential Address</span>
        {typeKey !== 'primary' && (
          <button 
            type="button" 
            disabled={readOnly}
            className="text-[12px] text-blue-900 font-normal flex items-center gap-1 hover:bg-blue-50 px-2 py-0.5 border border-blue-200 rounded transition-colors disabled:opacity-40"
          >
            <Copy size={12}/> Copy Primary
          </button>
        )}
      </div>
      <div className="p-3 grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-x-4 gap-y-3">
        <FormField label="Flat/Apt No" name={`${typeKey}.addressLine1`} value={data.addressLine1} readOnly={readOnly} />
        <FormField label="Street" name={`${typeKey}.addressLine2`} className="md:col-span-2" value={data.address} readOnly={readOnly} />
        <FormField label="Area" name={`${typeKey}.area`} className="md:col-span-2" value={data.area} readOnly={readOnly} />
        <FormField label="City" name={`${typeKey}.city`} value={data.city} readOnly={readOnly} />
        <FormSelect label="State" name={`${typeKey}.state`} options={indianStates} value={data.state || ''} readOnly={readOnly} />
        <FormField
          label="PIN Code"
          name={`${typeKey}.pinCode`}
          value={data.pinCode}
          inputMode="numeric"
          maxLength={6}
          pattern="[0-9]{6}"
          title="PIN code must be exactly 6 digits."
          readOnly={readOnly}
        />
        <FormField
          label="Dist. (KM)"
          name={`${typeKey}.distanceKm`}
          value={data.distanceKm}
          inputMode="decimal"
          pattern="[0-9]+(\\.[0-9]{1,2})?"
          title="Distance must be a number with up to 2 decimal places."
          readOnly={readOnly}
        />
        <FormSelect label="Type" name={`${typeKey}.residenceType`} options={["Own", "Rental"]} value={data.residenceType || ''} readOnly={readOnly} />
      </div>
    </div>

    <div className="border border-black/60 rounded-sm shadow-sm bg-white overflow-hidden">
      <div className="bg-gradient-to-b from-white to-[#F9FAFF] px-3 py-1 border-b border-black/60 font-normal text-[14px] uppercase text-black/90">
        Contact & Income
      </div>
      <div className="p-3 grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-x-4 gap-y-3">
        <FormField
          label="Contact Number"
          name={`${typeKey}.contactNumber`}
          value={data.contactNumber}
          inputMode="numeric"
          maxLength={10}
          pattern="[0-9]{10}"
          title="Contact number must be exactly 10 digits."
          readOnly={readOnly}
        />
        <FormField
          label="Alternative Number"
          name={`${typeKey}.alternativeNumber`}
          value={data.alternativeNumber}
          inputMode="numeric"
          maxLength={10}
          pattern="[0-9]{10}"
          title="Alternative number must be exactly 10 digits."
          readOnly={readOnly}
        />
        <FormField label="Email" name={`${typeKey}.emailId`} type="email" value={data.emailId} readOnly={readOnly} />
        <FormField label="Occupation" name={`${typeKey}.occupation`} value={data.occupation} readOnly={readOnly} />
        <FormField
          label="Annual Income"
          name={`${typeKey}.annualIncome`}
          value={data.annualIncome}
          inputMode="decimal"
          pattern="[0-9]+(\\.[0-9]{1,2})?"
          title="Annual income must be a number with up to 2 decimal places."
          readOnly={readOnly}
        />
      </div>
    </div>
  </div>
);

// --- MAIN COMPONENT ---

const ContractEditForm = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const selectedContract = location.state?.contract || {};
  const formMode = location.state?.mode || 'edit';
  const sourceWorkflow = normalizeContractStatus(location.state?.sourceWorkflow || selectedContract.status, 'D');
  const isCreateMode = formMode === 'create';
  const isViewMode = formMode === 'view';
  const isEditableWorkflow = sourceWorkflow !== 'D' || (!isCreateMode && normalizeContractStatus(selectedContract.status, 'D') !== 'D');
  const shouldPrint = Boolean(location.state?.print);
  const [activeTab, setActiveTab] = useState('Borrower Details');
  const [proposalCat, setProposalCat] = useState(selectedContract.category || '-');
  const [riskLevel, setRiskLevel] = useState(selectedContract.riskLevel || '-');
  const [contractStatus, setContractStatus] = useState(isEditableWorkflow ? normalizeContractStatus(selectedContract.status, 'E') : 'D');
  const [partyCodes, setPartyCodes] = useState({
    primary: selectedContract.customerCode || null,
    coApp: selectedContract.coApplicantCode || null,
    g1: selectedContract.guarantorCode || null,
    g2: selectedContract.guarantor2Code || null,
  });
  const [draftContract, setDraftContract] = useState({
    contractId: selectedContract.contractId || null,
    contractNumber: selectedContract.contractNumber || selectedContract.legacyContractNumber || null,
    contractDate: formatDateDisplay(selectedContract.contractDate),
  });
  const [savingDraft, setSavingDraft] = useState(false);
  const [loadingDraftCount, setLoadingDraftCount] = useState(0);
  const [draftMessage, setDraftMessage] = useState('');
  const [partyDetails, setPartyDetails] = useState([]);
  const [partyLoadVersion, setPartyLoadVersion] = useState(0);
  const [assetDetails, setAssetDetails] = useState({});
  const [assetLoadVersion, setAssetLoadVersion] = useState(0);
  const [financialDetails, setFinancialDetails] = useState({});
  const [financialLoadVersion, setFinancialLoadVersion] = useState(0);
  const [financialInputs, setFinancialInputs] = useState({
    irrRate: displayValue(selectedContract.irrRate),
    loanAmount: displayValue(selectedContract.loanAmount),
    totalContractValue: displayValue(selectedContract.totalContractValue),
  });
  const [repaymentRows, setRepaymentRows] = useState(() => repaymentRowsFrom({}, parseRepaymentSchedule(selectedContract.repaymentSchedule)));
  const [documentationDetails, setDocumentationDetails] = useState(() => ({
    areaCode: currentContractAreaCode(selectedContract),
  }));
  const [documentationLoadVersion, setDocumentationLoadVersion] = useState(0);
  const [pendingDocuments, setPendingDocuments] = useState(() => pendingDocumentsFrom({}));
  const [documentUploads, setDocumentUploads] = useState([]);
  const [coLendingDetails, setCoLendingDetails] = useState({});
  const [coLendingLoadVersion, setCoLendingLoadVersion] = useState(0);
  const [uploadDocumentCategory, setUploadDocumentCategory] = useState('');
  const [selectedUploadFiles, setSelectedUploadFiles] = useState([]);
  const [uploadInputKey, setUploadInputKey] = useState(0);
  const [activePreviewIndex, setActivePreviewIndex] = useState(null);
  const documentUploadsRef = useRef([]);

  const [types, setTypes] = useState({
    primary: 'Individual',
    coApp: 'Individual',
    g1: 'Individual',
    g2: 'Individual'
  });

  const [productType, setProductType] = useState('');
  const [assetSecured, setAssetSecured] = useState('Secured');

  const startDraftLoad = () => {
    let completed = false;
    setLoadingDraftCount((count) => count + 1);
    return () => {
      if (completed) return;
      completed = true;
      setLoadingDraftCount((count) => Math.max(0, count - 1));
    };
  };

  useEffect(() => {
    if (!shouldPrint) return undefined;

    const printTimer = window.setTimeout(() => window.print(), 450);
    return () => window.clearTimeout(printTimer);
  }, [shouldPrint]);

  useEffect(() => {
    documentUploadsRef.current = documentUploads;
  }, [documentUploads]);

  useEffect(() => () => {
    documentUploadsRef.current.forEach(revokeUploadPreview);
  }, []);

  useEffect(() => {
    if (!draftContract.contractId) return undefined;

    let active = true;
    const finishDraftLoad = startDraftLoad();
    fetchContractPartyDraft(draftContract.contractId)
      .then((parties) => {
        if (!active) return;
        setPartyDetails(Array.isArray(parties) ? parties : []);
        setPartyCodes({
          primary: partyByRole(parties, 'applicant').partyCode || null,
          coApp: partyByRole(parties, 'coApplicant').partyCode || null,
          g1: partyByRole(parties, 'guarantor1').partyCode || null,
          g2: partyByRole(parties, 'guarantor2').partyCode || null,
        });
        setTypes({
          primary: toCustomerType(partyByRole(parties, 'applicant').customerType),
          coApp: toCustomerType(partyByRole(parties, 'coApplicant').customerType),
          g1: toCustomerType(partyByRole(parties, 'guarantor1').customerType),
          g2: toCustomerType(partyByRole(parties, 'guarantor2').customerType),
        });
        setPartyLoadVersion((value) => value + 1);
      })
      .catch(() => {
        if (active) setDraftMessage('Unable to load saved party details.');
      })
      .finally(() => {
        finishDraftLoad();
      });

    return () => {
      active = false;
      finishDraftLoad();
    };
  }, [draftContract.contractId]);

  useEffect(() => {
    if (!draftContract.contractId) return undefined;

    let active = true;
    const finishDraftLoad = startDraftLoad();
    fetchContractFinancialDraft(draftContract.contractId)
      .then((financial) => {
        if (!active) return;
        const nextFinancial = financial || {};
        setFinancialDetails(nextFinancial);
        setRepaymentRows(repaymentRowsFrom(nextFinancial, parseRepaymentSchedule(selectedContract.repaymentSchedule)));
        setFinancialInputs({
          irrRate: displayValue(nextFinancial.irrRate || selectedContract.irrRate),
          loanAmount: displayValue(nextFinancial.loanAmount || selectedContract.loanAmount),
          totalContractValue: displayValue(nextFinancial.totalContractValue || selectedContract.totalContractValue),
        });
        setFinancialLoadVersion((value) => value + 1);
      })
      .catch(() => {
        if (active) setDraftMessage('Unable to load saved financial terms.');
      })
      .finally(() => {
        finishDraftLoad();
      });

    return () => {
      active = false;
      finishDraftLoad();
    };
  }, [draftContract.contractId, selectedContract.repaymentSchedule]);

  useEffect(() => {
    const nextTotalContractValue = formatMoney(totalRepaymentValue(repaymentRows));
    

    setFinancialInputs((current) => ({
      ...current,
      
      totalContractValue: nextTotalContractValue || current.totalContractValue,
    }));
  }, [financialInputs.loanAmount, repaymentRows]);

  useEffect(() => {
    if (!draftContract.contractId) return undefined;

    let active = true;
    const finishDraftLoad = startDraftLoad();
    fetchContractDocumentationDraft(draftContract.contractId)
      .then((documentation) => {
        if (!active) return;
        const nextDocumentation = {
          ...(documentation || {}),
          areaCode: cleanContractAreaCode(documentation?.areaCode) || currentContractAreaCode(selectedContract),
        };
        setDocumentationDetails(nextDocumentation);
        setPendingDocuments(pendingDocumentsFrom(nextDocumentation));
        setDocumentUploads(uploadsFrom(nextDocumentation));
        setDocumentationLoadVersion((value) => value + 1);
      })
      .catch(() => {
        if (active) setDraftMessage('Unable to load saved documentation details.');
      })
      .finally(() => {
        finishDraftLoad();
      });

    return () => {
      active = false;
      finishDraftLoad();
    };
  }, [draftContract.contractId]);

  useEffect(() => {
    if (!draftContract.contractId) return undefined;

    let active = true;
    const finishDraftLoad = startDraftLoad();
    fetchContractAssetDraft(draftContract.contractId)
      .then((asset) => {
        if (!active) return;
        const nextAsset = asset || {};
        setAssetDetails(nextAsset);
        setAssetSecured(nextAsset.assetSecured || 'Secured');
        setProductType(nextAsset.productType || '');
        setProposalCat(nextAsset.proposalCategory || '-');
        setRiskLevel(nextAsset.riskLevel || '-');
        setAssetLoadVersion((value) => value + 1);
      })
      .catch(() => {
        if (active) setDraftMessage('Unable to load saved asset details.');
      })
      .finally(() => {
        finishDraftLoad();
      });

    return () => {
      active = false;
      finishDraftLoad();
    };
  }, [draftContract.contractId]);

  useEffect(() => {
    if (!draftContract.contractId) return undefined;

    let active = true;
    const finishDraftLoad = startDraftLoad();
    fetchContractCoLendingDraft(draftContract.contractId)
      .then((coLending) => {
        if (!active) return;
        setCoLendingDetails(coLending || {});
        setCoLendingLoadVersion((value) => value + 1);
      })
      .catch(() => {
        if (active) setDraftMessage('Unable to load saved co lending details.');
      })
      .finally(() => {
        finishDraftLoad();
      });

    return () => {
      active = false;
      finishDraftLoad();
    };
  }, [draftContract.contractId]);

  const tabs = [
    { name: 'Borrower Details', icon: <User size={14} />, active: 'bg-blue-600', inactive: 'bg-blue-100', border: 'border-blue-400' },
    { name: 'Asset Details', icon: <Car size={14} />, active: 'bg-emerald-600', inactive: 'bg-emerald-100', border: 'border-emerald-400' },
    { name: 'Financial Terms', icon: <IndianRupee size={14} />, active: 'bg-amber-600', inactive: 'bg-amber-100', border: 'border-amber-400' },
    { name: 'Documentation Details', icon: <FileText size={14} />, active: 'bg-purple-600', inactive: 'bg-purple-100', border: 'border-purple-400' },
    { name: 'Co Lending Details', icon: <Share2 size={14} />, active: 'bg-cyan-600', inactive: 'bg-cyan-100', border: 'border-cyan-400' }
  ];

  const handleTypeChange = (key, value) => {
    setTypes(prev => ({ ...prev, [key]: value }));
  };

  const buildPartyDraft = (formData, key, role) => ({
    role,
    partyCode: partyCodes[key],
    customerType: normalizeCustomerType(formValue(formData, `${key}.customerType`)),
    salutation: formValue(formData, `${key}.salutation`),
    fullName: formValue(formData, `${key}.fullName`),
    firmName: formValue(formData, `${key}.firmName`),
    partner1Name: formValue(formData, `${key}.partner1Name`),
    partner2Name: formValue(formData, `${key}.partner2Name`),
    swdName: formValue(formData, `${key}.swdName`),
    addressLine1: formValue(formData, `${key}.addressLine1`),
    addressLine2: formValue(formData, `${key}.addressLine2`),
    area: formValue(formData, `${key}.area`),
    city: formValue(formData, `${key}.city`),
    state: formValue(formData, `${key}.state`),
    pinCode: formValue(formData, `${key}.pinCode`),
    contactNumber: formValue(formData, `${key}.contactNumber`),
    alternativeNumber: formValue(formData, `${key}.alternativeNumber`),
    emailId: formValue(formData, `${key}.emailId`),
    dateOfBirth: formValue(formData, `${key}.dateOfBirth`),
    panNumber: formValue(formData, `${key}.panNumber`),
    aadhaarNumber: formValue(formData, `${key}.aadhaarNumber`),
    occupation: formValue(formData, `${key}.occupation`),
    annualIncome: formValue(formData, `${key}.annualIncome`),
    residenceType: formValue(formData, `${key}.residenceType`),
    distanceKm: formValue(formData, `${key}.distanceKm`),
  });

  const saveBorrowerDraft = async () => {
    if (isViewMode) return;
    validateContractHeader();

    const formContainer = document.querySelector('[data-contract-form-scroll]');
    const invalidField = formContainer?.querySelector(':invalid');
    if (invalidField) {
      invalidField.reportValidity();
      invalidField.focus();
      throw new Error(validationMessageFor(formContainer, 'Borrower details validation failed.'));
    }

    const formData = formValuesFrom(formContainer);
    const response = await saveContractPartyDraft({
      contractId: draftContract.contractId,
      contractNumber: draftContract.contractNumber,
      contractDate: parseDateDisplay(draftContract.contractDate, 'Contract Date'),
      parties: [
        buildPartyDraft(formData, 'primary', 'applicant'),
        buildPartyDraft(formData, 'coApp', 'coApplicant'),
        buildPartyDraft(formData, 'g1', 'guarantor1'),
        buildPartyDraft(formData, 'g2', 'guarantor2'),
      ],
    });
    const results = response.parties || [];

    const nextCodes = { ...partyCodes };
    results.forEach((result) => {
      if (result.role === 'applicant') nextCodes.primary = result.partyCode;
      if (result.role === 'coApplicant') nextCodes.coApp = result.partyCode;
      if (result.role === 'guarantor1') nextCodes.g1 = result.partyCode;
      if (result.role === 'guarantor2') nextCodes.g2 = result.partyCode;
    });
    setPartyCodes(nextCodes);
    setDraftContract({
      contractId: response.contractId || draftContract.contractId,
      contractNumber: response.contractNumber || draftContract.contractNumber,
      contractDate: formatDateDisplay(response.contractDate || draftContract.contractDate),
    });
    setDraftMessage(`Draft contract ${response.contractNumber || draftContract.contractNumber || ''} saved: ${results.map((result) => result.partyCode).join(', ')}`);
    return response.contractId || draftContract.contractId;
  };

  const saveHeaderDraft = async () => {
    if (isViewMode || !draftContract.contractId) return draftContract.contractId;
    validateContractHeader();
    const response = await saveContractHeaderDraft(draftContract.contractId, {
      contractId: draftContract.contractId,
      contractNumber: draftContract.contractNumber,
      contractDate: parseDateDisplay(draftContract.contractDate, 'Contract Date'),
    });
    setDraftContract((current) => ({
      ...current,
      contractNumber: response.contractNumber || current.contractNumber,
      contractDate: formatDateDisplay(response.contractDate || current.contractDate),
    }));
    return draftContract.contractId;
  };

  const buildAssetDraft = (formData) => ({
    assetSecured: formValue(formData, 'asset.assetSecured') || assetSecured,
    productType: formValue(formData, 'asset.productType') || productType,
    vehicleTypeCode: formValue(formData, 'asset.vehicleTypeCode'),
    dealOfAssets: formValue(formData, 'asset.dealOfAssets'),
    vehicleMake: formValue(formData, 'asset.vehicleMake'),
    version: formValue(formData, 'asset.version'),
    manufactureYear: formValue(formData, 'asset.manufactureYear'),
    ownerSerialNo: formValue(formData, 'asset.ownerSerialNo'),
    registrationNumber: formValue(formData, 'asset.registrationNumber'),
    fuelType: formValue(formData, 'asset.fuelType'),
    kmsRun: numberFormValue(formData, 'asset.kmsRun'),
    marketValue: numberFormValue(formData, 'asset.marketValue'),
    chassisNumber: formValue(formData, 'asset.chassisNumber'),
    engineNumber: formValue(formData, 'asset.engineNumber'),
    idv: numberFormValue(formData, 'asset.idv'),
    insuranceExpiry: formValue(formData, 'asset.insuranceExpiry'),
    insuranceCompany: formValue(formData, 'asset.insuranceCompany'),
    insurancePremium: numberFormValue(formData, 'asset.insurancePremium'),
    propertyType: formValue(formData, 'asset.propertyType'),
    flatNo: formValue(formData, 'asset.flatNo'),
    apartmentNo: formValue(formData, 'asset.apartmentNo'),
    streetName: formValue(formData, 'asset.streetName'),
    areaName: formValue(formData, 'asset.areaName'),
    propertyCity: formValue(formData, 'asset.propertyCity'),
    propertyState: formValue(formData, 'asset.propertyState'),
    distanceFromOffice: numberFormValue(formData, 'asset.distanceFromOffice'),
    anyRentReceived: formValue(formData, 'asset.anyRentReceived'),
    rentAmount: numberFormValue(formData, 'asset.rentAmount'),
    guidelineValue: numberFormValue(formData, 'asset.guidelineValue'),
    natureOfBusiness: formValue(formData, 'asset.natureOfBusiness'),
    dateOfIncorporation: formValue(formData, 'asset.dateOfIncorporation'),
    isSecured: formValue(formData, 'asset.isSecured'),
    proposalCategory: proposalCat,
    riskLevel,
  });

  const validateVisibleFields = () => {
    const formContainer = document.querySelector('[data-contract-form-scroll]');
    const invalidField = formContainer?.querySelector(':invalid');
    if (invalidField) {
      invalidField.reportValidity();
      invalidField.focus();
      throw new Error(validationMessageFor(formContainer, 'Contract form validation failed.'));
    }
    return formContainer;
  };

  const saveAssetDraft = async () => {
    if (isViewMode) return;
    if (!draftContract.contractId) {
      throw new Error('Save borrower details before asset details.');
    }

    const formContainer = validateVisibleFields();
    const response = await saveContractAssetDraft(
      draftContract.contractId,
      buildAssetDraft(formValuesFrom(formContainer))
    );
    setAssetDetails(response || {});
    setAssetSecured(response?.assetSecured || assetSecured);
    setProductType(response?.productType || productType);
    setAssetLoadVersion((value) => value + 1);
    setDraftMessage(`Asset details saved for draft contract ${draftContract.contractNumber || draftContract.contractId}.`);
    return draftContract.contractId;
  };

  const buildFinancialDraft = (formData) => ({
    loanAmount: numberFormValue(formData, 'financial.loanAmount'),
    tenureMonths: integerFormValue(formData, 'financial.tenureMonths'),
    flatInterestRate: numberFormValue(formData, 'financial.flatInterestRate'),
    irrRate: numberStateValue(financialInputs.irrRate) || numberFormValue(formData, 'financial.irrRate'),
    insuranceDeposit: numberFormValue(formData, 'financial.insuranceDeposit'),
    totalContractValue: numberStateValue(financialInputs.totalContractValue) || numberFormValue(formData, 'financial.totalContractValue'),
    repaymentTerms: formValue(formData, 'financial.repaymentTerms'),
    isFirstEmiPaid: formValue(formData, 'financial.isFirstEmiPaid') === 'Yes',
    firstEmiDate: parseDateDisplay(formValue(formData, 'financial.firstEmiDate'), 'First EMI Date'),
    moratoriumMonths: moratoriumMonths(formValue(formData, 'financial.moratorium')),
    repaymentType: formValue(formData, 'financial.repaymentType'),
    emiAdvance: numberFormValue(formData, 'financial.emiAdvance'),
    processingCharges: numberFormValue(formData, 'financial.processingCharges'),
    rtoCharges: numberFormValue(formData, 'financial.rtoCharges'),
    valuationCharges: numberFormValue(formData, 'financial.valuationCharges'),
    stampDuty: numberFormValue(formData, 'financial.stampDuty'),
    rcHoldingAmount: numberFormValue(formData, 'financial.rcHoldingAmount'),
    otherCharges: numberFormValue(formData, 'financial.otherCharges'),
    modeOfPayment: formValue(formData, 'financial.modeOfPayment'),
    paymentDoneTo: formValue(formData, 'financial.paymentDoneTo'),
    payee1: formValue(formData, 'financial.payee1'),
    payee2: formValue(formData, 'financial.payee2'),
    payee3: formValue(formData, 'financial.payee3'),
    repaymentStructures: repaymentRows
      .map((row, index) => ({
        sequenceNo: index + 1,
        numberOfInstallments: row.numberOfInstallments ? Number.parseInt(row.numberOfInstallments, 10) : null,
        installmentAmount: row.installmentAmount || null,
      }))
      .filter((row) => row.numberOfInstallments !== null || row.installmentAmount !== null),
  });

  const saveFinancialDraft = async () => {
    if (isViewMode) return;
    validateContractHeader();
    if (!draftContract.contractId) {
      throw new Error('Save borrower details before financial terms.');
    }

    const formContainer = validateVisibleFields();
    if (!formValue(formValuesFrom(formContainer), 'financial.firstEmiDate')) {
      throw new Error('First EMI Date is required.');
    }
    validateFirstEmiDate(formValuesFrom(formContainer));
    const response = await saveContractFinancialDraft(
      draftContract.contractId,
      buildFinancialDraft(formValuesFrom(formContainer))
    );
    const nextRepaymentRows = repaymentRowsFrom(response || {}, []);
    setFinancialDetails(response || {});
    setRepaymentRows((currentRows) => repaymentRowsEqual(currentRows, nextRepaymentRows) ? currentRows : nextRepaymentRows);
    setFinancialInputs((current) => ({
      ...current,
      irrRate: displayValue(response?.irrRate ?? current.irrRate),
      loanAmount: displayValue(response?.loanAmount ?? current.loanAmount),
      totalContractValue: displayValue(response?.totalContractValue ?? current.totalContractValue),
    }));
    setFinancialLoadVersion((value) => value + 1);
    setDraftMessage(`Financial terms saved for draft contract ${draftContract.contractNumber || draftContract.contractId}.`);
    return draftContract.contractId;
  };

  const validateContractHeader = () => {
    if (!draftContract.contractNumber || !draftContract.contractNumber.trim()) {
      throw new Error('Contract number is required.');
    }
    if (draftContract.contractDate) {
      parseDateDisplay(draftContract.contractDate, 'Contract Date');
    }
  };

  const validateFirstEmiDate = (formData) => {
    const firstEmiDate = parseDateDisplay(formValue(formData, 'financial.firstEmiDate'), 'First EMI Date');
    const contractDate = parseDateDisplay(draftContract.contractDate, 'Contract Date');
    if (contractDate && firstEmiDate < contractDate) {
      throw new Error('First EMI Date must be greater than or equal to Contract Date.');
    }
  };

  const addRepaymentRow = () => {
    setRepaymentRows((rows) => [
      ...rows,
      { id: `${Date.now()}-${rows.length}`, installmentAmount: '', numberOfInstallments: '' },
    ]);
  };

  const updateRepaymentRow = (id, field, value) => {
    setRepaymentRows((rows) => rows.map((row) => (row.id === id ? { ...row, [field]: value } : row)));
  };

  const removeRepaymentRow = (id) => {
    setRepaymentRows((rows) => rows.length <= 1 ? rows : rows.filter((row) => row.id !== id));
  };

  const buildDocumentationDraft = (formData) => ({
    documentType: formValue(formData, 'documentation.documentType'),
    documentVerifiedBy: formValue(formData, 'documentation.documentVerifiedBy'),
    documentsObtainedBy: formValue(formData, 'documentation.documentsObtainedBy'),
    guarantorFiBy: formValue(formData, 'documentation.guarantorFiBy'),
    borrowerFiBy: formValue(formData, 'documentation.borrowerFiBy'),
    loanReferredBy: formValue(formData, 'documentation.loanReferredBy'),
    tvrDoneBy: formValue(formData, 'documentation.tvrDoneBy'),
    vehicleByAgency: formValue(formData, 'documentation.vehicleByAgency'),
    vehicleInspectionBy: formValue(formData, 'documentation.vehicleInspectionBy'),
    propertyValuationBy: formValue(formData, 'documentation.propertyValuationBy'),
    legalOpinionBy: formValue(formData, 'documentation.legalOpinionBy'),
    branchCollectionToolBy: formValue(formData, 'documentation.branchCollectionToolBy'),
    documentsCheckedBy: formValue(formData, 'documentation.documentsCheckedBy'),
    documentsVerifiedBy: formValue(formData, 'documentation.documentsVerifiedBy'),
    loanApprovedBy: formValue(formData, 'documentation.loanApprovedBy'),
    disbursedBy: formValue(formData, 'documentation.disbursedBy'),
    rcOnlineChecking: formValue(formData, 'documentation.rcOnlineChecking'),
    hoCollectionToolBy: formValue(formData, 'documentation.hoCollectionToolBy'),
    areaCode: cleanContractAreaCode(documentationDetails.areaCode) || null,
    hoTvrDoneBy: formValue(formData, 'documentation.hoTvrDoneBy'),
    stockMarkedToBank: formValue(formData, 'documentation.stockMarkedToBank'),
    pendingDocuments,
    documentUploads: documentUploads.map(({ id, previewUrl, uploadProgress, ...upload }) => upload),
  });

  const saveDocumentationDraft = async () => {
    if (isViewMode) return;
    if (!draftContract.contractId) {
      throw new Error('Save borrower details before documentation details.');
    }

    const formContainer = validateVisibleFields();
    const response = await saveContractDocumentationDraft(
      draftContract.contractId,
      buildDocumentationDraft(formValuesFrom(formContainer))
    );
    setDocumentationDetails(response || {});
    setPendingDocuments(pendingDocumentsFrom(response || {}));
    setDocumentUploads(uploadsFrom(response || {}));
    setDocumentationLoadVersion((value) => value + 1);
    setDraftMessage(`Documentation details saved for draft contract ${draftContract.contractNumber || draftContract.contractId}.`);
    return draftContract.contractId;
  };

  const buildCoLendingDraft = (formData) => ({
    coLendingType: formValue(formData, 'coLending.coLendingType'),
    coLenderName: formValue(formData, 'coLending.coLenderName'),
    securityDepositAmount: numberFormValue(formData, 'coLending.securityDepositAmount'),
    contributionSharePercent: numberFormValue(formData, 'coLending.contributionSharePercent'),
    emiSharePercent: numberFormValue(formData, 'coLending.emiSharePercent'),
    revenueSharePercent: numberFormValue(formData, 'coLending.revenueSharePercent'),
    riskSharePercent: numberFormValue(formData, 'coLending.riskSharePercent'),
  });

  const saveCoLendingDraft = async () => {
    if (isViewMode) return;
    if (!draftContract.contractId) {
      throw new Error('Save borrower details before co lending details.');
    }

    const formContainer = validateVisibleFields();
    const response = await saveContractCoLendingDraft(
      draftContract.contractId,
      buildCoLendingDraft(formValuesFrom(formContainer))
    );
    setCoLendingDetails(response || {});
    setCoLendingLoadVersion((value) => value + 1);
    setDraftMessage(`Co lending details saved for draft contract ${draftContract.contractNumber || draftContract.contractId}.`);
    return draftContract.contractId;
  };

  const togglePendingDocument = (documentName) => {
    setPendingDocuments((items) => items.map((item) => (
      item.documentName === documentName ? { ...item, pending: !item.pending } : item
    )));
  };

  const addDocumentUpload = () => {
    if (!uploadDocumentCategory || selectedUploadFiles.length === 0 || isViewMode) return;
    const createdRows = selectedUploadFiles.map((file, index) => ({
      id: `${Date.now()}-${index}-${file.name}`,
      documentCategory: uploadDocumentCategory,
      fileName: file.name,
      fileSize: file.size,
      contentType: file.type || '',
      storagePath: '',
      previewUrl: URL.createObjectURL(file),
      uploadProgress: 0,
    }));

    setDocumentUploads((uploads) => [...uploads, ...createdRows]);

    createdRows.forEach((row, rowIndex) => {
      let progress = 0;
      const timer = window.setInterval(() => {
        progress = Math.min(100, progress + 20 + rowIndex * 3);
        setDocumentUploads((uploads) => uploads.map((upload) => (
          upload.id === row.id ? { ...upload, uploadProgress: progress } : upload
        )));
        if (progress >= 100) {
          window.clearInterval(timer);
        }
      }, 140);
    });

    setUploadDocumentCategory('');
    setSelectedUploadFiles([]);
    setUploadInputKey((value) => value + 1);
  };

  const removeDocumentUpload = (id) => {
    setDocumentUploads((uploads) => {
      const removing = uploads.find((upload) => upload.id === id);
      revokeUploadPreview(removing);
      return uploads.filter((upload) => upload.id !== id);
    });
    setActivePreviewIndex((index) => {
      if (index === null) return null;
      const nextUploads = documentUploads.filter((upload) => upload.id !== id);
      return nextUploads.length === 0 ? null : Math.min(index, nextUploads.length - 1);
    });
  };

  const openDocumentPreview = (id) => {
    const index = documentUploads.findIndex((upload) => upload.id === id);
    if (index >= 0) setActivePreviewIndex(index);
  };

  const showPreviousDocument = () => {
    setActivePreviewIndex((index) => {
      if (index === null || documentUploads.length === 0) return null;
      return (index - 1 + documentUploads.length) % documentUploads.length;
    });
  };

  const showNextDocument = () => {
    setActivePreviewIndex((index) => {
      if (index === null || documentUploads.length === 0) return null;
      return (index + 1) % documentUploads.length;
    });
  };

  const saveCurrentTabDraft = async () => {
    if (activeTab === 'Borrower Details') {
      return saveBorrowerDraft();
    }
    await saveHeaderDraft();
    if (activeTab === 'Asset Details') {
      return saveAssetDraft();
    }
    if (activeTab === 'Financial Terms') {
      return saveFinancialDraft();
    }
    if (activeTab === 'Documentation Details') {
      return saveDocumentationDraft();
    }
    if (activeTab === 'Co Lending Details') {
      return saveCoLendingDraft();
    }
    return draftContract.contractId;
  };

  const handleSubmitWorkflow = async () => {
    if (isViewMode) return;

    try {
      setSavingDraft(true);
      setDraftMessage('');
      const submittedContractId = await saveCurrentTabDraft();

      if (!submittedContractId) {
        throw new Error('Contract draft was not created.');
      }

      await updateContractStatus(submittedContractId, contractStatus);
      setDraftMessage('Contract status updated.');
    } catch (error) {
      setDraftMessage(errorMessage(error, 'Unable to submit contract. Please check the entered values and try again.'));
    } finally {
      setSavingDraft(false);
    }
  };

  const handleNext = async () => {
    const currentIndex = tabs.findIndex(tab => tab.name === activeTab);
    if (currentIndex < tabs.length - 1) {
      try {
        setDraftMessage('');
        if ((activeTab === 'Borrower Details' || activeTab === 'Asset Details' || activeTab === 'Financial Terms' || activeTab === 'Documentation Details' || activeTab === 'Co Lending Details') && !isViewMode) {
          setSavingDraft(true);
          await saveCurrentTabDraft();
        }
        setActiveTab(tabs[currentIndex + 1].name);
        document.querySelector('[data-contract-form-scroll]')?.scrollTo({ top: 0, behavior: 'smooth' });
      } catch (error) {
        setDraftMessage(errorMessage(error, 'Unable to save draft. Please check the entered values and try again.'));
      } finally {
        setSavingDraft(false);
      }
    }
  };

  const handlePrev = () => {
    const currentIndex = tabs.findIndex(tab => tab.name === activeTab);
    if (currentIndex > 0) {
      setActiveTab(tabs[currentIndex - 1].name);
      document.querySelector('[data-contract-form-scroll]')?.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const indianStates = [
    "Andaman and Nicobar Islands",
    "Andhra Pradesh",
    "Arunachal Pradesh",
    "Assam",
    "Bihar",
    "Chandigarh",
    "Chhattisgarh",
    "Dadra and Nagar Haveli and Daman and Diu",
    "Delhi",
    "Goa",
    "Gujarat",
    "Haryana",
    "Himachal Pradesh",
    "Jammu and Kashmir",
    "Jharkhand",
    "Karnataka",
    "Kerala",
    "Ladakh",
    "Lakshadweep",
    "Madhya Pradesh",
    "Maharashtra",
    "Manipur",
    "Meghalaya",
    "Mizoram",
    "Nagaland",
    "Odisha",
    "Puducherry",
    "Punjab",
    "Rajasthan",
    "Sikkim",
    "Tamil Nadu",
    "Telangana",
    "Tripura",
    "Uttar Pradesh",
    "Uttarakhand",
    "West Bengal",
  ];
  const vehicleTypeOptions = ["Private Car", "Taxi Permit", "Three Wheeler", "SCV", "MCV", "HCV", "Others"];
  const applicantParty = partyByRole(partyDetails, 'applicant');
  const coApplicantParty = partyByRole(partyDetails, 'coApplicant');
  const guarantor1Party = partyByRole(partyDetails, 'guarantor1');
  const guarantor2Party = partyByRole(partyDetails, 'guarantor2');
  const partyData = (party, fallback = {}) => ({
    aadhaarNumber: party.aadhaarNumber,
    address: party.addressLine2 || fallback.address,
    addressLine1: party.addressLine1,
    alternativeNumber: party.alternativeNumber,
    annualIncome: party.annualIncome,
    area: party.area,
    city: party.city || fallback.city,
    contactNumber: party.contactNumber,
    dateOfBirth: party.dateOfBirth,
    distanceKm: party.distanceKm,
    emailId: party.emailId,
    firmName: party.firmName,
    name: party.fullName || fallback.name,
    occupation: party.occupation,
    pan: party.panNumber || fallback.pan,
    partner1Name: party.partner1Name,
    partner2Name: party.partner2Name,
    pinCode: party.pinCode,
    residenceType: party.residenceType,
    salutation: party.salutation,
    state: party.state || fallback.state,
    swdName: party.swdName,
  });
  const borrowerData = {
    address: selectedContract.customerAddress,
    city: selectedContract.customerCity,
    name: selectedContract.customerName,
    pan: selectedContract.customerPanNumber,
    state: selectedContract.customerState,
  };
  const guarantorData = {
    address: selectedContract.guarantorAddress,
    city: selectedContract.guarantorCity,
    name: selectedContract.guarantorName,
    pan: selectedContract.guarantorPanNumber,
    state: selectedContract.guarantorState,
  };
  const applicantData = partyData(applicantParty, borrowerData);
  const coApplicantData = partyData(coApplicantParty);
  const guarantor1Data = partyData(guarantor1Party, guarantorData);
  const guarantor2Data = partyData(guarantor2Party);
  const repaymentSchedule = parseRepaymentSchedule(selectedContract.repaymentSchedule);
  const assetData = {
    assetSecured: assetDetails.assetSecured || selectedContract.vehicleSecurityOffered || 'Secured',
    productType: assetDetails.productType || productType,
    vehicleTypeCode: assetDetails.vehicleTypeCode || selectedContract.vehicleTypeCode,
    dealOfAssets: assetDetails.dealOfAssets || selectedContract.vehicleFinanceType,
    vehicleMake: assetDetails.vehicleMake || selectedContract.vehicleMake,
    version: assetDetails.version || selectedContract.equipmentModel,
    manufactureYear: assetDetails.manufactureYear || selectedContract.manufactureYear,
    ownerSerialNo: assetDetails.ownerSerialNo || selectedContract.vehicleOwnerSerialNo,
    registrationNumber: assetDetails.registrationNumber || selectedContract.vehicleRegistrationNumber,
    fuelType: assetDetails.fuelType,
    kmsRun: assetDetails.kmsRun,
    marketValue: assetDetails.marketValue || selectedContract.vehicleValue,
    chassisNumber: assetDetails.chassisNumber || selectedContract.chassisNumber,
    engineNumber: assetDetails.engineNumber || selectedContract.engineNumber,
    idv: assetDetails.idv,
    insuranceExpiry: formatDateInput(assetDetails.insuranceExpiry),
    insuranceCompany: assetDetails.insuranceCompany,
    insurancePremium: assetDetails.insurancePremium,
    propertyType: assetDetails.propertyType,
    flatNo: assetDetails.flatNo,
    apartmentNo: assetDetails.apartmentNo,
    streetName: assetDetails.streetName,
    areaName: assetDetails.areaName,
    propertyCity: assetDetails.propertyCity,
    propertyState: assetDetails.propertyState,
    distanceFromOffice: assetDetails.distanceFromOffice,
    anyRentReceived: assetDetails.anyRentReceived,
    rentAmount: assetDetails.rentAmount,
    guidelineValue: assetDetails.guidelineValue,
    natureOfBusiness: assetDetails.natureOfBusiness,
    dateOfIncorporation: formatDateInput(assetDetails.dateOfIncorporation),
    isSecured: assetDetails.isSecured,
  };
  const financialData = {
    emiAdvance: financialDetails.emiAdvance,
    flatInterestRate: financialDetails.flatInterestRate,
    insuranceDeposit: financialDetails.insuranceDeposit || selectedContract.insuranceDeposit,
    irrRate: financialInputs.irrRate,
    loanAmount: financialInputs.loanAmount,
    modeOfPayment: financialDetails.modeOfPayment || selectedContract.modeOfPayment,
    moratorium: moratoriumLabel(financialDetails.moratoriumMonths),
    otherCharges: financialDetails.otherCharges,
    payee1: financialDetails.payee1,
    payee2: financialDetails.payee2,
    payee3: financialDetails.payee3,
    paymentDoneTo: financialDetails.paymentDoneTo,
    processingCharges: financialDetails.processingCharges,
    rcHoldingAmount: financialDetails.rcHoldingAmount,
    repaymentTerms: financialDetails.repaymentTerms,
    isFirstEmiPaid: booleanValue(financialDetails.isFirstEmiPaid ?? selectedContract.isFirstEmiPaid),
    firstEmiDate: formatDateDisplay(financialDetails.firstEmiDate || selectedContract.firstEmiDate),
    repaymentType: financialDetails.repaymentType,
    rtoCharges: financialDetails.rtoCharges,
    stampDuty: financialDetails.stampDuty,
    tenureMonths: financialDetails.tenureMonths || selectedContract.tenureMonths,
    totalContractValue: financialInputs.totalContractValue,
    valuationCharges: financialDetails.valuationCharges,
  };
  const firstEmiAmount = displayValue(repaymentRows[0]?.installmentAmount);
  const documentationData = {
    areaCode: cleanContractAreaCode(documentationDetails.areaCode) || currentContractAreaCode(selectedContract),
    borrowerFiBy: documentationDetails.borrowerFiBy,
    branchCollectionToolBy: documentationDetails.branchCollectionToolBy,
    disbursedBy: documentationDetails.disbursedBy,
    documentType: documentationDetails.documentType,
    documentVerifiedBy: documentationDetails.documentVerifiedBy,
    documentsCheckedBy: documentationDetails.documentsCheckedBy,
    documentsObtainedBy: documentationDetails.documentsObtainedBy,
    documentsVerifiedBy: documentationDetails.documentsVerifiedBy,
    guarantorFiBy: documentationDetails.guarantorFiBy,
    hoCollectionToolBy: documentationDetails.hoCollectionToolBy,
    hoTvrDoneBy: documentationDetails.hoTvrDoneBy,
    legalOpinionBy: documentationDetails.legalOpinionBy,
    loanApprovedBy: documentationDetails.loanApprovedBy,
    loanReferredBy: documentationDetails.loanReferredBy,
    propertyValuationBy: documentationDetails.propertyValuationBy,
    rcOnlineChecking: documentationDetails.rcOnlineChecking,
    stockMarkedToBank: documentationDetails.stockMarkedToBank,
    tvrDoneBy: documentationDetails.tvrDoneBy,
    vehicleByAgency: documentationDetails.vehicleByAgency,
    vehicleInspectionBy: documentationDetails.vehicleInspectionBy,
  };
  const activePreview = activePreviewIndex === null ? null : documentUploads[activePreviewIndex];
  const activePreviewSrc = activePreview?.previewUrl || activePreview?.storagePath;
  const visibleContractNumber = draftContract.contractNumber || displayContractNumber(selectedContract);
  const showBusyOverlay = savingDraft || loadingDraftCount > 0;
  const busyOverlayLabel = savingDraft ? 'Saving contract...' : 'Loading contract...';

  return (
    <div key={`${selectedContract.contractId || 'new'}-${formMode}`} className="h-full min-h-0 w-full bg-[#F0F2F5] text-black font-sans flex flex-col relative overflow-hidden">
      {showBusyOverlay && (
        <div className="fixed inset-0 z-[300] flex items-center justify-center bg-black/25 backdrop-blur-[1px]">
          <div className="flex min-w-[220px] flex-col items-center gap-3 rounded-md border border-blue-200 bg-white px-8 py-6 shadow-2xl">
            <Loader2 size={38} className="animate-spin text-blue-800" strokeWidth={2.5} />
            <div className="text-[13px] font-black uppercase tracking-wide text-blue-900">
              {busyOverlayLabel}
            </div>
          </div>
        </div>
      )}
      
      {/* 1. TOP ACTION BAR (Sticky) */}
      <div 
        className="shrink-0 bg-white border-b border-black/60 px-3 py-1.5 flex items-center justify-between gap-2 shadow-md z-[100]"
        style={{ fontFamily: 'Calibri, Candara, Segoe UI, Optima, Arial, sans-serif' }}
      >
        <div className="flex min-w-0 items-center gap-2">
          <button 
            onClick={() => navigate(-1)} 
            className="hover:bg-blue-50 p-1 rounded-full transition-colors border border-transparent hover:border-black/10"
          >
            <ArrowLeft size={17} className="text-black/90" />
          </button>
          
          <h1 className="text-[16px] font-bold text-blue-900 whitespace-nowrap">
            {isCreateMode ? 'Create Contract' : isViewMode ? 'View Contract' : 'Edit Contract'}
          </h1>

          <div className="flex items-center overflow-hidden border border-blue-900 rounded shadow-sm">
            <span className="bg-blue-900 text-white px-2 h-8 flex items-center text-[11px] font-black uppercase tracking-tight">
              Contract No
            </span>
            <input
              value={draftContract.contractNumber || ''}
              onChange={(event) => setDraftContract((current) => ({ ...current, contractNumber: event.target.value }))}
              placeholder={visibleContractNumber === 'New' ? 'Auto' : visibleContractNumber}
              readOnly={isViewMode}
              required
              className="h-8 w-28 bg-white px-2 text-[15px] font-black text-blue-900 outline-none read-only:bg-slate-100"
            />
          </div>

          <div className="flex items-center overflow-hidden border border-slate-500 rounded shadow-sm">
            <span className="bg-slate-700 text-white px-2 h-8 flex items-center text-[11px] font-black uppercase tracking-tight">
              Contract Date
            </span>
            <DatePickerField
              label="Contract Date"
              value={draftContract.contractDate || ''}
              onChange={(event) => setDraftContract((current) => ({ ...current, contractDate: event.target.value }))}
              readOnly={isViewMode}
              compact
            />
          </div>

          <h1 className="hidden">
            Application Number — #100254
          </h1>
          
          <div className="h-6 w-[1px] bg-black/20 mx-1"></div>
          
          <div className="flex items-center gap-2">
  {/* Proposal Category - High Impact Green */}
  <div className="flex items-center overflow-hidden border border-emerald-600 rounded shadow-sm">
    <span className="bg-emerald-600 text-white px-2 h-8 flex items-center text-[13px] font-black uppercase tracking-tight">
      Category
    </span>
    <select 
      value={proposalCat} 
      onChange={(e) => setProposalCat(e.target.value)} 
      disabled={isViewMode}
      className="text-[16px] font-black px-2 outline-none bg-white h-8 min-w-[52px] text-emerald-900 cursor-pointer hover:bg-emerald-50 transition-colors disabled:cursor-not-allowed disabled:bg-slate-100"
    >
      <option>-</option>
      <option>A</option>
      <option>B</option>
      <option>C</option>
    </select>
  </div>
  
  {/* Risk Level - High Impact Orange/Amber */}
  <div className="flex items-center overflow-hidden border border-amber-500 rounded shadow-sm">
    <span className="bg-amber-500 text-black px-2 h-8 flex items-center text-[13px] font-black uppercase tracking-tight">
      Risk Level
    </span>
    <select 
      value={riskLevel} 
      onChange={(e) => setRiskLevel(e.target.value)} 
      disabled={isViewMode}
      className="text-[16px] font-black px-2 outline-none bg-white h-8 w-[112px] text-amber-900 cursor-pointer hover:bg-amber-50 transition-colors disabled:cursor-not-allowed disabled:bg-slate-100"
    >
      <option>-</option>
      <option>Low</option>
      <option>Medium</option>
      <option>High</option>
    </select>
  </div>
</div>
        </div>

        <div className="flex shrink-0 gap-2 items-center">
          {/* Move to LOS - Light Blue */}
          <button disabled={isViewMode} className="px-3 py-1 bg-blue-50 text-blue-700 border border-blue-500 text-[12px] font-black uppercase flex items-center gap-1.5 hover:bg-blue-100 rounded-sm transition-all disabled:opacity-40">
            <RotateCcw size={14} /> MOVE TO LOS
          </button>

          <div className="flex items-center overflow-hidden border border-slate-500 rounded-sm bg-white shadow-sm">
            <span className="bg-slate-700 text-white px-2 h-8 flex items-center text-[12px] font-black uppercase">
              Status
              <span className="ml-1 text-red-300">*</span>
            </span>
            <select
              value={contractStatus}
              onChange={(event) => setContractStatus(event.target.value)}
              disabled={isViewMode || savingDraft}
              required
              className="h-8 min-w-[120px] bg-white px-2 text-[12px] font-black uppercase text-slate-900 outline-none disabled:cursor-not-allowed disabled:bg-slate-100"
            >
              {(isEditableWorkflow ? STATUS_OPTIONS.edit : STATUS_OPTIONS.create).map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </div>
          
          {/* Submit - Light Green and Larger Size */}
          <button
            type="button"
            disabled={isViewMode || savingDraft}
            onClick={handleSubmitWorkflow}
            className="px-5 py-1 bg-green-100 text-green-700 border border-green-500 text-[13px] font-black uppercase flex items-center gap-1.5 hover:bg-green-200 rounded-sm shadow-md transition-all disabled:opacity-40"
          >
            <CheckCircle size={16} /> {savingDraft ? 'SAVING...' : 'SUBMIT'}
          </button>
        </div>
      </div>

      <MessageModal message={draftMessage} onClose={() => setDraftMessage('')} />

      {/* 2. TAB ROW (Sticky - Offset by Top Bar height) */}
    <div className="shrink-0 bg-white border-b border-black/60 px-2 pt-2 flex items-end gap-1.5 shadow-sm z-[90]">
      {tabs.map((tab) => (
        <button
          key={tab.name}
          onClick={() => setActiveTab(tab.name)}
          style={{ fontFamily: 'Calibri, Candara, Segoe UI, Optima, Arial, sans-serif' }}
          className={`
            flex items-center gap-2 px-6 py-2 text-[16px] transition-all duration-300 ease-in-out
            rounded-t-lg border-t border-x border-black/60 uppercase tracking-wide font-normal
            relative overflow-hidden group
            ${activeTab === tab.name 
              ? "bg-gradient-to-b from-[#0052CC] to-[#003D99] text-white z-10 translate-y-[1px] shadow-[0_-2px_8px_rgba(0,82,204,0.2)]" 
              : "bg-gradient-to-b from-[#E1EFFF] to-[#D6E4FF] text-black hover:from-white hover:to-[#E1EFFF] hover:translate-y-[-1px]"
            }
          `}
        >
          {/* Subtle Shine Effect for Active Tab */}
          {activeTab === tab.name && (
            <span className="absolute inset-0 bg-gradient-to-r from-transparent via-white/5 to-transparent -translate-x-full group-hover:animate-[shimmer_2s_infinite]" />
          )}
          
          <span className={`transition-transform duration-300 ${activeTab === tab.name ? 'scale-105' : 'group-hover:scale-100'}`}>
            {tab.icon}
          </span>
          
          <span className="whitespace-nowrap tracking-tight">
            {tab.name}
          </span>
        </button>
      ))}

      {/* Keyframe for the shine effect */}
      <style dangerouslySetInnerHTML={{ __html: `
        @keyframes shimmer {
          100% { transform: translateX(100%); }
        }
      `}} />
    </div>
      {/* 3. MAIN WORKSPACE (Natural Flow) */}
      <fieldset data-contract-form-scroll disabled={isViewMode} className="flex-1 min-h-0 overflow-y-auto p-2 w-full pb-24">
       {activeTab === 'Borrower Details' && (
    <div 
        key={partyLoadVersion}
        className="max-w-[75%] space-y-8 pb-32 px-4" 
        style={{ 
          fontFamily: 'Calibri, Candara, Segoe UI, Optima, Arial, sans-serif',
          color: 'rgba(0, 0, 0, 0.9)' 
        }}
      >
    <EntityBlock 
      title="Primary Borrower" 
      icon={User} 
      typeKey="primary" 
      colorClass="bg-sky-100 text-black border border-slate-800" 
      customerType={types.primary} 
      onTypeChange={handleTypeChange} 
      indianStates={indianStates} 
      readOnly={isViewMode}
      data={applicantData}
    />
    
    <EntityBlock 
      title="Co-Applicant Details" 
      icon={Users} 
      typeKey="coApp" 
      colorClass="bg-sky-100 text-black border border-slate-400" 
      customerType={types.coApp} 
      onTypeChange={handleTypeChange} 
      indianStates={indianStates} 
      readOnly={isViewMode}
      data={coApplicantData}
    />
    
    <EntityBlock 
      title="Guarantor 01" 
      icon={ShieldCheck} 
      typeKey="g1" 
      colorClass="bg-sky-100 text-black border border-slate-400" 
      customerType={types.g1} 
      onTypeChange={handleTypeChange} 
      indianStates={indianStates} 
      readOnly={isViewMode}
      data={guarantor1Data}
    />
    
    <EntityBlock 
      title="Guarantor 02" 
      icon={ShieldCheck} 
      typeKey="g2" 
      colorClass="bg-sky-100 text-black border border-slate-400" 
      customerType={types.g2} 
      onTypeChange={handleTypeChange} 
      indianStates={indianStates} 
      readOnly={isViewMode}
      data={guarantor2Data}
    />
  </div>
)}

        {activeTab === 'Asset Details' && (
          <div 
            key={assetLoadVersion}
            className="max-w-[75%] space-y-6 pb-32 px-4" 
            style={{ fontFamily: 'Calibri, Candara, Segoe UI, Optima, Arial, sans-serif' }}
          >
            {/* Top Asset/Product Type Selector */}
            <div className="bg-white border border-black/60 rounded-sm p-4 grid grid-cols-1 md:grid-cols-2 gap-4 shadow-sm">
              <FormSelect label="Type of Assets" name="asset.assetSecured" options={["Secured", "UnSecured"]} value={assetSecured} onChange={(e) => setAssetSecured(e.target.value)} readOnly={isViewMode} />
              <FormSelect label="Product Type" name="asset.productType" options={["Vehicles", "MSME", "LAP", "Business Loans", "Collateral"]} value={productType || assetData.productType || ''} onChange={(e) => setProductType(e.target.value)} readOnly={isViewMode} />
            </div>

            {/* VEHICLES SECTION */}
            {(productType || assetData.productType) === 'Vehicles' && (
              <div className="space-y-4">
                {/* Brand Blue Gradient Header */}
                <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
                  <Car size={24} className="text-black/90" />
                  <span className="text-[16px] font-black uppercase tracking-wide">Vehicle Specifications</span>
                </div>
                <div className="bg-white border border-black/60 p-4 grid grid-cols-2 md:grid-cols-4 gap-4 shadow-sm rounded-b-sm">
                  <FormSelect label="Vehicle Type" name="asset.vehicleTypeCode" options={optionsWithCurrentValue(vehicleTypeOptions, assetData.vehicleTypeCode)} value={assetData.vehicleTypeCode || ''} readOnly={isViewMode} />
                  <FormSelect label="Deal of Assets" name="asset.dealOfAssets" options={["Purchase", "Refinance"]} value={assetData.dealOfAssets || ''} readOnly={isViewMode} />
                  <FormField label="Make of Vehicle" name="asset.vehicleMake" value={assetData.vehicleMake} readOnly={isViewMode} />
                  <FormField label="Version" name="asset.version" value={assetData.version} readOnly={isViewMode} />
                  <FormField label="Year of Mfg" name="asset.manufactureYear" value={assetData.manufactureYear} inputMode="numeric" maxLength={4} pattern="[0-9]{4}" title="Year of manufacturing must be 4 digits." readOnly={isViewMode} />
                  <FormField label="Owner serial Number" name="asset.ownerSerialNo" value={assetData.ownerSerialNo} readOnly={isViewMode} />
                  <FormField label="Regn Number" name="asset.registrationNumber" value={assetData.registrationNumber} readOnly={isViewMode} />
                  <FormSelect label="Fuel Type" name="asset.fuelType" options={["Diesel", "Petrol", "EV"]} value={assetData.fuelType || ''} readOnly={isViewMode} />
                  <FormField label="Kms Run" name="asset.kmsRun" value={assetData.kmsRun} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Kms run must be a number with up to 2 decimal places." readOnly={isViewMode} />
                  <FormField label="Market Value" name="asset.marketValue" value={assetData.marketValue} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Market value must be a number with up to 2 decimal places." readOnly={isViewMode} />
                  <FormField label="Chasis Number" name="asset.chassisNumber" value={assetData.chassisNumber} readOnly={isViewMode} />
                  <FormField label="Engine Number" name="asset.engineNumber" value={assetData.engineNumber} readOnly={isViewMode} />
                </div>

                <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
                  <ShieldCheck size={16} className="text-black/90" />
                  <span className="text-[16px] font-black uppercase tracking-wide">Insurance Details</span>
                </div>
                <div className="bg-white border border-black/60 p-4 grid grid-cols-2 md:grid-cols-4 gap-4 shadow-sm rounded-b-sm">
                  <FormField label="IDV" name="asset.idv" value={assetData.idv} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="IDV must be a number with up to 2 decimal places." readOnly={isViewMode} />
                  <FormField label="Expiry" name="asset.insuranceExpiry" type="date" value={assetData.insuranceExpiry} readOnly={isViewMode} />
                  <FormField label="Insurance Company" name="asset.insuranceCompany" value={assetData.insuranceCompany} readOnly={isViewMode} />
                  <FormField label="Premium" name="asset.insurancePremium" value={assetData.insurancePremium} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Premium must be a number with up to 2 decimal places." readOnly={isViewMode} />
                </div>
              </div>
            )}

            {/* LAP SECTION */}
            {productType === 'LAP' && (
              <div className="space-y-4">
                <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
                  <FileText size={16} className="text-black/90" />
                  <span className="text-[16px] font-black uppercase tracking-wide">Property Location Details</span>
                </div>
                <div className="bg-white border border-black/60 p-4 grid grid-cols-2 md:grid-cols-4 gap-4 shadow-sm rounded-b-sm">
                  <FormSelect label="Property Type" name="asset.propertyType" options={["Residential", "Commercial", "Industrial"]} value={assetData.propertyType || ''} readOnly={isViewMode} />
                  <FormField label="Flat No." name="asset.flatNo" value={assetData.flatNo} readOnly={isViewMode} />
                  <FormField label="Apartment No." name="asset.apartmentNo" value={assetData.apartmentNo} readOnly={isViewMode} />
                  <FormField label="Street Name" name="asset.streetName" value={assetData.streetName} readOnly={isViewMode} />
                  <FormField label="Area Name" name="asset.areaName" value={assetData.areaName} readOnly={isViewMode} />
                  <FormField label="City" name="asset.propertyCity" value={assetData.propertyCity} readOnly={isViewMode} />
                  <FormSelect label="State" name="asset.propertyState" options={indianStates} value={assetData.propertyState || ''} readOnly={isViewMode} />
                  <FormField label="Distance from Office" name="asset.distanceFromOffice" value={assetData.distanceFromOffice} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Distance must be a number with up to 2 decimal places." readOnly={isViewMode} />
                  <FormSelect label="Any Rent received" name="asset.anyRentReceived" options={["Yes", "No"]} value={assetData.anyRentReceived || ''} readOnly={isViewMode} />
                  <FormField label="Rent Amount (If yes)" name="asset.rentAmount" value={assetData.rentAmount} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Rent amount must be a number with up to 2 decimal places." readOnly={isViewMode} />
                  <FormField label="Guide Line Value" name="asset.guidelineValue" value={assetData.guidelineValue} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Guideline value must be a number with up to 2 decimal places." className="md:col-span-2" readOnly={isViewMode} />
                </div>
              </div>
            )}

            {/* MSME SECTION */}
            {productType === 'MSME' && (
              <div className="space-y-4">
                <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
                  <ShieldCheck size={16} className="text-black/90" />
                  <span className="text-[16px] font-black uppercase tracking-wide">Business Details</span>
                </div>
                <div className="bg-white border border-black/60 p-4 grid grid-cols-2 md:grid-cols-3 gap-4 shadow-sm rounded-b-sm">
                  <FormField label="Nature of Business" name="asset.natureOfBusiness" value={assetData.natureOfBusiness} readOnly={isViewMode} />
                  <FormField label="Date of Incorporation" name="asset.dateOfIncorporation" value={assetData.dateOfIncorporation} type="date" readOnly={isViewMode} />
                  <FormSelect label="Is it secured" name="asset.isSecured" options={["Yes", "No"]} value={assetData.isSecured || ''} readOnly={isViewMode} />
                </div>
              </div>
            )}
          </div>
        )}

       {activeTab === 'Financial Terms' && (
          <div key={financialLoadVersion} className="max-w-[75%] pb-32 px-4" style={{ fontFamily: 'Calibri, Candara, Segoe UI, Optima, Arial, sans-serif' }}>
          {/* 3-COLUMN MAIN GRID */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
            
            {/* COLUMN 1: CORE FINANCIAL TERMS */}
            <div className="flex flex-col">
              <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
                <IndianRupee size={24} className="text-black" /> 
               <span className="text-[16px] font-black uppercase tracking-tight text-black">
                Financial Terms
              </span>
              </div>
              <div className="bg-white border border-black/60 p-4 space-y-4 shadow-sm rounded-b-sm">
                <FormField
                  label="Loan Amount"
                  name="financial.loanAmount"
                  value={financialData.loanAmount}
                  onChange={(event) => setFinancialInputs((current) => ({ ...current, loanAmount: event.target.value }))}
                  inputMode="decimal"
                  pattern="[0-9]+(\\.[0-9]{1,2})?"
                  title="Loan amount must be a number with up to 2 decimal places."
                  readOnly={isViewMode}
                />
                <div className="grid grid-cols-2 gap-3">
                  <FormField label="Tenure (Months)" name="financial.tenureMonths" value={financialData.tenureMonths} inputMode="numeric" pattern="[0-9]+" title="Tenure must be a whole number." readOnly={isViewMode} />
                  <FormField label="Flat Rate (%)" name="financial.flatInterestRate" value={financialData.flatInterestRate} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,4})?" title="Flat rate must be a number with up to 4 decimal places." readOnly={isViewMode} />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <FormField label="IRR (%)" name="financial.irrRate" value={financialData.irrRate} onChange={() => {}} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,4})?" title="IRR is calculated from loan amount and repayment rows." readOnly />
                  <FormField label="Insurance Deposit" name="financial.insuranceDeposit" value={financialData.insuranceDeposit} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Insurance deposit must be a number with up to 2 decimal places." readOnly={isViewMode} />
                </div>
                <FormField label="Total Contract Value" name="financial.totalContractValue" value={financialData.totalContractValue} onChange={() => {}} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Total contract value is calculated from repayment rows." readOnly />
                <FormSelect label="Repayment Terms" name="financial.repaymentTerms" options={["Monthly", "Quarterly", "Bullet"]} value={financialData.repaymentTerms || ''} readOnly={isViewMode} />
                <FormSelect label="Moratorium" name="financial.moratorium" options={["No Moratorium", "One Month", "Two Month"]} value={financialData.moratorium} readOnly={isViewMode} />
                <FormSelect label="Advance" name="financial.repaymentType" options={["EMI", "Interest"]} value={financialData.repaymentType || ''} readOnly={isViewMode} />
              </div>
            </div>

            {/* COLUMN 2: REPAYMENT STRUCTURE */}
            <div className="flex flex-col">
              <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
                <RotateCcw size={16} className="text-black/90" /> 
                <span className="text-[16px] font-black uppercase tracking-wide">Repayment Structure</span>
              </div>
              <div className="bg-white border border-black/60 p-2 space-y-2 shadow-sm rounded-b-sm">
                <div className="border border-black/50 rounded-sm">
                  <table className="w-full table-fixed border-collapse text-[12px]">
                    <thead className="bg-[#E1EFFF] text-black/90 uppercase">
                      <tr>
                        <th className="border-b border-r border-black/50 px-1.5 py-1.5 text-left w-[44px]">Step</th>
                        <th className="border-b border-r border-black/50 px-1.5 py-1.5 text-left">EMI Amount</th>
                        <th className="border-b border-r border-black/50 px-1.5 py-1.5 text-left">No. of EMI</th>
                        <th className="border-b border-black/50 px-1 py-1.5 text-center w-[36px]"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {repaymentRows.map((row, index) => (
                        <tr key={row.id} className="bg-white">
                          <td className="border-r border-t border-black/30 px-1.5 py-1 font-black">{index + 1}</td>
                          <td className="border-r border-t border-black/30 px-1 py-1">
                            <input
                              name={`financial.repayment.${index}.installmentAmount`}
                              value={row.installmentAmount}
                              onChange={(event) => updateRepaymentRow(row.id, 'installmentAmount', event.target.value)}
                              readOnly={isViewMode}
                              inputMode="decimal"
                              pattern="[0-9]+(\\.[0-9]{1,2})?"
                              title="EMI amount must be a number with up to 2 decimal places."
                              className={`border border-black/50 px-1.5 py-1 text-[13px] font-black text-black/90 outline-none rounded-sm w-full ${isViewMode ? 'bg-slate-100 cursor-not-allowed' : 'bg-white'}`}
                            />
                          </td>
                          <td className="border-r border-t border-black/30 px-1 py-1">
                            <input
                              name={`financial.repayment.${index}.numberOfInstallments`}
                              value={row.numberOfInstallments}
                              onChange={(event) => updateRepaymentRow(row.id, 'numberOfInstallments', event.target.value)}
                              readOnly={isViewMode}
                              inputMode="numeric"
                              pattern="[0-9]+"
                              title="Number of EMI must be a whole number."
                              className={`border border-black/50 px-1.5 py-1 text-[13px] font-black text-black/90 outline-none rounded-sm w-full ${isViewMode ? 'bg-slate-100 cursor-not-allowed' : 'bg-white'}`}
                            />
                          </td>
                          <td className="border-t border-black/30 px-1 py-1 text-center">
                            <button
                              type="button"
                              onClick={() => removeRepaymentRow(row.id)}
                              disabled={isViewMode || repaymentRows.length <= 1}
                              className="inline-flex h-7 w-7 items-center justify-center rounded-sm border border-red-300 text-red-700 hover:bg-red-50 disabled:opacity-40"
                              title="Delete row"
                            >
                              <Trash2 size={14} />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <button
                  type="button"
                  onClick={addRepaymentRow}
                  disabled={isViewMode}
                  className="inline-flex items-center gap-1.5 border border-blue-500 bg-blue-50 px-3 py-1.5 text-[12px] font-black uppercase text-blue-700 rounded-sm hover:bg-blue-100 disabled:opacity-40"
                >
                  <Plus size={14} /> Add Row
                </button>
                <div className="border-t border-black/30 pt-3 space-y-3">
                  <FormSelect
                    label="Is First EMI Paid?"
                    name="financial.isFirstEmiPaid"
                    options={["No", "Yes"]}
                    value={financialData.isFirstEmiPaid ? 'Yes' : 'No'}
                    onChange={(event) => setFinancialDetails((current) => ({ ...current, isFirstEmiPaid: event.target.value === 'Yes' }))}
                    readOnly={isViewMode}
                  />
                  {financialData.isFirstEmiPaid && (
                    <FormField
                      label="First EMI Amount"
                      name="financial.firstEmiAmount"
                      value={firstEmiAmount}
                      onChange={() => {}}
                      readOnly
                    />
                  )}
                  <DatePickerField
                    label="First EMI Date"
                    name="financial.firstEmiDate"
                    value={financialData.firstEmiDate}
                    readOnly={isViewMode}
                    required
                  />
                </div>
              </div>
            </div>

            {/* COLUMN 3: INITIAL PAYMENT & DISBURSEMENT */}
            <div className="flex flex-col">
              <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
                <ShieldCheck size={16} className="text-black/90" /> 
                <span className="text-[16px] font-black uppercase tracking-wide">Initial & Disbursement</span>
              </div>
              <div className="bg-white border border-black/60 p-4 space-y-3 shadow-sm rounded-b-sm">
                <div className="grid grid-cols-2 gap-3">
                  <FormField label="EMI Advance" name="financial.emiAdvance" value={financialData.emiAdvance} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="EMI advance must be a number with up to 2 decimal places." readOnly={isViewMode} />
                  <FormField label="Processing" name="financial.processingCharges" value={financialData.processingCharges} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Processing charge must be a number with up to 2 decimal places." readOnly={isViewMode} />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <FormField label="RTO" name="financial.rtoCharges" value={financialData.rtoCharges} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="RTO charge must be a number with up to 2 decimal places." readOnly={isViewMode} />
                  <FormField label="Valuation" name="financial.valuationCharges" value={financialData.valuationCharges} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Valuation charge must be a number with up to 2 decimal places." readOnly={isViewMode} />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <FormField label="Stamp Duty" name="financial.stampDuty" value={financialData.stampDuty} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Stamp duty must be a number with up to 2 decimal places." readOnly={isViewMode} />
                  <FormField label="RC Holding" name="financial.rcHoldingAmount" value={financialData.rcHoldingAmount} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="RC holding must be a number with up to 2 decimal places." readOnly={isViewMode} />
                </div>
                <FormField label="Other Charges" name="financial.otherCharges" value={financialData.otherCharges} inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,2})?" title="Other charges must be a number with up to 2 decimal places." readOnly={isViewMode} />
                <FormSelect label="Repayment Mode" name="financial.modeOfPayment" options={["E Nach", "Online", "Cash", "Others"]} value={financialData.modeOfPayment || ''} readOnly={isViewMode} />
                
                <div className="mt-4 pt-4 border-t border-black/60">
                  <FormField label="Payment Done To" name="financial.paymentDoneTo" placeholder="Dealer/Customer" value={financialData.paymentDoneTo} readOnly={isViewMode} />
                  <div className="grid grid-cols-3 gap-2 mt-3">
                    <FormField label="Pay 1" name="financial.payee1" value={financialData.payee1} readOnly={isViewMode} />
                    <FormField label="Pay 2" name="financial.payee2" value={financialData.payee2} readOnly={isViewMode} />
                    <FormField label="Pay 3" name="financial.payee3" value={financialData.payee3} readOnly={isViewMode} />
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>
)}
      {/* 4. DOCUMENTATION DETAILS TAB */}
        {activeTab === 'Documentation Details' && (
        <div key={documentationLoadVersion} className="max-w-[75%] space-y-6 pb-32 px-4" style={{ fontFamily: 'Calibri, Candara, Segoe UI, Optima, Arial, sans-serif' }}>
        
        {/* ROW 1: TWO COLUMNS (BRANCH & HO) */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
          
          {/* COLUMN 1: BRANCH LEVEL DETAILS */}
          <div className="flex flex-col">
            <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
              <FileText size={24} className="text-black" /> 
              <span className="text-[16px] font-black uppercase tracking-wide">Document Details at Branch Level</span>
            </div>
            <div className="bg-white border border-black/60 p-4 space-y-4 shadow-sm rounded-b-sm">
              <div className="grid grid-cols-2 gap-4">
                <FormSelect label="Document Type" name="documentation.documentType" options={["Original", "Photocopy", "Digitally Signed"]} value={documentationData.documentType || ''} readOnly={isViewMode} />
                <FormField label="Verified By" name="documentation.documentVerifiedBy" value={documentationData.documentVerifiedBy} placeholder="Employee Name/ID" readOnly={isViewMode} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <FormField label="Obtained By" name="documentation.documentsObtainedBy" value={documentationData.documentsObtainedBy} readOnly={isViewMode} />
                <FormField label="FI Guarantor" name="documentation.guarantorFiBy" value={documentationData.guarantorFiBy} readOnly={isViewMode} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <FormField label="FI of Borrower" name="documentation.borrowerFiBy" value={documentationData.borrowerFiBy} readOnly={isViewMode} />
                <FormField label="Loan Referred By" name="documentation.loanReferredBy" value={documentationData.loanReferredBy} readOnly={isViewMode} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <FormField label="TVR Done By" name="documentation.tvrDoneBy" value={documentationData.tvrDoneBy} readOnly={isViewMode} />
                <FormField label="Vehicle by Agency (if any)" name="documentation.vehicleByAgency" value={documentationData.vehicleByAgency} readOnly={isViewMode} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <FormField label="Vehicle Inspection By" name="documentation.vehicleInspectionBy" value={documentationData.vehicleInspectionBy} readOnly={isViewMode} />
                <FormField label="Property Valuation By" name="documentation.propertyValuationBy" value={documentationData.propertyValuationBy} readOnly={isViewMode} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <FormField label="Legal Opinion By" name="documentation.legalOpinionBy" value={documentationData.legalOpinionBy} readOnly={isViewMode} />
                <FormField label="Collection Tool By" name="documentation.branchCollectionToolBy" value={documentationData.branchCollectionToolBy} readOnly={isViewMode} />
              </div>
            </div>
          </div>

          {/* COLUMN 2: HO LEVEL DETAILS */}
          <div className="flex flex-col">
            <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
              <ShieldCheck size={24} className="text-black/90" /> 
              <span className="text-[16px] font-black uppercase tracking-wide">Document Details at HO Level</span>
            </div>
            <div className="bg-white border border-black/60 p-4 space-y-4 shadow-sm rounded-b-sm">
              <div className="grid grid-cols-2 gap-4">
                <FormField label="Documents Checked By" name="documentation.documentsCheckedBy" value={documentationData.documentsCheckedBy} readOnly={isViewMode} />
                <FormField label="Documents Verified By" name="documentation.documentsVerifiedBy" value={documentationData.documentsVerifiedBy} readOnly={isViewMode} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <FormField label="Loan Approved By" name="documentation.loanApprovedBy" value={documentationData.loanApprovedBy} readOnly={isViewMode} />
                <FormField label="Disbursed By" name="documentation.disbursedBy" value={documentationData.disbursedBy} readOnly={isViewMode} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <FormField label="RC Online Checking" name="documentation.rcOnlineChecking" value={documentationData.rcOnlineChecking} readOnly={isViewMode} />
                <FormField label="Collection Tool By (HO)" name="documentation.hoCollectionToolBy" value={documentationData.hoCollectionToolBy} readOnly={isViewMode} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-1" style={{ fontFamily: 'Calibri, sans-serif' }}>
                  <label className="text-[14px] text-black/90 font-normal uppercase tracking-tight leading-tight">
                    Area Code
                  </label>
                  <AreaSearchField
                    value={documentationData.areaCode}
                    onChange={(areaCode) => setDocumentationDetails((current) => ({ ...current, areaCode }))}
                    readOnly={isViewMode}
                  />
                </div>
                <FormField label="TVR Done By (HO)" name="documentation.hoTvrDoneBy" value={documentationData.hoTvrDoneBy} readOnly={isViewMode} />
              </div>
              <FormSelect label="Stock Marked to Bank" name="documentation.stockMarkedToBank" options={["Select Bank", "HDFC", "ICICI", "SBI", "Axis", "Kotak"]} value={documentationData.stockMarkedToBank || ''} readOnly={isViewMode} />
            </div>
          </div>
        </div>

        {/* ROW 2: PENDING DOCUMENTS CHECKLIST */}
        <div className="flex flex-col">
          <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
            <Copy size={16} className="text-black/90" /> 
            <span className="text-[16px] font-black uppercase tracking-wide">Pending Document Details (Check List)</span>
          </div>
          <div className="bg-white border border-black/60 p-4 shadow-sm rounded-b-sm">
            <div className="flex flex-wrap gap-x-12 gap-y-4 py-2 px-2">
              {pendingDocuments.map((item) => (
                <label key={item.documentName} className="flex items-center gap-3 cursor-pointer group">
                  <input
                    type="checkbox"
                    checked={item.pending}
                    disabled={isViewMode}
                    onChange={() => togglePendingDocument(item.documentName)}
                    className="w-4 h-4 accent-blue-700 border-black/60 rounded cursor-pointer disabled:opacity-40"
                  />
                  <span className="text-[16px] font-black text-black/90 group-hover:text-black uppercase tracking-tight">{item.documentName}</span>
                </label>
              ))}
            </div>
          </div>
        </div>

        {/* ROW 3: UPLOAD DOCUMENTS SECTION */}
        <div className="flex flex-col">
          <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
            <Send size={16} className="text-black/90" /> 
            <span className="text-[16px] font-black uppercase tracking-wide">Upload Documents Section</span>
          </div>
          <div className="bg-white border border-black/60 p-5 shadow-sm rounded-b-sm space-y-6">
            
            {/* SUB-ROW 1: INPUT CONTROLS */}
            <div className="flex flex-col md:flex-row items-end gap-6 bg-[#F9FAFF] p-4 border border-black/20 rounded-sm">
              <FormSelect 
                label="Select Document Type" 
                name="documentation.uploadDocumentCategory"
                options={["PAN Card", "Aadhar", "Property Tax", "RC Copy", "Agreement", "Insurance"]} 
                value={uploadDocumentCategory}
                onChange={(event) => setUploadDocumentCategory(event.target.value)}
                className="flex-1"
                readOnly={isViewMode}
              />
              <div className="flex-[2] w-full">
                <label className="text-[14px] text-black/90 font-normal uppercase tracking-tight mb-1 block">Browse Attachment</label>
                <div className="flex gap-3">
                  <input 
                    key={uploadInputKey}
                    type="file" 
                    multiple
                    accept="image/*,application/pdf"
                    disabled={isViewMode}
                    onChange={(event) => setSelectedUploadFiles(Array.from(event.target.files || []))}
                    className="block w-full text-[14px] text-black/90 file:mr-4 file:py-1.5 file:px-4 file:rounded-sm file:border-0 file:text-[12px] file:font-normal file:bg-blue-800 file:text-white hover:file:bg-blue-900 cursor-pointer border border-black/60 bg-white shadow-sm"
                  />
                  <button
                    type="button"
                    onClick={addDocumentUpload}
                    disabled={isViewMode || !uploadDocumentCategory || selectedUploadFiles.length === 0}
                    className="bg-blue-800 text-white px-8 py-1.5 text-[16px] font-black rounded-sm uppercase hover:bg-blue-900 transition-all shadow-md active:scale-95 disabled:opacity-40"
                  >
                    Upload
                  </button>
                </div>
                {selectedUploadFiles.length > 0 && (
                  <div className="mt-2 text-[12px] font-black uppercase text-blue-900">
                    {selectedUploadFiles.length} file{selectedUploadFiles.length > 1 ? 's' : ''} selected
                  </div>
                )}
              </div>
            </div>

            {/* SUB-ROW 2: DATA TABLE */}
            <div className="flex flex-col space-y-3">
              <div className="flex items-center justify-between px-1">
                <span className="text-[16px] font-black text-black/60 uppercase tracking-widest">Linked Attachments Registry</span>
                <span className="text-[12px] text-blue-800 font-normal uppercase">Total Files: {documentUploads.length}</span>
              </div>
              <div className="border border-black/60 rounded-sm overflow-hidden shadow-sm">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] border-b border-black/60 shadow-sm">
                      <th className="px-4 py-3 text-[16px] font-black text-black/90 uppercase tracking-wider w-24">
                        Preview
                      </th>
                      <th className="px-4 py-3 text-[16px] font-black text-black/90 uppercase tracking-wider">
                        Document Category
                      </th>
                      <th className="px-4 py-3 text-[16px] font-black text-black/90 uppercase tracking-wider">
                        File Name
                      </th>
                      <th className="px-4 py-3 text-[16px] font-black text-black/90 uppercase tracking-wider text-right">
                        Actions
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-black/10 bg-white">
                    {documentUploads.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="px-4 py-5 text-center text-[13px] uppercase text-black/50">No files linked</td>
                      </tr>
                    ) : documentUploads.map((upload) => (
                      <tr key={upload.id} className="hover:bg-blue-50/40 transition-colors group text-black/90">
                        <td className="px-4 py-3">
                          <button
                            type="button"
                            onClick={() => openDocumentPreview(upload.id)}
                            disabled={!upload.previewUrl && !upload.storagePath}
                            className="w-12 h-12 bg-white rounded-sm border border-black/20 overflow-hidden relative shadow-sm group-hover:border-blue-400 flex items-center justify-center disabled:opacity-50"
                            title="Preview document"
                          >
                            {upload.previewUrl && isImageUpload(upload) ? (
                              <img src={upload.previewUrl} alt={upload.fileName} className="h-full w-full object-cover" />
                            ) : (
                              <FileText size={20} className="text-blue-800" />
                            )}
                          </button>
                        </td>
                        <td className="px-4 py-3 text-[14px]">
                          <span className="px-2 py-0.5 rounded-sm bg-blue-50 text-blue-900 border border-blue-100">{upload.documentCategory}</span>
                        </td>
                        <td className="px-4 py-3 text-[14px]">{upload.fileName}</td>
                        <td className="px-4 py-3 text-right">
                          <div className="flex justify-end items-center gap-4">
                            <button
                              type="button"
                              onClick={() => openDocumentPreview(upload.id)}
                              disabled={!upload.previewUrl && !upload.storagePath}
                              className="text-blue-800 hover:text-blue-900 text-[14px] uppercase flex items-center gap-1 disabled:opacity-40"
                            >
                              <FileText size={14} /> View
                            </button>
                            <button
                              type="button"
                              onClick={() => removeDocumentUpload(upload.id)}
                              disabled={isViewMode}
                              className="text-red-700 hover:text-red-900 text-[14px] uppercase flex items-center gap-1 disabled:opacity-40"
                            >
                              <RotateCcw size={14} /> Remove
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>
)}

      {/* 5. CO LENDING DETAILS TAB */}
      {activeTab === 'Co Lending Details' && (
        <div key={coLendingLoadVersion} className="max-w-[75%] pb-32 px-4" style={{ fontFamily: 'Calibri, Candara, Segoe UI, Optima, Arial, sans-serif' }}>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        
        {/* COLUMN 1: MODEL & LENDER INFO */}
        <div className="flex flex-col">
          <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
            <Share2 size={16} className="text-black/90" /> 
            <span className="text-[16px] font-black uppercase tracking-wide">Contribution Model</span>
          </div>
          <div className="bg-white border border-black/60 p-4 space-y-4 shadow-sm rounded-b-sm">
            <FormSelect 
              label="Co-Lending Type" 
              name="coLending.coLendingType"
              options={["Contribution Model", "Revenue Sharing (RSP)", "Risk Sharing", "Franchise"]} 
              value={coLendingDetails.coLendingType || ''}
              readOnly={isViewMode}
            />
            <FormField label="Name of Co-Lender" name="coLending.coLenderName" value={coLendingDetails.coLenderName} readOnly={isViewMode} />
            <FormField
              label="Security Deposit Amount"
              name="coLending.securityDepositAmount"
              value={coLendingDetails.securityDepositAmount}
              inputMode="decimal"
              pattern="[0-9]+(\\.[0-9]{1,2})?"
              title="Security deposit must be a number with up to 2 decimal places."
              readOnly={isViewMode}
            />
          </div>
        </div>

        {/* COLUMN 2: SHARE PERCENTAGES */}
        <div className="flex flex-col">
          <div className="bg-gradient-to-r from-[#E1EFFF] to-[#D6E4FF] text-black/90 border border-black/60 px-3 py-1.5 rounded-t-lg shadow-sm flex items-center gap-2">
            <IndianRupee size={16} className="text-black/90" /> 
            <span className="text-[16px] font-black uppercase tracking-wide">Revenue & Risk Share</span>
          </div>
          <div className="bg-white border border-black/60 p-4 shadow-sm rounded-b-sm">
            <div className="grid grid-cols-2 gap-x-4 gap-y-4">
              <FormField
                label="If Contribution Share %"
                name="coLending.contributionSharePercent"
                value={coLendingDetails.contributionSharePercent}
                inputMode="decimal"
                pattern="[0-9]+(\\.[0-9]{1,4})?"
                title="Contribution share must be a percentage with up to 4 decimal places."
                readOnly={isViewMode}
              />
              <FormField
                label="EMI Share"
                name="coLending.emiSharePercent"
                value={coLendingDetails.emiSharePercent}
                inputMode="decimal"
                pattern="[0-9]+(\\.[0-9]{1,4})?"
                title="EMI share must be a percentage with up to 4 decimal places."
                readOnly={isViewMode}
              />
              <FormField
                label="% of Revenue Share"
                name="coLending.revenueSharePercent"
                value={coLendingDetails.revenueSharePercent}
                inputMode="decimal"
                pattern="[0-9]+(\\.[0-9]{1,4})?"
                title="Revenue share must be a percentage with up to 4 decimal places."
                readOnly={isViewMode}
              />
              <FormField
                label="Risk Share"
                name="coLending.riskSharePercent"
                value={coLendingDetails.riskSharePercent}
                inputMode="decimal"
                pattern="[0-9]+(\\.[0-9]{1,4})?"
                title="Risk share must be a percentage with up to 4 decimal places."
                readOnly={isViewMode}
              />
            </div>
            
            {/* The note section updated to match the brand blue style */}
            <div className="mt-6 p-3 bg-[#F9FAFF] border border-black/20 rounded-sm">
              <p className="text-[14px] text-black/90 leading-tight">
                <span className="font-bold text-blue-900">Note:</span> Co-lending terms are governed by the master agreement between the primary financier and the participating partner.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )}
        {!['Borrower Details', 'Asset Details', 'Financial Terms', 'Co Lending Details', 'Documentation Details'].includes(activeTab) && (
          <div className="flex flex-col items-center justify-center py-20 bg-white border border-dashed border-slate-300 rounded-lg mx-4">
            <h2 className="text-xl font-black uppercase text-slate-400 italic tracking-widest">{activeTab} MODULE</h2>
          </div>
        )}
      </fieldset>

     {/* 4. COMPACT PERSISTENT NAVIGATION BAR */}
<div className="shrink-0 bg-white/95 backdrop-blur-md border-t-2 border-black/20 py-1.5 flex justify-end items-center px-6 gap-3 shadow-[0_-10px_25px_rgba(0,0,0,0.06)] z-[80]">
  
  {/* Previous Section Button - Light Brand Blue Style */}
  {activeTab !== tabs[0].name && (
    <button 
      onClick={handlePrev} 
      className="px-4 py-1.5 bg-[#E1EFFF] border-2 border-[#0052CC]/20 text-[#0052CC] text-[11px] font-black rounded-md flex items-center gap-2 hover:bg-black hover:text-white hover:border-black uppercase tracking-widest transition-all active:scale-95 shadow-sm"
    >
      <ChevronLeft size={14} strokeWidth={3} /> 
      Previous Section
    </button>
  )}

  {/* Next / Final Review Button - Light Brand Blue with Shimmer */}
  {activeTab !== tabs[tabs.length - 1].name ? (
    <button 
      onClick={handleNext} 
      className="relative overflow-hidden px-6 py-2 bg-[#E1EFFF] border-2 border-[#0052CC]/30 text-[#0052CC] text-[11px] font-black rounded-md flex items-center gap-2 hover:bg-[#0052CC] hover:text-white uppercase tracking-[0.15em] transition-all duration-300 group active:scale-95 shadow-sm"
    >
      {/* Shimmer Effect */}
      <span className="absolute inset-0 w-1/2 h-full bg-white/40 skew-x-[-25deg] -translate-x-full group-hover:animate-[shimmer_0.75s_ease-out]" style={{ filter: 'blur(8px)' }} />
      
      <span className="relative z-10">Next Section</span>
      <ChevronRight size={14} strokeWidth={3} className="relative z-10 transition-transform group-hover:translate-x-1" />
    </button>
  ) : (
    <button className="px-6 py-2 bg-emerald-600 text-white text-[11px] font-black rounded-md flex items-center gap-2 hover:bg-emerald-800 uppercase tracking-[0.15em] transition-all shadow-[0_5px_15px_rgba(16,185,129,0.2)] active:scale-95">
      Final Review <CheckCircle size={14} strokeWidth={3} />
    </button>
  )}

  {/* Shimmer Keyframes */}
  <style dangerouslySetInnerHTML={{ __html: `
    @keyframes shimmer {
      100% { transform: translateX(450%) skewX(-25deg); }
    }
  `}} />
</div>
     
      {activePreview && (
        <div className="fixed inset-0 z-[250] bg-black/80 flex flex-col">
          <div className="h-12 shrink-0 bg-white border-b border-black/30 flex items-center justify-between px-4">
            <div className="min-w-0">
              <div className="text-[13px] font-black uppercase text-blue-900 truncate">{activePreview.documentCategory}</div>
              <div className="text-[12px] font-black text-black/70 truncate">{activePreview.fileName}</div>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-[12px] font-black uppercase text-black/60">
                {activePreviewIndex + 1} / {documentUploads.length}
              </span>
              <button
                type="button"
                onClick={() => setActivePreviewIndex(null)}
                className="h-8 w-8 inline-flex items-center justify-center rounded-sm border border-black/30 text-black hover:bg-slate-100"
                title="Close preview"
              >
                <X size={16} />
              </button>
            </div>
          </div>

          <div className="relative flex-1 min-h-0 flex items-center justify-center p-4">
            {documentUploads.length > 1 && (
              <>
                <button
                  type="button"
                  onClick={showPreviousDocument}
                  className="absolute left-4 top-1/2 -translate-y-1/2 z-10 h-11 w-11 inline-flex items-center justify-center rounded-sm bg-white text-blue-900 border border-black/30 shadow-lg hover:bg-blue-50"
                  title="Previous document"
                >
                  <ChevronLeft size={24} />
                </button>
                <button
                  type="button"
                  onClick={showNextDocument}
                  className="absolute right-4 top-1/2 -translate-y-1/2 z-10 h-11 w-11 inline-flex items-center justify-center rounded-sm bg-white text-blue-900 border border-black/30 shadow-lg hover:bg-blue-50"
                  title="Next document"
                >
                  <ChevronRight size={24} />
                </button>
              </>
            )}

            <div className="h-full w-full max-w-6xl bg-white border border-black/30 shadow-2xl overflow-hidden">
              {activePreviewSrc && isImageUpload(activePreview) && (
                <img src={activePreviewSrc} alt={activePreview.fileName} className="h-full w-full object-contain bg-black" />
              )}
              {activePreviewSrc && isPdfUpload(activePreview) && (
                <iframe title={activePreview.fileName} src={activePreviewSrc} className="h-full w-full bg-white" />
              )}
              {(!activePreviewSrc || (!isImageUpload(activePreview) && !isPdfUpload(activePreview))) && (
                <div className="h-full flex flex-col items-center justify-center gap-3 text-black/60">
                  <FileText size={44} className="text-blue-800" />
                  <div className="text-[14px] font-black uppercase">Preview not available for this file</div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

    </div>
  );
};

export default ContractEditForm;
