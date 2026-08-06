import { afcDate, afcMoney } from './AfcReportHeader';

const columns = [
  ['serialNumber', 'Sl No', 'right'],
  ['dueDate', 'Due Date', 'center date'],
  ['whenPaid', 'When Paid', 'center date'],
  ['dueAmount', 'Due Amount', 'right money'],
  ['emiAmountPaid', 'EMI Amount Paid', 'right money'],
  ['receiptNumber', 'Receipt Number', 'left'],
  ['delayDays', 'Delay Days', 'right'],
  ['afcAmount', 'AFC Amount', 'right money'],
  ['contractBalance', 'Contract Balance', 'right money'],
  ['currentAccountBalance', 'Current Account Balance', 'right money'],
];

function value(row, field, type) {
  if (type.includes('money')) return afcMoney(row[field]);
  if (type.includes('date')) return afcDate(row[field]);
  return row[field] ?? '';
}

function align(type) {
  if (type.includes('right')) return 'text-right';
  if (type.includes('center')) return 'text-center';
  return 'text-left';
}

export default function AfcReportGrid({ rows }) {
  return (
    <div className="no-print min-h-0 flex-1 overflow-auto bg-white">
      <table className="min-w-[1350px] border-collapse text-[12px] text-black">
        <thead className="sticky top-0 z-20 bg-[#1f2937] text-[12px] font-bold text-white">
          <tr>
            {columns.map(([field, label, type]) => (
              <th key={field} className={`border border-black/40 px-2 py-1 ${align(type)}`}>{label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={`${row.serialNumber}-${row.dueDate}`} className={index % 2 === 0 ? 'bg-white' : 'bg-slate-50'}>
              {columns.map(([field, , type]) => (
                <td key={field} className={`border border-black/30 px-2 py-1 ${align(type)}`}>{value(row, field, type)}</td>
              ))}
            </tr>
          ))}
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

export { columns as afcColumns, value as afcValue, align as afcAlign };
