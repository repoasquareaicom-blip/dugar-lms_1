function money(value) {
  const number = Number(value || 0);
  return number.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const items = [
  ['Contract Count', 'contractCount', false],
  ['Total Contract Value', 'totalContractValue', true],
  ['Total Authorised Receipts', 'totalAuthorisedReceipts', true],
  ['Principal Outstanding', 'principalOutstanding', true],
  ['Interest Outstanding', 'interestOutstanding', true],
  ['Total Outstanding', 'totalOutstanding', true],
  ['Overdue Amount', 'overdueAmount', true],
  ['Current Due', 'currentDue', true],
];

export default function DemandListSummary({ summary }) {
  const data = summary || {};
  return (
    <div className="no-print grid grid-cols-2 border-b border-black/20 bg-[#f8fafc] text-[12px] text-black md:grid-cols-4 xl:grid-cols-8">
      {items.map(([label, key, currency]) => (
        <div key={key} className="border-r border-black/20 px-2 py-1 last:border-r-0">
          <div className="font-semibold uppercase text-black/60">{label}</div>
          <div className="text-right font-bold">{currency ? money(data[key]) : Number(data[key] || 0).toLocaleString('en-IN')}</div>
        </div>
      ))}
    </div>
  );
}
