import { formatDate, lineValues, visibleDemandListColumns } from './DemandListGrid';

function PrintStack({ values }) {
  return (
    <>
      {values.map((value, index) => (
        <div key={index} className="demand-print-line">
          {value}
        </div>
      ))}
    </>
  );
}

export default function DemandListPrintView({ data, filters }) {
  const rows = data?.rows?.content || [];
  const columns = visibleDemandListColumns({ showArea: !filters?.areaCode?.trim() });

  return (
    <div className="demand-print-root">
      <h1 className="demand-print-title">Demand List as on {formatDate(filters.asOnDate)}</h1>
      <table className="demand-print-table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} className={column.align === 'right' ? 'num' : column.align === 'center' ? 'center' : ''}>
                {column.label.map((line) => <div key={line}>{line}</div>)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => {
            const displayRow = { ...row, serialNumber: index + 1 };
            return (
              <tr key={row.contractId}>
                {columns.map((column) => (
                  <td key={column.key} className={column.align === 'right' ? 'num' : column.align === 'center' ? 'center' : ''}>
                    <PrintStack values={lineValues(displayRow, column.key)} />
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
