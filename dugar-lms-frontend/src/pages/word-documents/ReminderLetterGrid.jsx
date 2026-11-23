import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../../api/apiClient';
import ServerDataTable from '../../components/common/ServerDataTable';

const transformResponse = (data) => ({ rows: data.content || [], totalElements: Number(data.totalElements || 0) });
const rowId = (row) => row.contractId;
export default function ReminderLetterGrid({ asOnDate, pendingOnly, documentSelection, renderControls }) {
  const [calculation, setCalculation] = useState(null), [refresh, setRefresh] = useState(0);
  const calculationKey = `${asOnDate}:${refresh}`;
  const snapshot = calculation?.key === calculationKey ? calculation.id : null;
  const error = calculation?.key === calculationKey ? calculation.error || '' : '';
  const calculating = Boolean(asOnDate && !snapshot && !error);
  const refreshRequested = useRef(false);
  const refreshCalculations = () => {
    if (documentSelection.disabled || calculating || refreshRequested.current || !asOnDate) return;
    refreshRequested.current = true;
    selection.current.onQueryChange({ filters: { asOnDate, refresh: refresh + 1 } });
    setRefresh((n) => n + 1);
  };
  const selection = useRef(documentSelection), requestVersion = useRef(0);
  useEffect(() => { selection.current = documentSelection; }, [documentSelection]);
  useEffect(() => {
    let cancelled = false;
    selection.current.onQueryChange({ filters: { asOnDate, refresh } });
    if (asOnDate) api.post('/document-exports/reminder/snapshots', { asOnDate })
      .then(({ data }) => { if (!cancelled) setCalculation({ key: `${asOnDate}:${refresh}`, id: data.id }); })
      .catch((e) => { if (!cancelled) setCalculation({ key: `${asOnDate}:${refresh}`, error: e.response?.data?.message || 'Unable to calculate Reminder Letter values.' }); });
    refreshRequested.current = false;
    return () => { cancelled = true; requestVersion.current += 1; };
  }, [asOnDate, refresh]);
  const fetchPage = useCallback(async (params) => {
    const request = ++requestVersion.current;
    const query = { ...params, reminderSnapshotId: snapshot, filters: { asOnDate, pendingOnly, reminderSnapshotId: snapshot } };
    selection.current.onQueryChange(query);
    try {
      const { data } = await api.get(`/document-exports/reminder/snapshots/${snapshot}`, {
        params: { asOnDate, pendingOnly, search: params.keyword, page: params.page, size: params.pageSize },
      });
      if (request === requestVersion.current) selection.current.onResults(query, Number(data.totalElements || 0));
      return data;
    } catch (e) {
      if (request === requestVersion.current) setCalculation({ key: `${asOnDate}:${refresh}`, id: snapshot, error: e.response?.data?.message || 'Unable to load Reminder Letter values.' });
      throw e;
    }
  }, [asOnDate, pendingOnly, refresh, snapshot]);
  const columns = [
    { field: 'selected', headerName: 'Select', minWidth: 65, renderCell: (row) => <input type="checkbox" aria-label={`Select ${row.contractNumber}`} checked={documentSelection.isSelected(row)} disabled={documentSelection.disabled} onChange={() => documentSelection.onToggle(row)} /> },
    { field: 'contractNumber', headerName: 'Loan No.' }, { field: 'borrowerName', headerName: 'Borrower Name' },
    { field: 'emi', headerName: 'EMI' }, { field: 'currentDue', headerName: 'Monthly Part Amount Due' },
    { field: 'noOfOverdues', headerName: 'No. of Overdues' }, { field: 'totalOs', headerName: 'Total Overdue EMI Amount' },
  ];
  const refreshButton = <button type="button" disabled={documentSelection.disabled || calculating || !asOnDate} onClick={refreshCalculations} className="gd-button">Refresh Calculations</button>;
  return <div className="flex h-full min-h-0 flex-col">
    {renderControls ? renderControls(refreshButton) : <div className="gd-filters">{refreshButton}</div>}
    <div className="flex min-h-0 flex-1 flex-col" aria-busy={calculating}>
      {error && <div role="alert" className={snapshot ? 'gd-error' : 'gd-feedback'}>
        <p className="text-red-700">{error}</p>
        <button type="button" className="gd-button" disabled={documentSelection.disabled || !asOnDate} onClick={refreshCalculations}>Retry calculations</button>
      </div>}
      {snapshot ? <ServerDataTable key={`${snapshot}:${pendingOnly}`} columns={columns} fetchPage={fetchPage} transformResponse={transformResponse}
        getRowId={rowId} isRowSelected={documentSelection.isSelected} title="Reminder Letter - Select Contracts"
        titleAction={documentSelection.renderActions} searchPlaceholder="Search by Loan Number or Borrower Name" />
        : !error && <div role="status" aria-live="polite" className="gd-feedback">
          {calculating ? <>
            <span aria-hidden="true" className="gd-spinner" />
            <p className="font-medium text-slate-700">Calculating Reminder Letter values...</p>
            <p className="text-xs text-slate-500">Loading loan details and financial calculations</p>
          </> : <p className="text-slate-600">Select an As-on Date.</p>}
        </div>}
    </div>
  </div>;
}
