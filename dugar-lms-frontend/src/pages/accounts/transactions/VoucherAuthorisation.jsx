import React, { useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, ShieldCheck, X } from 'lucide-react';
import ServerDataTable from '../../../components/common/ServerDataTable';
import {
  authoriseVoucher,
  cancelVoucher,
  fetchAuthorisationQueue,
  fetchVoucherReview,
  rejectVoucher,
} from '../../../services/voucherService';

function money(value) {
  return Number(value || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function displayDate(value) {
  if (!value) return '-';
  const date = new Date(String(value).includes('T') ? value : `${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: String(value).includes('T') ? '2-digit' : undefined,
    minute: String(value).includes('T') ? '2-digit' : undefined,
  }).replace(/ /g, '-');
}

function requestErrorMessage(error, action) {
  return error?.response?.data?.message || error?.message || `Unable to ${action}.`;
}

const columns = [
  { field: 'serialNumber', headerName: 'S.No', minWidth: 70, align: 'center', headerAlign: 'center' },
  { field: 'voucherType', headerName: 'Voucher Type', minWidth: 130, sortable: true },
  { field: 'voucherNumber', headerName: 'Voucher Number', minWidth: 150, sortable: true },
  { field: 'voucherDate', headerName: 'Voucher Date', minWidth: 130, sortable: true, formatter: displayDate },
  { field: 'headerControlName', headerName: 'Party Name', minWidth: 220 },
  { field: 'contractNumber', headerName: 'Contract Number', minWidth: 150 },
  { field: 'voucherAmount', headerName: 'Voucher Amount', minWidth: 150, sortable: true, align: 'right', headerAlign: 'right', formatter: money },
  { field: 'submittedBy', headerName: 'Submitted By', minWidth: 130, sortable: true },
  { field: 'submittedAt', headerName: 'Submitted At', minWidth: 180, sortable: true, formatter: displayDate },
  { field: 'status', headerName: 'Current Status', minWidth: 140 },
  {
    field: 'action',
    headerName: 'Action',
    minWidth: 110,
    align: 'center',
    headerAlign: 'center',
    renderCell: () => <span className="rounded bg-blue-800 px-3 py-1 text-[11px] font-black uppercase text-white">Review</span>,
  },
];

const filterFields = [
  { field: 'voucherType', label: 'Voucher Type' },
  { field: 'voucherDateFrom', label: 'Date From', type: 'date' },
  { field: 'voucherDateTo', label: 'Date To', type: 'date' },
  { field: 'minimumAmount', label: 'Min Amount', type: 'number' },
  { field: 'maximumAmount', label: 'Max Amount', type: 'number' },
  { field: 'submittedBy', label: 'Submitted By' },
];

export default function VoucherAuthorisation() {
  const [review, setReview] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState(null);
  const [reasonAction, setReasonAction] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  const difference = useMemo(() => Number(review?.totalDebit || 0) - Number(review?.totalCredit || 0), [review]);

  const fetchPage = async ({ filters, keyword, page, pageSize, sortColumn, sortDirection }) => fetchAuthorisationQueue({
    keyword,
    voucherType: filters.voucherType,
    voucherDateFrom: filters.voucherDateFrom,
    voucherDateTo: filters.voucherDateTo,
    minimumAmount: filters.minimumAmount,
    maximumAmount: filters.maximumAmount,
    submittedBy: filters.submittedBy,
    page,
    size: pageSize,
    sortColumn,
    sortDirection,
  });

  const openReview = (voucher) => {
    setBusy(true);
    fetchVoucherReview(voucher.voucherHeaderId)
      .then(setReview)
      .catch((error) => setMessage({ type: 'error', text: requestErrorMessage(error, 'load voucher review') }))
      .finally(() => setBusy(false));
  };

  const runAction = (action, reason = '') => {
    if (!review) return;
    setBusy(true);
    const request = action === 'authorise'
      ? authoriseVoucher(review.voucherHeaderId)
      : action === 'reject'
        ? rejectVoucher(review.voucherHeaderId, reason)
        : cancelVoucher(review.voucherHeaderId, reason);

    request
      .then(() => {
        setReview(null);
        setReasonAction(null);
        setReloadKey((value) => value + 1);
        setMessage({ type: 'success', text: `Voucher ${action}d successfully.` });
      })
      .catch((error) => setMessage({ type: 'error', text: requestErrorMessage(error, action) }))
      .finally(() => setBusy(false));
  };

  return (
    <div className="h-full min-h-0 bg-gray-100 p-2 text-black" style={{ fontFamily: 'Calibri, "Segoe UI", sans-serif' }}>
      <ServerDataTable
        key={reloadKey}
        columns={columns}
        defaultFilters={{ voucherType: '', voucherDateFrom: '', voucherDateTo: '', minimumAmount: '', maximumAmount: '', submittedBy: '' }}
        defaultPageSize={25}
        defaultSortColumn="submittedAt"
        defaultSortDirection="desc"
        fetchPage={fetchPage}
        filterFields={filterFields}
        getRowId={(row) => row.voucherHeaderId}
        loadingLabel="Loading pending vouchers"
        onRowClick={openReview}
        onRowDoubleClick={openReview}
        searchPlaceholder="Search voucher, party, contract..."
        title="Voucher Authorisation Queue"
        transformResponse={(data) => ({ rows: data.content || [], totalElements: data.totalElements || 0 })}
      />

      {busy && <div className="fixed inset-0 z-[260] grid place-items-center bg-white/40" />}
      {message && <MessageModal message={message} onClose={() => setMessage(null)} />}
      {review && (
        <ReviewModal
          review={review}
          difference={difference}
          onClose={() => setReview(null)}
          onAuthorise={() => runAction('authorise')}
          onReject={() => setReasonAction('reject')}
          onCancel={() => setReasonAction('cancel')}
        />
      )}
      {reasonAction && (
        <ReasonModal
          title={reasonAction === 'reject' ? 'Reject Voucher' : 'Cancel Voucher'}
          onClose={() => setReasonAction(null)}
          onSubmit={(reason) => runAction(reasonAction, reason)}
        />
      )}
    </div>
  );
}

function ReviewModal({ review, difference, onClose, onAuthorise, onReject, onCancel }) {
  const balanced = Math.abs(difference) < 0.01;
  return (
    <div className="fixed inset-0 z-[280] grid place-items-center bg-black/50 p-4">
      <div className="flex max-h-[92vh] w-full max-w-6xl flex-col overflow-hidden border-2 border-blue-950 bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-300 bg-blue-50 px-4 py-2">
          <div>
            <h3 className="text-[20px] font-black uppercase">Voucher Review: {review.voucherNumber}</h3>
            <p className="text-[12px] font-bold uppercase text-gray-600">Status {review.status} | Version {review.versionNumber}</p>
          </div>
          <button type="button" onClick={onClose} className="grid h-8 w-8 place-items-center hover:bg-white"><X size={18} /></button>
        </div>
        <div className="grid grid-cols-2 gap-2 border-b border-gray-300 p-3 text-[12px] font-bold lg:grid-cols-6">
          <Info label="Voucher Type" value={review.voucherType} />
          <Info label="Voucher Date" value={displayDate(review.voucherDate)} />
          <Info label="System Date" value={displayDate(review.systemDate)} />
          <Info label="Mode" value={review.transactionType || '-'} />
          <Info label="Contract" value={review.contractNumber || '-'} />
          <Info label="Amount" value={money(review.voucherAmount)} right />
          <Info label="Header Ledger" value={`${review.headerControlCode || '-'} ${review.headerControlName || ''}`} wide />
          <Info label="Submitted By" value={review.submittedBy || '-'} />
          <Info label="Submitted At" value={displayDate(review.submittedAt)} />
        </div>
        <div className="min-h-0 flex-1 overflow-auto p-3">
          <table className="w-full min-w-[1000px] border-collapse text-left text-[13px]">
            <thead className="bg-blue-950 text-white">
              <tr>{['Serial No', 'Category', 'Ledger Code', 'Ledger Name', 'Loan Reference', 'Party', 'Narration', 'Debit', 'Credit'].map((h) => <th key={h} className="border border-blue-800 px-2 py-1 text-[12px] font-black uppercase">{h}</th>)}</tr>
            </thead>
            <tbody>
              {(review.details || []).map((detail) => (
                <tr key={detail.voucherDetailId}>
                  <td className="border border-gray-300 px-2 py-1 text-center">{detail.serialNumber}</td>
                  <td className="border border-gray-300 px-2 py-1">{detail.category}</td>
                  <td className="border border-gray-300 px-2 py-1">{detail.ledgerCode}</td>
                  <td className="border border-gray-300 px-2 py-1">{detail.ledgerName}</td>
                  <td className="border border-gray-300 px-2 py-1">{detail.loanReference || '-'}</td>
                  <td className="border border-gray-300 px-2 py-1">{detail.partyName || '-'}</td>
                  <td className="border border-gray-300 px-2 py-1">{detail.narration || '-'}</td>
                  <td className="border border-gray-300 px-2 py-1 text-right font-black">{money(detail.debitAmount)}</td>
                  <td className="border border-gray-300 px-2 py-1 text-right font-black">{money(detail.creditAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="mt-3 grid grid-cols-2 gap-2 text-[13px] font-black uppercase lg:grid-cols-5">
            <Info label="Total Debit" value={money(review.totalDebit)} right />
            <Info label="Total Credit" value={money(review.totalCredit)} right />
            <Info label="Header Amount" value={money(review.voucherAmount)} right />
            <Info label="Difference" value={money(difference)} right />
            <div className={`border px-2 py-2 text-center ${balanced ? 'border-emerald-500 bg-emerald-50 text-emerald-800' : 'border-rose-500 bg-rose-50 text-rose-800'}`}>{balanced ? 'Balanced' : 'Not Balanced'}</div>
          </div>
          {review.history?.length > 0 && (
            <div className="mt-3 border border-gray-300 bg-gray-50 p-2 text-[12px] font-bold">
              <p className="mb-1 font-black uppercase">Previous Authorisation History</p>
              {review.history.map((item) => (
                <p key={item.voucherHeaderHistoryId}>Version {item.versionNumber} | {item.previousStatus} | {displayDate(item.changedAt)} | {item.changeReason || '-'}</p>
              ))}
            </div>
          )}
        </div>
        <div className="flex justify-end gap-2 border-t border-gray-300 bg-gray-50 px-4 py-3">
          <button onClick={onClose} className="border border-gray-400 bg-white px-5 py-2 text-[12px] font-black uppercase">Back</button>
          <button onClick={onCancel} className="bg-slate-700 px-5 py-2 text-[12px] font-black uppercase text-white">Cancel</button>
          <button onClick={onReject} className="bg-rose-700 px-5 py-2 text-[12px] font-black uppercase text-white">Reject</button>
          <button onClick={onAuthorise} className="inline-flex items-center gap-2 bg-emerald-700 px-5 py-2 text-[12px] font-black uppercase text-white"><ShieldCheck size={15} /> Authorise</button>
        </div>
      </div>
    </div>
  );
}

function Info({ label, value, right, wide }) {
  return <div className={`border border-gray-300 bg-white px-2 py-1 ${wide ? 'lg:col-span-2' : ''}`}><p className="text-[10px] font-black uppercase text-gray-500">{label}</p><p className={`truncate text-[13px] font-black text-black ${right ? 'text-right' : ''}`}>{value}</p></div>;
}

function ReasonModal({ title, onClose, onSubmit }) {
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
    <div className="fixed inset-0 z-[300] grid place-items-center bg-black/50 p-4">
      <div className="w-full max-w-md border-2 border-blue-950 bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-300 bg-blue-50 px-4 py-3">
          <p className="flex items-center gap-2 text-[14px] font-black uppercase"><AlertTriangle size={17} /> {title}</p>
          <button onClick={onClose}><X size={18} /></button>
        </div>
        <div className="p-4">
          <textarea value={reason} onChange={(event) => setReason(event.target.value)} className="h-28 w-full border border-gray-400 p-2 text-[13px] font-bold outline-none" placeholder="Enter mandatory reason..." />
          {error && <p className="mt-2 text-[12px] font-black uppercase text-rose-700">{error}</p>}
        </div>
        <div className="flex justify-end gap-2 border-t border-gray-300 bg-gray-50 px-4 py-3">
          <button onClick={onClose} className="border border-gray-400 bg-white px-5 py-2 text-[12px] font-black uppercase">Back</button>
          <button onClick={submit} className="bg-blue-900 px-5 py-2 text-[12px] font-black uppercase text-white">Submit</button>
        </div>
      </div>
    </div>
  );
}

function MessageModal({ message, onClose }) {
  return (
    <div className="fixed inset-0 z-[310] grid place-items-center bg-black/40 p-4">
      <div className={`w-full max-w-md border-2 bg-white shadow-2xl ${message.type === 'error' ? 'border-rose-300' : 'border-emerald-300'}`}>
        <div className={`flex items-center justify-between border-b px-4 py-3 ${message.type === 'error' ? 'bg-rose-50 text-rose-900' : 'bg-emerald-50 text-emerald-900'}`}>
          <p className="flex items-center gap-2 text-[13px] font-black uppercase">{message.type === 'error' ? <AlertTriangle size={18} /> : <CheckCircle2 size={18} />} {message.type === 'error' ? 'Error' : 'Success'}</p>
          <button onClick={onClose}><X size={18} /></button>
        </div>
        <div className="p-5 text-center text-[14px] font-bold">{message.text}</div>
      </div>
    </div>
  );
}
