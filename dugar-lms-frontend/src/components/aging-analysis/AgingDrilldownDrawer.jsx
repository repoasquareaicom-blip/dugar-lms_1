import { X } from 'lucide-react';

function formatMoney(value) {
  if (value === null || value === undefined || value === '') return '';
  return Number(value).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function rawColumns(rows) {
  const seen = new Set();
  rows.forEach((row) => {
    Object.keys(row || {}).forEach((key) => seen.add(key));
  });
  return Array.from(seen);
}

function formatRawValue(value) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function RawTable({ title, rows }) {
  const columns = rawColumns(rows);
  return (
    <div className="mb-4">
      <div className="mb-2 text-[13px] font-black uppercase text-[#0052CC]">{title}</div>
      <div className="overflow-auto">
        <table className="min-w-full border-collapse">
          <thead className="bg-[#e8edf5] font-bold">
            <tr>{columns.map((column) => <th key={column} className="border border-black/30 px-2 py-1 text-left">{column}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={index}>
                {columns.map((column) => <td key={column} className="border border-black/20 px-2 py-1 align-top">{formatRawValue(row?.[column])}</td>)}
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={Math.max(columns.length, 1)} className="border border-black/20 px-3 py-5 text-center font-bold uppercase text-black/50">No rows found</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function AgingDrilldownDrawer({ state, onClose, onSelectContract, onSelectReceipt }) {
  if (!state?.open) return null;
  const contracts = state.contracts || [];
  const detail = state.detail;
  const emis = state.emis || [];
  const receipts = state.receipts || [];
  const rawVoucher = state.rawVoucher || { headers: [], details: [] };

  return (
    <div className="no-print fixed inset-0 z-[120]">
      <button type="button" aria-label="Close aging drilldown" className="absolute inset-0 bg-black/20" onClick={onClose} />
      <div className="absolute inset-y-0 right-0 flex w-[860px] max-w-[96vw] flex-col border-l-2 border-black/20 bg-white text-[12px] text-black shadow-2xl">
        <div className="flex items-center justify-between border-b border-black/20 bg-slate-50 px-3 py-2">
          <div className="font-bold uppercase">{state.title || 'Aging Drilldown'}</div>
          <button type="button" onClick={onClose} className="inline-flex h-8 w-8 items-center justify-center rounded border border-red-300 bg-red-50 text-red-700 hover:bg-red-100" title="Close">
            <X size={16} />
          </button>
        </div>
        {state.loading && (
          <div className="flex items-center gap-2 border-b border-blue-100 bg-blue-50 px-3 py-2 font-bold text-blue-700">
            <div className="h-4 w-4 animate-spin rounded-full border-2 border-blue-200 border-t-[#0052CC]" />
            Loading drill-down...
          </div>
        )}
        {state.message && <div className="border-b border-red-200 bg-red-50 px-3 py-2 font-bold text-red-700">{state.message}</div>}
        <div className="min-h-0 flex-1 overflow-auto p-3">
          <div className="mb-3 text-[13px] font-black uppercase text-[#0052CC]">Contracts</div>
          {state.loading && contracts.length === 0 && (
            <div className="flex h-32 items-center justify-center border border-dashed border-blue-200 bg-blue-50/50">
              <div className="text-center">
                <div className="mx-auto h-8 w-8 animate-spin rounded-full border-4 border-blue-200 border-t-[#0052CC]" />
                <div className="mt-2 font-bold uppercase text-[#0052CC]">Loading</div>
              </div>
            </div>
          )}
          {(!state.loading || contracts.length > 0) && <table className="mb-4 min-w-full border-collapse">
            <thead className="bg-[#e8edf5] font-bold">
              <tr>
                {['Contract Number', 'Borrower', 'AUM', 'Total Outstanding', 'Overdue EMIs', 'Bucket'].map((label) => <th key={label} className="border border-black/30 px-2 py-1 text-left">{label}</th>)}
              </tr>
            </thead>
            <tbody>
              {contracts.map((row) => (
                <tr key={row.contractId} className="hover:bg-blue-50">
                  <td className="border border-black/20 px-2 py-1">
                    <button type="button" className="font-bold text-[#0052CC] underline" onClick={() => onSelectContract(row.contractId)}>{row.loanNumber}</button>
                  </td>
                  <td className="border border-black/20 px-2 py-1">{row.borrowerName || ''}</td>
                  <td className="border border-black/20 px-2 py-1 text-right">{formatMoney(row.principalOutstanding)}</td>
                  <td className="border border-black/20 px-2 py-1 text-right">{formatMoney(row.totalOutstanding)}</td>
                  <td className="border border-black/20 px-2 py-1 text-right">{row.overdueInstallmentCount || 0}</td>
                  <td className="border border-black/20 px-2 py-1">{state.bucketLabel || ''}</td>
                </tr>
              ))}
              {!state.loading && contracts.length === 0 && <tr><td colSpan="6" className="border border-black/20 px-3 py-5 text-center font-bold uppercase text-black/50">No contracts found</td></tr>}
            </tbody>
          </table>}

          {detail && (
            <>
              <div className="mb-2 text-[13px] font-black uppercase text-[#0052CC]">Contract Details - {detail.contractNumber}</div>
              <div className="mb-4 grid grid-cols-2 gap-x-4 border border-black/20 p-2">
                {[
                  ['Area Code', detail.areaCode],
                  ['Total Contract Value', formatMoney(detail.totalContractValue)],
                  ['Finance Charges', formatMoney(detail.financeCharges)],
                  ['Original Principal', formatMoney(detail.originalPrincipal)],
                  ['Total Received', formatMoney(detail.totalReceived)],
                  ['Principal Recovered', formatMoney(detail.principalRecovered)],
                  ['AUM', formatMoney(detail.aum)],
                  ['Total Outstanding', formatMoney(detail.totalOutstanding)],
                  ['Overdue EMIs', detail.overdueEmiCount],
                  ['Ageing Bucket', detail.ageingBucket],
                ].map(([label, value]) => (
                  <div key={label} className="contents">
                    <div className="border-b border-black/10 py-1 font-bold uppercase text-black/60">{label}</div>
                    <div className="border-b border-black/10 py-1 text-right font-bold">{value}</div>
                  </div>
                ))}
              </div>

              <div className="mb-2 text-[13px] font-black uppercase text-[#0052CC]">EMI Schedule</div>
              <table className="mb-4 min-w-full border-collapse">
                <thead className="bg-[#e8edf5] font-bold"><tr>{['EMI No', 'Due Date', 'Installment', 'Paid', 'Outstanding', 'Status'].map((label) => <th key={label} className="border border-black/30 px-2 py-1 text-left">{label}</th>)}</tr></thead>
                <tbody>{emis.map((emi) => <tr key={emi.emiNumber}><td className="border border-black/20 px-2 py-1">{emi.emiNumber}</td><td className="border border-black/20 px-2 py-1">{emi.dueDate || ''}</td><td className="border border-black/20 px-2 py-1 text-right">{formatMoney(emi.installmentAmount)}</td><td className="border border-black/20 px-2 py-1 text-right">{formatMoney(emi.paidAmount)}</td><td className="border border-black/20 px-2 py-1 text-right">{formatMoney(emi.outstandingAmount)}</td><td className="border border-black/20 px-2 py-1 font-bold">{emi.status}</td></tr>)}</tbody>
              </table>

              <div className="mb-2 text-[13px] font-black uppercase text-[#0052CC]">Receipt History</div>
              <table className="min-w-full border-collapse">
                <thead className="bg-[#e8edf5] font-bold"><tr>{['Date', 'Voucher No', 'Type', 'Receipt No', 'Ledger', 'Sub Ledger', 'Debit', 'Credit', 'Narration'].map((label) => <th key={label} className="border border-black/30 px-2 py-1 text-left">{label}</th>)}</tr></thead>
                <tbody>{receipts.map((receipt, index) => {
                  const selected = state.rawVoucherKey === `${receipt.voucherType || ''}:${receipt.voucherNumber || ''}`;
                  return (
                    <tr
                      key={`${receipt.voucherNumber}-${index}`}
                      className={`${onSelectReceipt ? 'cursor-pointer hover:bg-blue-50' : ''} ${selected ? 'bg-blue-50' : ''}`}
                      onClick={() => onSelectReceipt?.(receipt)}
                    >
                      <td className="border border-black/20 px-2 py-1">{receipt.voucherDate || ''}</td>
                      <td className="border border-black/20 px-2 py-1">
                        {onSelectReceipt ? <button type="button" className="font-bold text-[#0052CC] underline decoration-dotted underline-offset-2">{receipt.voucherNumber || ''}</button> : receipt.voucherNumber || ''}
                      </td>
                      <td className="border border-black/20 px-2 py-1">{receipt.voucherType || ''}</td>
                      <td className="border border-black/20 px-2 py-1">{receipt.receiptNumber || receipt.temporaryReceiptNumber || ''}</td>
                      <td className="border border-black/20 px-2 py-1">{receipt.ledgerCode || ''}</td>
                      <td className="border border-black/20 px-2 py-1">{receipt.subLedgerCode || ''}</td>
                      <td className="border border-black/20 px-2 py-1 text-right">{formatMoney(receipt.debitAmount)}</td>
                      <td className="border border-black/20 px-2 py-1 text-right">{formatMoney(receipt.collectionAmount)}</td>
                      <td className="border border-black/20 px-2 py-1">{receipt.narration || ''}</td>
                    </tr>
                  );
                })}</tbody>
              </table>

              {(state.rawVoucherLoading || state.rawVoucherMessage || state.rawVoucherKey) && (
                <div className="mt-4 border-t border-black/20 pt-3">
                  {state.rawVoucherLoading && (
                    <div className="mb-3 flex items-center gap-2 border border-blue-100 bg-blue-50 px-3 py-2 font-bold text-blue-700">
                      <div className="h-4 w-4 animate-spin rounded-full border-2 border-blue-200 border-t-[#0052CC]" />
                      Loading raw voucher...
                    </div>
                  )}
                  {state.rawVoucherMessage && <div className="mb-3 border border-red-200 bg-red-50 px-3 py-2 font-bold text-red-700">{state.rawVoucherMessage}</div>}
                  {!state.rawVoucherLoading && state.rawVoucherKey && (
                    <>
                      <RawTable title="Raw Voucher Header" rows={rawVoucher.headers || []} />
                      <RawTable title="Raw Voucher Details" rows={rawVoucher.details || []} />
                    </>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
