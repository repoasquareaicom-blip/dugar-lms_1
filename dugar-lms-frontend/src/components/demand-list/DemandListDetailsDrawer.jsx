import { X } from 'lucide-react';
import { useEffect } from 'react';
import { formatDate, formatMoney } from './DemandListGrid';

const fields = [
  ['Loan No.', 'loanNumber'],
  ['Borrower Name', 'borrowerName'],
  ['Guarantor Name', 'guarantorName'],
  ['Product Type', 'productType'],
  ['Asset / Property', 'assetDescription'],
  ['Registration / Location', 'registrationOrLocation'],
  ['Contract Value', 'contractValue', 'money'],
  ['Authorised Receipts', 'authorisedReceipts', 'money'],
  ['Principal Outstanding', 'principalOutstanding', 'money'],
  ['Interest Outstanding', 'interestOutstanding', 'money'],
  ['Total Outstanding', 'totalOutstanding', 'money'],
  ['No. of Overdues', 'overdueInstallmentCount'],
  ['Overdue Amount', 'overdueAmount', 'money'],
  ['Overdue From Date', 'overdueFromDate', 'date'],
  ['Overdue End Date', 'overdueEndDate', 'date'],
  ['Current Due', 'currentDueAmount', 'money'],
  ['Current Due Date', 'currentDueDate', 'date'],
  ['Warning', 'warning'],
];

function display(row, field, type) {
  if (type === 'money') return formatMoney(row[field]);
  if (type === 'date') return formatDate(row[field]);
  return row[field] || '';
}

export default function DemandListDetailsDrawer({ row, onClose }) {
  useEffect(() => {
    if (!row) return undefined;
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose?.();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose, row]);

  if (!row) return null;

  return (
    <div className="no-print fixed inset-0 z-50">
      <button
        type="button"
        aria-label="Close demand details"
        className="absolute inset-0 bg-black/20"
        onClick={onClose}
      />
      <div className="absolute inset-y-0 right-0 w-[420px] border-l-2 border-black/20 bg-white text-[12px] text-black shadow-2xl">
        <div className="flex items-center justify-between border-b border-black/20 bg-slate-50 px-3 py-2">
          <div className="font-bold uppercase">Demand Details</div>
          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-8 w-8 items-center justify-center rounded border border-red-300 bg-red-50 text-red-700 hover:bg-red-100"
            title="Close"
          >
            <X size={16} />
          </button>
        </div>
        <div className="grid grid-cols-[150px_1fr] gap-0 p-3">
          {fields.map(([label, field, type]) => (
            <div key={field} className="contents">
              <div className="border-b border-black/10 py-1 font-semibold uppercase text-black/60">{label}</div>
              <div className="border-b border-black/10 py-1 text-right font-bold">{display(row, field, type)}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
