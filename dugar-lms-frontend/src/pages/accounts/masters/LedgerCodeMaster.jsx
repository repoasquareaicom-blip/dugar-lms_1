import React, { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, Edit3, FilePlus2, Loader2, Save, TriangleAlert, X } from 'lucide-react';
import ServerDataTable from '../../../components/common/ServerDataTable';
import {
  createLedgerCode,
  fetchLedgerCodes,
  updateLedgerCode,
} from '../../../services/ledgerCodeService';

const PAGE_SIZE_OPTIONS = [25, 50, 100, 250];
const DEFAULT_FILTERS = {};
const DEFAULT_HIDDEN_COLUMNS = {};
const emptyForm = {
  ledgerCode: '',
  ledgerName: '',
  openingBalance: '',
  isActive: true,
};

function display(value) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function money(value) {
  const amount = Number(value || 0);
  return amount.toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function transformLedgersResponse(data) {
  const rows = Array.isArray(data?.content) ? data.content : [];
  return {
    rows,
    totalElements: Number(data?.totalElements || rows.length),
  };
}

function saveErrorMessage(error, isCreate) {
  const message = error?.response?.data?.message || error?.response?.data?.error || error?.message || '';
  if (/duplicate|already exists|unique|uq_ledger_codes_code|403/i.test(message)) {
    return 'Ledger code already exists!';
  }
  if (error?.response?.status === 409 || (isCreate && error?.response?.status === 403)) {
    return 'Ledger code already exists!';
  }
  return message || 'Unable to save ledger code.';
}

const LedgerCodeMaster = () => {
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [notice, setNotice] = useState(null);
  const [noticeVisible, setNoticeVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editLoading, setEditLoading] = useState(false);
  const [gridVersion, setGridVersion] = useState(0);

  const reloadGrid = () => setGridVersion((value) => value + 1);

  useEffect(() => {
    if (!notice) return undefined;

    setNoticeVisible(true);
    const hideTimer = window.setTimeout(() => setNoticeVisible(false), 3600);
    const clearTimer = window.setTimeout(() => setNotice(null), 4000);

    return () => {
      window.clearTimeout(hideTimer);
      window.clearTimeout(clearTimer);
    };
  }, [notice]);

  const showNotice = (text, type = 'success') => {
    setNotice({ text, type });
  };

  const openAdd = () => {
    setEditing({});
    setForm(emptyForm);
    setNotice(null);
  };

  const openEdit = (record) => {
    setEditing(record);
    setForm({
      ledgerCode: record.ledgerCode || '',
      ledgerName: record.ledgerName || '',
      openingBalance: record.openingBalance ?? '',
      isActive: record.isActive !== false,
    });
    setNotice(null);
  };

  const beginEdit = (record) => {
    setEditLoading(true);
    window.setTimeout(() => {
      openEdit(record);
      setEditLoading(false);
    }, 180);
  };

  const closeModal = () => {
    setEditing(null);
    setForm(emptyForm);
  };

  const setField = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const save = async (event) => {
    event.preventDefault();
    if (saving) return;

    const payload = {
      ledgerCode: form.ledgerCode.trim(),
      ledgerName: form.ledgerName.trim(),
      openingBalance: form.openingBalance === '' ? 0 : Number(form.openingBalance),
      isActive: form.isActive,
    };

    try {
      setSaving(true);
      if (editing?.ledgerId) {
        await updateLedgerCode(editing.ledgerCode, payload);
        showNotice('Ledger code updated successfully.');
      } else {
        await createLedgerCode(payload);
        showNotice('Ledger code created successfully.');
      }
      closeModal();
      reloadGrid();
    } catch (error) {
      showNotice(saveErrorMessage(error, !editing?.ledgerId), 'error');
    } finally {
      setSaving(false);
    }
  };

  const filterFields = useMemo(() => [], []);

  const columns = useMemo(
    () => [
      { field: 'serialNumber', headerName: 'S.No', minWidth: 70, align: 'center', headerAlign: 'center' },
      { field: 'ledgerCode', headerName: 'Ledger Code', minWidth: 120, sortable: true },
      { field: 'ledgerName', headerName: 'Ledger Name', minWidth: 260, sortable: true },
      { field: 'openingBalance', headerName: 'Opening Balance', minWidth: 150, align: 'right', headerAlign: 'right', formatter: money },
    ],
    [],
  );

  const contextMenuItems = (row) => [
    {
      label: 'Edit Ledger',
      icon: <Edit3 size={14} />,
      onClick: () => beginEdit(row),
    },
  ];

  return (
    <div className="relative h-full min-h-0 bg-white" style={{ fontFamily: 'Calibri, sans-serif' }}>
      {notice && (
        <div className={`fixed inset-0 z-[300] grid place-items-center bg-black/35 p-4 backdrop-blur-[1px] transition-opacity duration-300 ease-out ${noticeVisible ? 'opacity-100' : 'pointer-events-none opacity-0'}`}>
          <div className={`w-full max-w-md transform overflow-hidden rounded-lg border-2 bg-white shadow-2xl transition-all duration-300 ease-out ${noticeVisible ? 'scale-100 translate-y-0' : 'scale-95 translate-y-2'} ${notice.type === 'error' ? 'border-red-200' : 'border-emerald-200'}`}>
            <div className={`flex items-center justify-between border-b px-4 py-3 ${notice.type === 'error' ? 'border-red-100 bg-red-50 text-red-900' : 'border-emerald-100 bg-emerald-50 text-emerald-900'}`}>
              <div className="flex items-center gap-3">
                <div className={`grid h-9 w-9 place-items-center rounded-full bg-white ${notice.type === 'error' ? 'text-red-700' : 'text-emerald-700'}`}>
                  {notice.type === 'error' ? <TriangleAlert size={19} /> : <CheckCircle2 size={19} />}
                </div>
                <div className="text-[13px] font-black uppercase tracking-widest">
                  {notice.type === 'error' ? 'Save Failed' : 'Saved'}
                </div>
              </div>
              <button type="button" onClick={() => setNotice(null)} className="grid h-8 w-8 place-items-center rounded hover:bg-white/70">
                <X size={16} />
              </button>
            </div>

            <div className="px-5 py-5">
              <div className="text-center text-[15px] font-bold leading-relaxed text-slate-800">
                {notice.text}
              </div>
            </div>

            <div className="flex justify-center gap-2 border-t border-slate-200 bg-slate-50 px-5 py-3">
              <button type="button" onClick={() => setNotice(null)} className="rounded-sm border border-slate-300 bg-white px-5 py-2 text-[13px] font-black uppercase text-slate-700 hover:bg-slate-100">
                Close
              </button>
              <button type="button" onClick={() => setNotice(null)} className={`rounded-sm px-6 py-2 text-[13px] font-black uppercase text-white ${notice.type === 'error' ? 'bg-red-700 hover:bg-red-800' : 'bg-emerald-700 hover:bg-emerald-800'}`}>
                OK
              </button>
            </div>
          </div>
        </div>
      )}

      {editLoading && (
        <div className="fixed inset-0 z-[250] grid place-items-center bg-white/40 backdrop-blur-[1px]">
          <div className="flex items-center gap-3 rounded-lg border-2 border-blue-100 bg-white px-5 py-4 text-[#0052CC] shadow-2xl">
            <Loader2 size={20} className="animate-spin" />
            <span className="text-[12px] font-black uppercase tracking-widest">Opening ledger</span>
          </div>
        </div>
      )}

      <ServerDataTable
        key={gridVersion}
        columns={columns}
        defaultFilters={DEFAULT_FILTERS}
        defaultHiddenColumns={DEFAULT_HIDDEN_COLUMNS}
        defaultPageSize={25}
        defaultSortColumn="ledgerCode"
        defaultSortDirection="asc"
        fetchPage={fetchLedgerCodes}
        filterFields={filterFields}
        getContextMenuItems={contextMenuItems}
        getRowId={(row) => row.ledgerId}
        loadingLabel="Loading ledger codes"
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        searchPlaceholder="Quick search ledger code or name..."
        title="Ledger Code"
        titleAction={(
          <button
            type="button"
            onClick={openAdd}
            className="inline-flex items-center gap-2 rounded-lg border-2 border-[#0052CC] bg-[#0052CC] px-3 py-1.5 text-[11px] font-black uppercase text-white shadow-sm transition-all hover:bg-blue-700"
          >
            <FilePlus2 size={14} strokeWidth={3} />
            Add Ledger
          </button>
        )}
        transformResponse={transformLedgersResponse}
        onRowDoubleClick={beginEdit}
      />

      {editing !== null && (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/50 p-4">
          <form onSubmit={save} className="w-full max-w-2xl overflow-hidden rounded-sm border-2 border-black/30 bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-black/20 bg-slate-50 px-5 py-3">
              <h2 className="text-[18px] font-black uppercase text-slate-950">
                {editing?.ledgerId ? 'Edit Ledger Code' : 'Add Ledger Code'}
              </h2>
              <button type="button" onClick={closeModal} disabled={saving} className="inline-flex h-8 w-8 items-center justify-center rounded-sm hover:bg-slate-200 disabled:cursor-not-allowed disabled:opacity-50">
                <X size={18} />
              </button>
            </div>

            <div className="grid grid-cols-1 gap-4 p-5 md:grid-cols-4">
              <Field label="Ledger Code" value={form.ledgerCode} onChange={(value) => setField('ledgerCode', value)} required />
              <Field label="Ledger Name" value={form.ledgerName} onChange={(value) => setField('ledgerName', value)} required className="md:col-span-3" />
              <Field label="Opening Balance" type="number" value={form.openingBalance} onChange={(value) => setField('openingBalance', value)} className="md:col-span-2" step="0.01" />
            </div>

            <div className="flex justify-end gap-2 border-t border-black/20 bg-slate-50 px-5 py-3">
              <button type="button" onClick={closeModal} disabled={saving} className="rounded-sm border border-slate-300 bg-white px-4 py-2 text-[13px] font-black uppercase text-slate-700 disabled:cursor-not-allowed disabled:opacity-50">Cancel</button>
              <button type="submit" disabled={saving} className="inline-flex min-w-24 items-center justify-center gap-2 rounded-sm bg-blue-800 px-5 py-2 text-[13px] font-black uppercase text-white disabled:cursor-wait disabled:bg-blue-500">
                {saving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
                {saving ? 'Saving' : 'Save'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

const Field = ({ label, value, onChange, required = false, type = 'text', className = '', step }) => (
  <label className={`flex flex-col gap-1 ${className}`}>
    <span className="text-[11px] font-black uppercase tracking-widest text-slate-500">{label}</span>
    <input
      type={type}
      step={step}
      value={display(value) === '-' ? '' : value}
      required={required}
      onChange={(event) => onChange(event.target.value)}
      className="h-10 border border-slate-300 bg-white px-3 text-[14px] font-bold uppercase outline-none focus:border-blue-700"
    />
  </label>
);

export default LedgerCodeMaster;
