const groupedColumns = [
  { key: 'serialNumber', label: ['Sl. No'], align: 'right', sortField: 'serialNumber', width: 'w-[70px]' },
  { key: 'loanNumber', label: ['Loan No.'], sortField: 'loanNumber', width: 'w-[110px]' },
  { key: 'party', label: ['Name of Borrower', 'Name of Guarantor'], sortField: 'borrowerName', width: 'w-[230px]' },
  {
    key: 'asset',
    label: ['Product Type', 'Make of Vehicle/', 'Regn No./Location', 'No .of Owner', 'Product usage'],
    sortField: 'productType',
    width: 'w-[260px]',
  },
  {
    key: 'outstanding',
    label: ['Contract Value', 'Principal O/S', 'Interest O/S', 'Total O/S'],
    align: 'right',
    sortField: 'contractValue',
    width: 'w-[175px]',
  },
  {
    key: 'overdue',
    label: ['No.of Overdues', 'O/D Amount', 'From Date', 'End Date'],
    align: 'right',
    sortField: 'overdueInstallmentCount',
    width: 'w-[165px]',
  },
  { key: 'currentDue', label: ['Current Due'], align: 'right', sortField: 'currentDueAmount', width: 'w-[125px]' },
  { key: 'currentDueDate', label: ['Due Date'], align: 'center', sortField: 'currentDueDate', width: 'w-[110px]' },
];

function formatDate(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en-GB').format(date);
}

function formatMoney(value) {
  if (value === null || value === undefined || value === '') return '';
  return Number(value).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function firstValue(row, keys) {
  for (const key of keys) {
    if (row[key] !== null && row[key] !== undefined && row[key] !== '') return row[key];
  }
  return '';
}

function lineValues(row, key) {
  switch (key) {
    case 'serialNumber':
      return [row.serialNumber];
    case 'loanNumber':
      return [row.loanNumber || ''];
    case 'party':
      return [row.borrowerName || '', row.guarantorName || ''];
    case 'asset':
      return [
        row.productType || '',
        row.assetDescription || '',
        row.registrationOrLocation || '',
        row.ownerNumber || '',
        row.vehicleTypeCode || row.usage || '',
      ];
    case 'outstanding':
      return [
        formatMoney(row.contractValue),
        formatMoney(row.principalOutstanding),
        formatMoney(row.interestOutstanding),
        formatMoney(row.totalOutstanding),
      ];
    case 'overdue':
      return [
        firstValue(row, ['overdueInstallmentCount', 'noOfOverdues', 'numberOfOverdues', 'overdue_count']),
        formatMoney(firstValue(row, ['overdueAmount', 'odAmount', 'overdue_amount'])),
        formatDate(firstValue(row, ['overdueFromDate', 'fromDate', 'overdue_from_date'])),
        formatDate(firstValue(row, ['overdueEndDate', 'endDate', 'overdue_end_date'])),
      ];
    case 'currentDue':
      return [formatMoney(row.currentDueAmount)];
    case 'currentDueDate':
      return [formatDate(row.currentDueDate)];
    default:
      return [''];
  }
}

function alignClass(align) {
  if (align === 'right') return 'text-right';
  if (align === 'center') return 'text-center';
  return 'text-left';
}

function headerAlignClass(align) {
  return align === 'right' ? 'text-right' : 'text-left';
}

function StackCell({ values, align = 'left' }) {
  return (
    <div className={`space-y-0.5 leading-4 ${alignClass(align)}`}>
      {values.map((value, index) => (
        <div key={index} className="min-h-4 whitespace-normal">
          {value}
        </div>
      ))}
    </div>
  );
}

export default function DemandListGrid({ rows, page, pageSize, sortColumn, sortDirection, onSort, onOpen }) {
  return (
    <div className="no-print min-h-0 flex-1 overflow-auto bg-white">
      <table className="min-w-[1250px] border-collapse text-[12px] text-black">
        <thead className="sticky top-0 z-20 bg-[#e8edf5] text-[12px] font-bold">
          <tr>
            {groupedColumns.map((column) => (
              <th key={column.key} className={`${column.width} border border-black/40 px-2 py-1 align-top ${headerAlignClass(column.align)}`}>
                <button type="button" onClick={() => onSort(column.sortField)} className={`w-full text-inherit ${headerAlignClass(column.align)}`}>
                  {column.label.map((line) => <div key={line}>{line}</div>)}
                  {sortColumn === column.sortField ? <div>{sortDirection === 'asc' ? 'ASC' : 'DESC'}</div> : null}
                </button>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => {
            const displayRow = { ...row, serialNumber: page * pageSize + index + 1 };
            return (
              <tr key={row.contractId} onDoubleClick={() => onOpen(row)} className={`${index % 2 === 0 ? 'bg-white' : 'bg-slate-50'} hover:bg-blue-50`}>
                {groupedColumns.map((column) => (
                  <td key={column.key} className={`border border-black/30 px-2 py-1 align-top ${alignClass(column.align)}`}>
                    <StackCell values={lineValues(displayRow, column.key)} align={column.align || 'left'} />
                  </td>
                ))}
              </tr>
            );
          })}
          {rows.length === 0 && (
            <tr>
              <td colSpan={groupedColumns.length} className="border border-black/30 px-3 py-6 text-center font-bold uppercase text-black/50">No records found</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export { groupedColumns as demandListColumns, formatDate, formatMoney, lineValues };
