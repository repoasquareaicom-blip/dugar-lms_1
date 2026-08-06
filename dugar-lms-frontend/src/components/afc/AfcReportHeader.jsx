function money(value) {
  return Number(value || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function date(value) {
  if (!value) return '';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat('en-GB').format(parsed);
}

const fields = [
  ['Loan Number', 'loanNumber'],
  ['Customer Name', 'customerName'],
  ['Product Type', 'productType'],
  ['Loan Amount', 'loanAmount', 'money'],
  ['Finance Charges', 'financeCharges', 'money'],
  ['Contract Value', 'contractValue', 'money'],
  ['EMI Start Date', 'emiStartDate', 'date'],
  ['As On Date', 'asOnDate', 'date'],
];

export default function AfcReportHeader({ header }) {
  const data = header || {};
  return (
    <div className="no-print grid grid-cols-2 border-b border-black/20 bg-[#f8fafc] text-[12px] text-black md:grid-cols-4 xl:grid-cols-8">
      {fields.map(([label, key, type]) => (
        <div key={key} className="border-r border-black/20 px-2 py-1 last:border-r-0">
          <div className="font-semibold uppercase text-black/60">{label}</div>
          <div className={`font-bold ${type === 'money' ? 'text-right' : ''}`}>{type === 'money' ? money(data[key]) : type === 'date' ? date(data[key]) : data[key] || ''}</div>
        </div>
      ))}
    </div>
  );
}

export { money as afcMoney, date as afcDate };
