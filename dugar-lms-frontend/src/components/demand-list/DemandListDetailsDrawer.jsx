import { X } from 'lucide-react';
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
  if (!row) return null;

  return (
    <div className="no-print fixed inset-y-0 right-0 z-50 w-[420px] border-l-2 border-black/20 bg-white text-[12px] text-black shadow-2xl">
      <div className="flex items-center justify-between border-b border-black/20 px-3 py-2">
        <div className="font-bold uppercase">Demand Details</div>
        <button type="button" onClick={onClose} className="rounded border border-black/20 p-1"><X size={14} /></button>
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
  );
}
