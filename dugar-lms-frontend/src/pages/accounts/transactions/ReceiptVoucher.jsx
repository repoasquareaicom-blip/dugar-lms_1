import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  FileText,
  Loader2,
  Plus,
  Save,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { fetchContractsPage } from '../../../services/contractsService';
import { fetchLedgerCodes } from '../../../services/ledgerCodeService';
import {
  authoriseVoucher,
  cancelVoucher,
  fetchVoucher,
  rejectVoucher,
  reopenVoucher,
  resubmitVoucher,
  saveVoucher,
  searchVouchers,
  updateVoucher,
} from '../../../services/voucherService';
import { getStoredAuthToken } from '../../../api/apiClient';

const today = new Date().toISOString().slice(0, 10);

function emptyRow() {
  return {
    id: crypto.randomUUID(),
    detailsCode: null,
    loanReference: null,
    areaCode: '',
    debit: '',
    credit: '',
    narration: '',
  };
}

function amount(value) {
  return Number.parseFloat(value || 0) || 0;
}

function money(value) {
  return amount(value).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function displayDate(value) {
  if (!value) return '';
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).replace(/ /g, '-');
}

function cleanAmountInput(value) {
  const cleaned = String(value || '').replace(/[^0-9.]/g, '');
  const parts = cleaned.split('.');
  return parts.length <= 1 ? parts[0] : `${parts[0]}.${parts.slice(1).join('')}`;
}

function blockNegativeNumberKeys(event) {
  if (['-', '+', 'e', 'E'].includes(event.key)) {
    event.preventDefault();
  }
}

function requestErrorMessage(error, action = 'save voucher') {
  const status = error?.response?.status;
  const serverMessage = error?.response?.data?.message;
  if (status === 401) {
    return 'Your login session is no longer valid. Please sign in again, then reopen this voucher screen and try once more.';
  }
  if (status === 403) {
    return `You do not have permission to ${action}. Please contact the administrator.`;
  }
  if (status === 404) {
    return `The ${action} API is not available. Please restart the API server.`;
  }
  if (status >= 500) {
    return `The API could not ${action}. Please check the API log for the exact server error.`;
  }
  if (serverMessage) return serverMessage;
  return error?.message || `Unable to ${action}.`;
}

function saveErrorMessage(error) {
  return requestErrorMessage(error, 'save voucher');
}

function hasAuthToken() {
  return Boolean(getStoredAuthToken());
}

function voucherKind(path) {
  const lower = path.toLowerCase();
  if (lower.includes('/journal')) return 'journal';
  if (lower.includes('/payment')) return 'payment';
  return 'receipt';
}

function readParty(contract) {
  if (!contract) return null;
  const area = contract.areaCode || contract.area || contract.branch || contract.branchCode || contract.area_code || '-';
  const addressParts = [
    contract.customerAddress || contract.addressLine1 || contract.address || contract.borrowerAddress,
    contract.addressLine2 || contract.customerAddress2,
    contract.customerCity || contract.city,
    contract.customerState || contract.state,
    contract.customerPinCode || contract.pinCode || contract.pincode,
  ].filter(Boolean);

  return {
    contractNumber: contract.contractNumber || contract.legacyContractNumber || contract.contractId || '-',
    partyName: contract.borrowerName || contract.customerName || contract.partyName || '-',
    address: addressParts.length > 0 ? addressParts.join(', ') : '-',
    areaCode: area,
  };
}

function rowHasEntry(row) {
  return Boolean(
    row.detailsCode
    || row.loanReference
    || row.areaCode
    || row.narration.trim()
    || amount(row.debit) > 0
    || amount(row.credit) > 0,
  );
}

function rowHasRequiredAmount(row, kind) {
  if (kind === 'payment') return amount(row.debit) > 0;
  if (kind === 'receipt') return amount(row.credit) > 0;
  return amount(row.debit) > 0 || amount(row.credit) > 0;
}

function duplicateDetailRows(rows) {
  const seen = new Set();
  for (const row of rows) {
    const key = [
      row.detailsCode?.ledgerCode || '',
      row.loanReference?.contractNumber || row.loanReference?.legacyContractNumber || '',
      amount(row.debit).toFixed(2),
      amount(row.credit).toFixed(2),
    ].join('|').toUpperCase();
    if (seen.has(key)) return true;
    seen.add(key);
  }
  return false;
}

function focusNextInput(currentElement) {
  const fields = Array.from(
    document.querySelectorAll('input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled])'),
  ).filter((element) => element.offsetParent !== null && element.tabIndex !== -1);
  const currentIndex = fields.indexOf(currentElement);
  if (currentIndex >= 0) fields[currentIndex + 1]?.focus();
}

function moveNextOnEnter(event) {
  if (event.key !== 'Enter') return;
  event.preventDefault();
  focusNextInput(event.currentTarget);
}

const VoucherDateInput = ({ value, onChange }) => {
  const [editing, setEditing] = useState(false);

  return (
    <input
      type={editing ? 'date' : 'text'}
      value={editing ? value : displayDate(value)}
      onFocus={() => setEditing(true)}
      onBlur={() => setEditing(false)}
      onKeyDown={moveNextOnEnter}
      onChange={(event) => onChange(event.target.value)}
      className="h-7 w-full border border-gray-300 bg-white px-2 text-right text-[13px] font-black text-black outline-none focus:border-blue-700"
    />
  );
};

const ReceiptVoucher = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const searchParams = new URLSearchParams(location.search);
  const selectedContract = location.state?.contract || null;
  const requestedVoucherId = location.state?.voucherHeaderId || searchParams.get('voucherHeaderId');
  const authorisationMode = location.state?.authorisationMode || searchParams.get('authorisation') === 'true';
  const returnTo = location.state?.returnTo || '/accounts/transaction/authorisation';
  const kind = voucherKind(location.pathname);
  const editMode = location.pathname.toLowerCase().includes('/edit/');
  const isReceipt = kind === 'receipt';
  const isPayment = kind === 'payment';
  const isJournal = kind === 'journal';
  const voucherTitle = isJournal ? 'Journal Voucher' : isPayment ? 'Payment Voucher' : 'Receipt Voucher';

  const [category, setCategory] = useState(selectedContract ? 'LOAN' : 'GENERAL');
  const [systemDate, setSystemDate] = useState(today);
  const [voucherDate, setVoucherDate] = useState(today);
  const [voucherNo, setVoucherNo] = useState('Auto');
  const [voucherStatus, setVoucherStatus] = useState('');
  const [headerControlCode, setHeaderControlCode] = useState(null);
  const [voucherAmount, setVoucherAmount] = useState('');
  const [rows, setRows] = useState([
    {
      ...emptyRow(),
      loanReference: selectedContract,
      areaCode: readParty(selectedContract)?.areaCode || '',
    },
  ]);
  const [message, setMessage] = useState(null);
  const [duplicateConfirm, setDuplicateConfirm] = useState(false);
  const [searchOpen, setSearchOpen] = useState(editMode && !requestedVoucherId);
  const [editingVoucherId, setEditingVoucherId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [loadingVoucher, setLoadingVoucher] = useState(false);
  const [reasonAction, setReasonAction] = useState(null);

  const isLoan = category === 'LOAN';
  const debitEditable = isPayment || isJournal;
  const creditEditable = isReceipt || isJournal;
  const selectedLoan = rows.find((row) => row.loanReference)?.loanReference || selectedContract;
  const party = readParty(selectedLoan);
  const enteredRows = useMemo(() => rows.filter(rowHasEntry), [rows]);

  const detailDebitTotal = useMemo(
    () => enteredRows.reduce((sum, row) => sum + amount(row.debit), 0),
    [enteredRows],
  );
  const detailCreditTotal = useMemo(
    () => enteredRows.reduce((sum, row) => sum + amount(row.credit), 0),
    [enteredRows],
  );

  const footerDebitTotal = isReceipt && !isJournal ? amount(voucherAmount) : detailDebitTotal;
  const footerCreditTotal = isPayment && !isJournal ? amount(voucherAmount) : detailCreditTotal;
  const difference = footerDebitTotal - footerCreditTotal;
  const totalsMatch = Math.abs(difference) < 0.01 && footerDebitTotal > 0 && footerCreditTotal > 0;

  useEffect(() => {
    if (!isLoan) {
      setRows((current) => current.map((row) => ({ ...row, loanReference: null, areaCode: '' })));
    }
  }, [isLoan]);

  useEffect(() => {
    if (rows.length > 0 && rows.every(rowHasEntry)) {
      setRows((current) => [...current, emptyRow()]);
    }
  }, [rows]);

  const updateRow = (id, patch) => {
    setRows((current) => current.map((row) => (row.id === id ? { ...row, ...patch } : row)));
  };

  const addRow = () => {
    setRows((current) => [...current, emptyRow()]);
  };

  const removeRow = (id) => {
    setRows((current) => (current.length === 1 ? current : current.filter((row) => row.id !== id)));
  };

  const setNonNegativeRowAmount = (id, field, value) => {
    updateRow(id, { [field]: cleanAmountInput(value) });
  };

  const payload = (allowDuplicateDetails = false) => {
    const contract = rows.find((row) => row.loanReference)?.loanReference || null;
    return {
      voucherType: kind.toUpperCase(),
      voucherTypeDescription: voucherTitle,
      voucherNumber: editMode ? voucherNo : voucherNo,
      voucherDate,
      systemDate,
      transactionType: location.pathname.toLowerCase().includes('/bank') ? 'BANK' : 'CASH',
      voucherAmount: amount(voucherAmount),
      category,
      contractId: contract?.contractId || null,
      contractNumber: contract?.contractNumber || contract?.legacyContractNumber || null,
      contractType: contract?.product || contract?.contractType || null,
      headerControlCode: headerControlCode?.ledgerCode || '',
      headerControlName: headerControlCode?.ledgerName || '',
      allowDuplicateDetails,
      details: enteredRows.map((row, index) => {
        const partyInfo = readParty(row.loanReference);
        return {
          serialNumber: index + 1,
          category,
          ledgerCode: row.detailsCode?.ledgerCode || '',
          ledgerName: row.detailsCode?.ledgerName || '',
          debitAmount: amount(row.debit),
          creditAmount: amount(row.credit),
          partyCode: row.loanReference?.customerCode || '',
          partyName: partyInfo?.partyName || '',
          loanReference: row.loanReference?.contractNumber || row.loanReference?.legacyContractNumber || '',
          narration: row.narration,
          address: partyInfo?.address || '',
        };
      }),
    };
  };

  const validate = () => {
    if (!isJournal && !headerControlCode) return 'Header control code is required.';
    if (!isJournal && amount(voucherAmount) <= 0) return 'Voucher amount is required.';
    if (!voucherDate) return 'Voucher date is required.';
    if (voucherDate > today) return 'Voucher date cannot be a future date.';
    if (enteredRows.length === 0) return 'At least one detail row is required.';
    if (enteredRows.some((row) => !row.detailsCode)) return 'Details code is required for every entered row.';
    if (isLoan && enteredRows.some((row) => !row.loanReference)) return 'Loan reference is required for loan category.';
    if (enteredRows.some((row) => !rowHasRequiredAmount(row, kind))) {
      if (isPayment) return 'Debit amount is required for every entered row.';
      if (isReceipt) return 'Credit amount is required for every entered row.';
      return 'Debit or credit amount is required for every row.';
    }
    if (!totalsMatch) {
      return 'Debit and credit totals should match before saving.';
    }
    return '';
  };

  const performSave = (allowDuplicateDetails = false) => {
    setDuplicateConfirm(false);
    setSaving(true);
    const request = editingVoucherId
      ? updateVoucher(editingVoucherId, payload(allowDuplicateDetails))
      : saveVoucher(payload(allowDuplicateDetails));
    request
      .then((saved) => {
        setVoucherNo(saved.voucherNumber || voucherNo);
        setEditingVoucherId(saved.voucherHeaderId || editingVoucherId);
        setMessage({ type: 'success', text: `Voucher ${editingVoucherId ? 'updated' : 'submitted'} successfully: ${saved.voucherNumber || voucherNo}` });
      })
      .catch((error) => {
        setMessage({ type: 'error', text: saveErrorMessage(error) });
      })
      .finally(() => {
      setSaving(false);
      });
  };

  const handleSubmitForAuthorisation = () => {
    if (!editingVoucherId) {
      handleSave();
      return;
    }
    const error = validate();
    if (error) {
      setMessage({ type: 'error', text: error });
      return;
    }
    if (duplicateDetailRows(enteredRows)) {
      setMessage({ type: 'error', text: 'Duplicate detail rows must be resolved before submitting for authorisation.' });
      return;
    }
    setSaving(true);
    updateVoucher(editingVoucherId, payload(false))
      .then(() => resubmitVoucher(editingVoucherId))
      .then((saved) => {
        setMessage({ type: 'success', text: `Voucher submitted for authorisation: ${saved.voucherNumber || voucherNo}` });
      })
      .catch((error) => {
        setMessage({ type: 'error', text: saveErrorMessage(error) });
      })
      .finally(() => setSaving(false));
  };

  const handleSave = (allowDuplicate = false) => {
    const error = validate();
    if (error) {
      setMessage({ type: 'error', text: error });
      return;
    }
    if (!allowDuplicate && duplicateDetailRows(enteredRows)) {
      setDuplicateConfirm(true);
      return;
    }

    performSave(allowDuplicate);
  };

  const loadVoucherForEdit = (voucher) => {
    setEditingVoucherId(voucher.voucherHeaderId);
    setVoucherNo(voucher.voucherNumber || 'Auto');
    setVoucherDate(voucher.voucherDate || today);
    setSystemDate(voucher.systemDate || today);
    setVoucherStatus(voucher.status || '');
    setHeaderControlCode(voucher.headerControlCode ? {
      ledgerCode: voucher.headerControlCode,
      ledgerName: voucher.headerControlName || '',
    } : null);
    setVoucherAmount(String(voucher.voucherAmount || ''));
    const nextCategory = voucher.details?.find((detail) => detail.category)?.category || category;
    setCategory(nextCategory);
    const loadedRows = (voucher.details || []).map((detail) => ({
      id: crypto.randomUUID(),
      detailsCode: {
        ledgerCode: detail.ledgerCode,
        ledgerName: detail.ledgerName || '',
      },
      loanReference: detail.loanReference ? {
        contractNumber: detail.loanReference,
        customerName: detail.partyName || '',
        customerAddress: detail.address || '',
      } : null,
      areaCode: '',
      debit: detail.debitAmount ? String(detail.debitAmount) : '',
      credit: detail.creditAmount ? String(detail.creditAmount) : '',
      narration: detail.narration || '',
    }));
    setRows(loadedRows.length > 0 ? loadedRows : [emptyRow()]);
    setSearchOpen(false);
  };

  useEffect(() => {
    if (!requestedVoucherId) return;
    setLoadingVoucher(true);
    fetchVoucher(requestedVoucherId)
      .then(loadVoucherForEdit)
      .catch((error) => setMessage({ type: 'error', text: requestErrorMessage(error, 'load voucher') }))
      .finally(() => setLoadingVoucher(false));
  }, [requestedVoucherId]);

  const saveBeforeAuthorisationAction = () => {
    const error = validate();
    if (error) {
      setMessage({ type: 'error', text: error });
      return Promise.reject(new Error(error));
    }
    if (duplicateDetailRows(enteredRows)) {
      const duplicateError = 'Duplicate detail rows must be resolved before authorisation.';
      setMessage({ type: 'error', text: duplicateError });
      return Promise.reject(new Error(duplicateError));
    }
    return updateVoucher(editingVoucherId, payload(false));
  };

  const handleAuthorise = () => {
    if (!editingVoucherId) return;
    setSaving(true);
    saveBeforeAuthorisationAction()
      .then(() => authoriseVoucher(editingVoucherId))
      .then((saved) => {
        setVoucherStatus(saved.status || 'AUTHORISED');
        setMessage({ type: 'success', text: `Voucher authorised successfully: ${saved.voucherNumber || voucherNo}` });
      })
      .catch((error) => {
        if (error?.response || !error?.message) {
          setMessage({ type: 'error', text: requestErrorMessage(error, 'authorise voucher') });
        }
      })
      .finally(() => setSaving(false));
  };

  const handleReasonAction = (reason) => {
    if (!editingVoucherId || !reasonAction) return;
    const action = reasonAction;
    setSaving(true);
    const request = action === 'reject'
      ? rejectVoucher(editingVoucherId, reason)
      : cancelVoucher(editingVoucherId, reason);

    request
      .then((saved) => {
        setReasonAction(null);
        setVoucherStatus(saved.status || action.toUpperCase());
        setMessage({ type: 'success', text: `Voucher ${action === 'reject' ? 'rejected' : 'cancelled'} successfully: ${saved.voucherNumber || voucherNo}` });
      })
      .catch((error) => setMessage({ type: 'error', text: requestErrorMessage(error, `${action} voucher`) }))
      .finally(() => setSaving(false));
  };

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-gray-100 text-black" style={{ fontFamily: 'Calibri, "Segoe UI", sans-serif' }}>
      {loadingVoucher && <div className="fixed inset-0 z-[260] grid place-items-center bg-white/50" />}
      <main className="mx-auto flex min-h-0 w-full max-w-7xl flex-1 flex-col p-2">
        <section className="flex h-full min-h-0 flex-col border border-gray-300 bg-white shadow">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-300 bg-blue-50 px-4 py-2">
            <div>
              <h2 className="text-[22px] font-black uppercase tracking-tight text-black">{voucherTitle}</h2>
              <p className="mt-1 text-[12px] font-bold uppercase text-gray-600">
                {authorisationMode ? `Authorisation process${voucherStatus ? ` | Status ${voucherStatus}` : ''}` : editMode ? 'Edit voucher' : 'Accounts voucher entry'}
              </p>
            </div>
            <table className="min-w-[500px] border border-blue-900 bg-white text-[11px] font-black uppercase text-gray-700">
              <thead>
                <tr className="bg-blue-900 text-white">
                  <th className="border-r border-blue-700 px-2 py-1 text-left">Voucher No</th>
                  <th className="border-r border-blue-700 px-2 py-1 text-left">System Date</th>
                  <th className="px-2 py-1 text-left">Voucher Date</th>
                </tr>
              </thead>
              <tbody>
                <tr className="align-middle">
                  <td className="w-32 border-r border-gray-300 px-2 py-1 text-[14px] text-black">{voucherNo}</td>
                  <td className="w-36 border-r border-gray-300 px-2 py-1 text-[14px] text-black">{displayDate(systemDate)}</td>
                  <td className="w-44 px-2 py-1">
                    <VoucherDateInput value={voucherDate} onChange={setVoucherDate} />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div className="grid grid-cols-1 items-end gap-2 px-4 py-2 lg:grid-cols-12">
            <Field label="Category" className="lg:col-span-1">
              <select value={category} onKeyDown={moveNextOnEnter} onChange={(event) => setCategory(event.target.value)} className="field-input">
                <option value="GENERAL">General</option>
                <option value="LOAN">Loan</option>
              </select>
            </Field>

            {!isJournal && (
              <>
                <Field label="Header Control Code" className="lg:col-span-7">
                  <LedgerLookup value={headerControlCode} onChange={setHeaderControlCode} placeholder="Search ledger code..." />
                </Field>
                <Field label="Voucher Amount" className="flex flex-col items-end lg:col-span-4">
                  <input type="number" min="0" value={voucherAmount} onKeyDown={(event) => { blockNegativeNumberKeys(event); moveNextOnEnter(event); }} onPaste={(event) => event.clipboardData.getData('text').includes('-') && event.preventDefault()} onChange={(event) => setVoucherAmount(cleanAmountInput(event.target.value))} className="field-input h-14 max-w-64 border-2 border-blue-800 bg-blue-100 text-right text-[32px] font-black text-blue-950 shadow-inner" placeholder="0.00" />
                </Field>
              </>
            )}
          </div>

          <div className="flex min-h-0 flex-1 flex-col border-t border-gray-300 p-2">
            <div className="mb-1 flex items-center justify-between">
              <h3 className="inline-flex items-center gap-2 text-[14px] font-black uppercase tracking-widest text-blue-900">
                <FileText size={16} /> Details
              </h3>
              <button type="button" onClick={addRow} className="inline-flex items-center gap-2 rounded bg-blue-900 px-3 py-2 text-[12px] font-black uppercase text-white">
                <Plus size={15} /> Add Row
              </button>
            </div>

            <div className="min-h-0 flex-1 overflow-auto border-2 border-blue-950">
              <table className="w-full min-w-[1180px] border-separate border-spacing-y-[2px] text-left">
                <thead className="sticky top-0 z-30">
                  <tr className="bg-blue-950 text-white">
                    <Th className="w-16 text-center">S.No</Th>
                    <Th className="w-44">Details Code</Th>
                    <Th className="w-36">Loan Reference</Th>
                    <Th className="w-24">Area</Th>
                    <Th className="min-w-[340px]">Narration</Th>
                    <Th className="w-36 text-right">Debit</Th>
                    <Th className="w-36 text-right">Credit</Th>
                    <Th className="w-16 text-center">Action</Th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row, index) => (
                    <tr key={row.id} className="bg-white">
                      <Td className="text-center font-black">{index + 1}</Td>
                      <Td>
                        <LedgerLookup value={row.detailsCode} onChange={(value) => updateRow(row.id, { detailsCode: value })} placeholder="Details code..." />
                      </Td>
                      <Td>
                        <ContractLookup
                          value={row.loanReference}
                          onChange={(value) => updateRow(row.id, { loanReference: value, areaCode: readParty(value)?.areaCode || '' })}
                          className="max-w-36"
                        />
                      </Td>
                      <Td>
                        <input value={row.areaCode} readOnly onKeyDown={moveNextOnEnter} className="grid-input bg-gray-100 text-center text-gray-700" />
                      </Td>
                      <Td>
                        <input value={row.narration} onKeyDown={moveNextOnEnter} onChange={(event) => updateRow(row.id, { narration: event.target.value })} className="grid-input" placeholder="Narration" />
                      </Td>
                      <Td>
                        {debitEditable ? (
                          <input
                            type="number"
                            min="0"
                            value={row.debit}
                            onKeyDown={(event) => { blockNegativeNumberKeys(event); moveNextOnEnter(event); }}
                            onPaste={(event) => event.clipboardData.getData('text').includes('-') && event.preventDefault()}
                            onChange={(event) => setNonNegativeRowAmount(row.id, 'debit', event.target.value)}
                            className="grid-input text-right"
                            placeholder="0.00"
                          />
                        ) : (
                          <span className="block h-7"></span>
                        )}
                      </Td>
                      <Td>
                        {creditEditable ? (
                          <input
                            type="number"
                            min="0"
                            value={row.credit}
                            onKeyDown={(event) => { blockNegativeNumberKeys(event); moveNextOnEnter(event); }}
                            onPaste={(event) => event.clipboardData.getData('text').includes('-') && event.preventDefault()}
                            onChange={(event) => setNonNegativeRowAmount(row.id, 'credit', event.target.value)}
                            className="grid-input text-right"
                            placeholder="0.00"
                          />
                        ) : (
                          <span className="block h-7"></span>
                        )}
                      </Td>
                      <Td className="text-center">
                        <button type="button" onClick={() => removeRow(row.id)} className="rounded p-1 text-gray-600 hover:bg-rose-50 hover:text-rose-700">
                          <Trash2 size={16} />
                        </button>
                      </Td>
                    </tr>
                  ))}
                </tbody>
                <tfoot className="sticky bottom-0 z-20">
                  <tr className="bg-blue-50">
                    <td colSpan="5" className="border-r border-blue-200 px-1 py-1 text-right text-[12px] font-black uppercase text-blue-900">Total</td>
                    <td className="border-r border-blue-200 bg-blue-100 px-2 py-1 text-right text-[15px] font-black text-blue-950">{money(footerDebitTotal)}</td>
                    <td className="border-r border-blue-200 bg-blue-100 px-2 py-1 text-right text-[15px] font-black text-blue-950">{money(footerCreditTotal)}</td>
                    <td className="px-1 py-1"></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>

          <div className="shrink-0 border-t-2 border-blue-950 bg-gray-50 px-3 py-1.5">
            <div className="flex items-end justify-between gap-3">
              <div className="min-w-[360px] flex-1 text-left">
                <div className="min-h-14 border border-gray-300 bg-white px-2 py-1.5 text-left text-[12px] font-bold text-gray-800">
                {party ? (
                  <div className="leading-5">
                    <p className="text-[13px] font-black uppercase text-black">{party.partyName}</p>
                    <p>{party.address}</p>
                  </div>
                ) : (
                  <p className="text-[12px] font-black uppercase text-gray-500">Party details will appear for loan category.</p>
                )}
                </div>
              </div>

              <div className="flex shrink-0 items-end justify-end">
                <div className="flex gap-2">
                  {authorisationMode ? (
                    <>
                      <button type="button" onClick={() => navigate(returnTo)} disabled={saving} className="rounded border border-gray-300 bg-white px-5 py-2 text-[13px] font-black uppercase text-gray-800">
                        Back
                      </button>
                      <button type="button" onClick={() => setReasonAction('cancel')} disabled={saving || !editingVoucherId} className="rounded bg-slate-700 px-5 py-2 text-[13px] font-black uppercase text-white disabled:bg-slate-400">
                        Cancel
                      </button>
                      <button type="button" onClick={() => setReasonAction('reject')} disabled={saving || !editingVoucherId} className="rounded bg-rose-700 px-5 py-2 text-[13px] font-black uppercase text-white disabled:bg-rose-400">
                        Reject
                      </button>
                      <button type="button" onClick={handleAuthorise} disabled={saving || !editingVoucherId} className="rounded bg-emerald-700 px-5 py-2 text-[13px] font-black uppercase text-white disabled:bg-emerald-400">
                        Authorise
                      </button>
                    </>
                  ) : (
                    <button
                      type="button"
                      onClick={() => handleSave()}
                      disabled={saving}
                      className="inline-flex min-w-32 items-center justify-center gap-2 rounded bg-blue-800 px-5 py-2 text-[13px] font-black uppercase text-white shadow disabled:cursor-wait disabled:bg-blue-500"
                    >
                      {saving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
                      {saving ? 'Checking' : editMode ? 'Save Changes' : 'Submit'}
                    </button>
                  )}
                  {editMode && !authorisationMode && (
                    <button
                      type="button"
                      onClick={handleSubmitForAuthorisation}
                      disabled={saving || !editingVoucherId}
                      className="inline-flex min-w-48 items-center justify-center gap-2 rounded bg-emerald-700 px-5 py-2 text-[13px] font-black uppercase text-white shadow disabled:cursor-not-allowed disabled:bg-emerald-400"
                    >
                      Submit for Authorisation
                    </button>
                  )}
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>

      {message && (
        <MessageModal message={message} onClose={() => setMessage(null)} />
      )}

      {duplicateConfirm && (
        <ConfirmModal
          title="Duplicate Details"
          text="An exact duplicate detail row was found. Narration is ignored for this check. Do you want to proceed?"
          confirmLabel="Proceed"
          onCancel={() => setDuplicateConfirm(false)}
          onConfirm={() => handleSave(true)}
        />
      )}

      {reasonAction && (
        <ReasonModal
          title={reasonAction === 'reject' ? 'Reject Voucher' : 'Cancel Voucher'}
          onClose={() => setReasonAction(null)}
          onSubmit={handleReasonAction}
        />
      )}

      {searchOpen && (
        <VoucherSearchModal
          kind={kind}
          transactionType={location.pathname.toLowerCase().includes('/bank') ? 'BANK' : location.pathname.toLowerCase().includes('/cash') ? 'CASH' : ''}
          onClose={() => setSearchOpen(false)}
          onSelect={loadVoucherForEdit}
        />
      )}

      <style>{`
        .field-input {
          height: 38px;
          width: 100%;
          border: 1px solid rgb(209 213 219);
          background: white;
          padding: 0 10px;
          font-size: 13px;
          font-weight: 800;
          text-transform: uppercase;
          outline: none;
        }
        .field-input:focus,
        .grid-input:focus {
          border-color: #1d4ed8;
          box-shadow: inset 0 0 0 1px #1d4ed8;
        }
        .grid-input {
          height: 28px;
          width: 100%;
          border: 1px solid rgb(209 213 219);
          background: white;
          padding: 0 6px;
          font-size: 13px;
          font-weight: 800;
          outline: none;
        }
      `}</style>
    </div>
  );
};

const Field = ({ label, children, className = '' }) => (
  <label className={`block ${className}`}>
    <span className="mb-1 block text-[11px] font-black uppercase tracking-widest text-gray-600">{label}</span>
    {children}
  </label>
);

const Th = ({ children, className = '' }) => (
  <th className={`border-r border-blue-800 px-1 py-1 text-[12px] font-black uppercase last:border-r-0 ${className}`}>{children}</th>
);

const Td = ({ children, className = '' }) => (
  <td className={`border-r border-gray-300 px-1 py-0 align-middle last:border-r-0 ${className}`}>{children}</td>
);

const MessageModal = ({ message, onClose }) => (
  <div className="fixed inset-0 z-[300] grid place-items-center bg-black/40 p-4">
    <div className={`w-full max-w-md overflow-hidden rounded-lg border-2 bg-white shadow-2xl ${message.type === 'error' ? 'border-rose-200' : 'border-emerald-200'}`}>
      <div className={`flex items-center justify-between border-b px-4 py-3 ${message.type === 'error' ? 'border-rose-100 bg-rose-50 text-rose-900' : 'border-emerald-100 bg-emerald-50 text-emerald-900'}`}>
        <div className="flex items-center gap-2 text-[13px] font-black uppercase tracking-widest">
          {message.type === 'error' ? <AlertTriangle size={18} /> : <CheckCircle2 size={18} />}
          {message.type === 'error' ? 'Validation' : 'Ready'}
        </div>
        <button type="button" onClick={onClose} className="grid h-8 w-8 place-items-center rounded hover:bg-white">
          <X size={16} />
        </button>
      </div>
      <div className="px-5 py-6 text-center text-[15px] font-bold text-gray-800">{message.text}</div>
      <div className="flex justify-center border-t border-gray-200 bg-gray-50 px-5 py-3">
        <button type="button" onClick={onClose} className="rounded bg-blue-800 px-7 py-2 text-[13px] font-black uppercase text-white">
          OK
        </button>
      </div>
    </div>
  </div>
);

const ConfirmModal = ({ title, text, confirmLabel, onCancel, onConfirm }) => (
  <div className="fixed inset-0 z-[300] grid place-items-center bg-black/40 p-4">
    <div className="w-full max-w-md overflow-hidden rounded-lg border-2 border-amber-200 bg-white shadow-2xl">
      <div className="flex items-center justify-between border-b border-amber-100 bg-amber-50 px-4 py-3 text-amber-900">
        <div className="flex items-center gap-2 text-[13px] font-black uppercase tracking-widest">
          <AlertTriangle size={18} />
          {title}
        </div>
        <button type="button" onClick={onCancel} className="grid h-8 w-8 place-items-center rounded hover:bg-white">
          <X size={16} />
        </button>
      </div>
      <div className="px-5 py-6 text-center text-[15px] font-bold text-gray-800">{text}</div>
      <div className="flex justify-center gap-3 border-t border-gray-200 bg-gray-50 px-5 py-3">
        <button type="button" onClick={onCancel} className="rounded border border-gray-300 bg-white px-6 py-2 text-[13px] font-black uppercase text-gray-800">
          Cancel
        </button>
        <button type="button" onClick={onConfirm} className="rounded bg-blue-800 px-6 py-2 text-[13px] font-black uppercase text-white">
          {confirmLabel}
        </button>
      </div>
    </div>
  </div>
);

const ReasonModal = ({ title, onClose, onSubmit }) => {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  const submit = () => {
    if (!reason.trim()) {
      setError('Reason is mandatory.');
      return;
    }
    onSubmit(reason.trim());
  };

  return (
    <div className="fixed inset-0 z-[310] grid place-items-center bg-black/40 p-4">
      <div className="w-full max-w-md overflow-hidden rounded-lg border-2 border-blue-900 bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-blue-100 bg-blue-50 px-4 py-3">
          <div className="flex items-center gap-2 text-[13px] font-black uppercase tracking-widest text-blue-950">
            <AlertTriangle size={18} />
            {title}
          </div>
          <button type="button" onClick={onClose} className="grid h-8 w-8 place-items-center rounded hover:bg-white">
            <X size={16} />
          </button>
        </div>
        <div className="p-4">
          <textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            className="h-28 w-full resize-none border border-gray-300 p-2 text-[13px] font-bold outline-none focus:border-blue-700"
            placeholder="Enter mandatory reason..."
          />
          {error && <p className="mt-2 text-[12px] font-black uppercase text-rose-700">{error}</p>}
        </div>
        <div className="flex justify-end gap-2 border-t border-gray-200 bg-gray-50 px-4 py-3">
          <button type="button" onClick={onClose} className="rounded border border-gray-300 bg-white px-5 py-2 text-[12px] font-black uppercase text-gray-800">
            Back
          </button>
          <button type="button" onClick={submit} className="rounded bg-blue-800 px-5 py-2 text-[12px] font-black uppercase text-white">
            Submit
          </button>
        </div>
      </div>
    </div>
  );
};

const VoucherSearchModal = ({ kind, transactionType, onClose, onSelect }) => {
  const [filters, setFilters] = useState({ voucherNumber: '', voucherDate: '', contractNumber: '' });
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const search = () => {
    if (!hasAuthToken()) {
      setError('Your login token is missing. Please sign in again before searching vouchers.');
      return;
    }
    setLoading(true);
    setError('');
    searchVouchers({
      voucherType: kind.toUpperCase(),
      transactionType,
      voucherNumber: filters.voucherNumber,
      voucherDate: filters.voucherDate,
      contractNumber: filters.contractNumber,
    })
      .then((data) => setResults(Array.isArray(data) ? data : []))
      .catch((err) => setError(requestErrorMessage(err, 'search vouchers')))
      .finally(() => setLoading(false));
  };

  const selectVoucher = (summary) => {
    const confirmed = window.confirm('This voucher is already authorised. Editing will remove it from reports until it is authorised again. Continue?');
    if (!confirmed) return;
    setLoading(true);
    setError('');
    reopenVoucher(summary.voucherHeaderId, 'Voucher reopened for correction')
      .then(() => fetchVoucher(summary.voucherHeaderId))
      .then(onSelect)
      .catch((err) => setError(requestErrorMessage(err, 'load voucher')))
      .finally(() => setLoading(false));
  };

  return (
    <div className="fixed inset-0 z-[290] grid place-items-center bg-black/50 p-5">
      <div className="flex max-h-[86vh] w-full max-w-5xl flex-col overflow-hidden rounded-lg border-2 border-blue-900 bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-blue-100 bg-blue-50 px-5 py-4">
          <div>
            <h3 className="text-[18px] font-black uppercase text-black">Search Voucher</h3>
            <p className="text-[12px] font-bold uppercase text-gray-600">Select a voucher to edit</p>
          </div>
          <button type="button" onClick={onClose} className="grid h-9 w-9 place-items-center rounded hover:bg-white">
            <X size={18} />
          </button>
        </div>

        <div className="grid grid-cols-1 gap-3 border-b border-gray-200 px-5 py-4 md:grid-cols-4">
          <Field label="Voucher Number">
            <input value={filters.voucherNumber} onChange={(event) => setFilters((current) => ({ ...current, voucherNumber: event.target.value }))} className="field-input" placeholder="Voucher no..." />
          </Field>
          <Field label="Voucher Date">
            <input type="date" value={filters.voucherDate} onChange={(event) => setFilters((current) => ({ ...current, voucherDate: event.target.value }))} className="field-input" />
          </Field>
          <Field label="Contract Number">
            <input value={filters.contractNumber} onChange={(event) => setFilters((current) => ({ ...current, contractNumber: event.target.value }))} className="field-input" placeholder="Contract no..." />
          </Field>
          <div className="flex items-end">
            <button type="button" onClick={search} disabled={loading} className="inline-flex h-[38px] w-full items-center justify-center gap-2 rounded bg-blue-800 px-5 text-[13px] font-black uppercase text-white disabled:bg-blue-500">
              {loading ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
              Search
            </button>
          </div>
        </div>

        {error && <div className="border-b border-rose-100 bg-rose-50 px-5 py-2 text-[13px] font-black text-rose-800">{error}</div>}

        <div className="min-h-[320px] overflow-auto p-5">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="bg-blue-950 text-white">
                <Th>Voucher No</Th>
                <Th>Date</Th>
                <Th>Type</Th>
                <Th>Mode</Th>
                <Th>Contract</Th>
                <Th className="text-right">Amount</Th>
                <Th className="text-center">Rows</Th>
              </tr>
            </thead>
            <tbody>
              {results.map((row) => (
                <tr key={row.voucherHeaderId} onDoubleClick={() => selectVoucher(row)} className="cursor-pointer border-b border-gray-200 hover:bg-blue-50">
                  <Td className="font-black text-blue-900">{row.voucherNumber}</Td>
                  <Td>{displayDate(row.voucherDate)}</Td>
                  <Td>{row.voucherType}</Td>
                  <Td>{row.transactionType || '-'}</Td>
                  <Td>{row.contractNumber || '-'}</Td>
                  <Td className="text-right font-black">{money(row.voucherAmount)}</Td>
                  <Td className="text-center">
                    <button type="button" onClick={() => selectVoucher(row)} className="rounded bg-blue-800 px-4 py-1.5 text-[12px] font-black uppercase text-white">
                      Select
                    </button>
                  </Td>
                </tr>
              ))}
              {!loading && results.length === 0 && (
                <tr>
                  <td colSpan="7" className="px-3 py-10 text-center text-[13px] font-black uppercase text-gray-500">No vouchers found</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

const LedgerLookup = ({ value, onChange, placeholder }) => (
  <AsyncLookup
    value={value}
    placeholder={placeholder}
    getLabel={(item) => (item ? `${item.ledgerCode} - ${item.ledgerName}` : '')}
    fetchOptions={async (keyword) => {
      const data = await fetchLedgerCodes({ keyword, pageSize: 10, filters: { isActive: true } });
      return data?.content || [];
    }}
    onChange={onChange}
  />
);

const ContractLookup = ({ value, onChange, className = '' }) => (
  <AsyncLookup
    value={value}
    placeholder="Contract no..."
    getLabel={(item) => {
      if (!item) return '';
      return item.contractNumber || item.legacyContractNumber || item.contractId || '';
    }}
    fetchOptions={async (keyword) => {
      const data = await fetchContractsPage({ keyword, pageSize: 10, isDraft: false });
      const contracts = data?.content || data?.items || data?.data || [];
      const prefix = keyword.trim().toLowerCase();
      if (!prefix) return contracts;
      return contracts.filter((item) => {
        const contractNumber = String(item.contractNumber || item.legacyContractNumber || item.contractId || '').toLowerCase();
        return contractNumber.startsWith(prefix);
      });
    }}
    renderOption={(item) => (
      <>
        <p className="text-[13px] font-black">{item.contractNumber || item.legacyContractNumber || item.contractId}</p>
      </>
    )}
    onChange={onChange}
    className={className}
  />
);

const AsyncLookup = ({ value, onChange, fetchOptions, getLabel, renderOption, placeholder, className = '' }) => {
  const wrapperRef = useRef(null);
  const [query, setQuery] = useState(getLabel(value));
  const [options, setOptions] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);

  const selectOption = useCallback((option) => {
    if (!option) return;
    onChange(option);
    setQuery(getLabel(option));
    setOpen(false);
  }, [getLabel, onChange]);

  useEffect(() => {
    if (!open) setQuery(getLabel(value));
    setActiveIndex(0);
  }, [getLabel, open, value]);

  useEffect(() => {
    if (!open) return undefined;

    const handlePointerDown = (event) => {
      if (!wrapperRef.current?.contains(event.target)) {
        setOpen(false);
      }
    };

    document.addEventListener('mousedown', handlePointerDown);
    return () => document.removeEventListener('mousedown', handlePointerDown);
  }, [open]);

  useEffect(() => {
    if (!open || query.trim().length < 1) {
      setOptions([]);
      return undefined;
    }

    let active = true;
    const timer = window.setTimeout(async () => {
      setLoading(true);
      try {
        const result = await fetchOptions(query.trim());
        if (active) {
          setOptions(Array.isArray(result) ? result : []);
          setActiveIndex(0);
        }
      } catch {
        if (active) setOptions([]);
      } finally {
        if (active) setLoading(false);
      }
    }, 250);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [fetchOptions, open, query]);

  const handleKeyDown = (event) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((current) => Math.min(current + 1, Math.max(options.length - 1, 0)));
      return;
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex((current) => Math.max(current - 1, 0));
      return;
    }

    if (event.key === 'Enter' || event.key === 'Tab') {
      if (open && options.length > 0) {
        event.preventDefault();
        selectOption(options[activeIndex] || options[0]);
        window.setTimeout(() => focusNextInput(event.currentTarget), 0);
        return;
      }

      if (event.key === 'Enter') {
        event.preventDefault();
        focusNextInput(event.currentTarget);
      }
    }

    if (event.key === 'Escape') {
      setOpen(false);
    }
  };

  return (
    <div ref={wrapperRef} className={`relative ${className}`}>
      <div className="relative">
        <input
          value={query}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          onChange={(event) => {
            setQuery(event.target.value);
            setOpen(true);
            onChange(null);
            setActiveIndex(0);
          }}
          placeholder={placeholder}
          className="field-input pr-9"
        />
        {loading ? <Loader2 size={15} className="absolute right-3 top-3 animate-spin text-blue-700" /> : <Search size={15} className="absolute right-3 top-3 text-gray-600" />}
      </div>

      {open && options.length > 0 && (
        <div className="absolute left-0 right-0 top-10 z-50 max-h-64 overflow-auto border-2 border-blue-950 bg-white shadow-xl">
          {options.map((option, index) => (
            <button
              key={`${getLabel(option)}-${index}`}
              type="button"
              onMouseDown={(event) => event.preventDefault()}
              onMouseEnter={() => setActiveIndex(index)}
              onClick={() => selectOption(option)}
              className={`block w-full border-b border-gray-200 px-3 py-2 text-left ${activeIndex === index ? 'bg-blue-100' : 'hover:bg-blue-50'}`}
            >
              {renderOption ? renderOption(option) : (
                <>
                  <p className="text-[13px] font-black">{getLabel(option)}</p>
                  <p className="text-[11px] font-bold uppercase text-gray-600">Ledger master</p>
                </>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default ReceiptVoucher;
