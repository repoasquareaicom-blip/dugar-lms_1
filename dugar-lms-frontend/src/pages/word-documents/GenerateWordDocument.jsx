import api, { getApiBaseUrl, getStoredAuthToken } from '../../api/apiClient';
import { useCallback, useEffect, useRef, useState } from 'react';
import ContractGrid from '../Credit/transaction/contract/ContractGrid';
import ReminderLetterGrid from './ReminderLetterGrid';
import './GenerateWordDocument.css';
import { DOCUMENT_TEMPLATES_CHANGED, fetchWordTemplates, downloadWordTemplate } from '../../services/wordTemplateService';

const message = (error) => error?.response?.data?.message || error?.message || 'Document request failed.';
const isReminderTemplate = (template) => [template?.documentKey, template?.templateName].some((name) => String(name || '').trim().replaceAll('_', ' ').toLowerCase() === 'reminder letter');
const busyStatus = (job) => job && ['QUEUED', 'RUNNING'].includes(job.status);
function criteria(params) {
  return { ...params.filters, keyword: params.keyword, sortColumn: params.sortColumn, sortDirection: params.sortDirection, isDraft: false, page: 0, size: 250 };
}
function filterKey(params) { return JSON.stringify([params.keyword || '', params.filters || {}]); }

export default function GenerateWordDocument() {
  const [templates, setTemplates] = useState([]), [templateId, setTemplateId] = useState('');
  const [format, setFormat] = useState('DOCX'), [catalogueLoading, setCatalogueLoading] = useState(false);
  const [selected, setSelected] = useState([]), [allMatching, setAllMatching] = useState(false);
  const [query, setQuery] = useState({ ready: false, total: 0, criteria: null });
  const [job, setJob] = useState(null), [starting, setStarting] = useState(false), [notice, setNotice] = useState('');
  const [viewUrl, setViewUrl] = useState(''), [viewing, setViewing] = useState(false);
  const [errorPage, setErrorPage] = useState(0);
  const [asOnDate, setAsOnDate] = useState(() => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()));
  const [pendingOnly, setPendingOnly] = useState(true);
  const catalogueVersion = useRef(0), selectionQuery = useRef('');
  const resultVersion = useRef(0);
  const clearResult = () => {
    resultVersion.current += 1;
    setJob(null); setNotice(''); setErrorPage(0);
    try { window.sessionStorage?.removeItem('document-export-job'); } catch { /* Storage may be disabled. */ }
  };
  const document = templates.find((t) => String(t.id) === templateId);
  const reminder = isReminderTemplate(document);
  const SelectionGrid = reminder ? ReminderLetterGrid : ContractGrid;
  const count = allMatching ? query.total : selected.length;
  const working = starting || busyStatus(job);
  const loadTemplates = useCallback(async () => {
    const request = ++catalogueVersion.current; setCatalogueLoading(true);
    try {
      const data = await fetchWordTemplates({ isActive: true });
      if (request !== catalogueVersion.current) return;
      const rows = (Array.isArray(data) ? data : []).filter((t) => t.isActive !== false && t.hasFile !== false && ['en', 'hi'].includes(t.languageCode || 'en'));
      setTemplates(rows); setTemplateId((id) => rows.some((t) => String(t.id) === id) ? id : String(rows[0]?.id || ''));
    } catch (error) { setNotice(message(error)); }
    finally { if (request === catalogueVersion.current) setCatalogueLoading(false); }
  }, []);
  useEffect(() => {
    loadTemplates();
    const onStorage = (event) => { if (event.key === DOCUMENT_TEMPLATES_CHANGED) loadTemplates(); };
    window.addEventListener('focus', loadTemplates); window.addEventListener(DOCUMENT_TEMPLATES_CHANGED, loadTemplates); window.addEventListener('storage', onStorage);
    return () => { window.removeEventListener('focus', loadTemplates); window.removeEventListener(DOCUMENT_TEMPLATES_CHANGED, loadTemplates); window.removeEventListener('storage', onStorage); };
  }, [loadTemplates]);
  useEffect(() => {
    let id;
    try { id = window.sessionStorage?.getItem('document-export-job'); } catch { /* Storage may be disabled. */ }
    const version = resultVersion.current;
    if (id) api.get(`/document-exports/${id}`).then(({ data }) => { if (version === resultVersion.current) setJob(data); }).catch(() => {});
  }, []);
  useEffect(() => () => { if (viewUrl) URL.revokeObjectURL(viewUrl); }, [viewUrl]);
  const exportId = job?.id, exportStatus = job?.status;
  useEffect(() => {
    if (!exportId || !['QUEUED', 'RUNNING'].includes(exportStatus)) return;
    let cancelled = false, timer;
    const version = resultVersion.current;
    const poll = async () => {
      if (cancelled || version !== resultVersion.current) return;
      try {
        const { data } = await api.get(`/document-exports/${exportId}`, { params: { errorPage } });
        if (!cancelled && version === resultVersion.current) { setJob(data); if (busyStatus(data)) timer = window.setTimeout(poll, 1500); }
      } catch (error) { if (!cancelled && version === resultVersion.current) { setNotice(message(error)); timer = window.setTimeout(poll, 4000); } }
    };
    timer = window.setTimeout(poll, 500);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [exportId, exportStatus, errorPage]);
  const onQueryChange = useCallback((params) => {
    const key = filterKey(params);
    if (selectionQuery.current !== key) { selectionQuery.current = key; setSelected([]); setAllMatching(false); }
    setQuery((current) => ({ ...current, ready: false }));
  }, []);
  const onResults = useCallback((params, total) => setQuery({ ready: true, total, criteria: criteria(params), reminderSnapshotId: params.reminderSnapshotId, search: params.keyword }), []);
  const resetSelection = () => { setSelected([]); setAllMatching(false); setQuery((q) => ({ ...q, ready: false })); };
  const generate = async () => {
    if (!document || !count || !query.ready || working) return;
    if (count > 100 && !window.confirm(`Generate ${count.toLocaleString()} documents? This bulk export may take several minutes.`)) return;
    clearResult(); setStarting(true);
    try {
      const { data } = await api.post('/document-exports', { templateId: document.id, outputFormat: format, allMatching, contractIds: allMatching ? [] : selected, criteria: query.criteria, confirmLarge: count > 100,
        ...(reminder ? { asOnDate, pendingOnly, reminderSnapshotId: query.reminderSnapshotId, search: query.search || '' } : {}),
      });
      setJob(data);
      try { window.sessionStorage?.setItem('document-export-job', data.id); } catch { /* Storage may be disabled. */ }
    } catch (error) { setNotice(message(error)); }
    finally { setStarting(false); }
  };
  const viewOriginal = async () => {
    setViewing(true); setNotice('');
    try { const { data } = await api.get(`/word-templates/${templateId}/preview`, { responseType: 'blob' }); setViewUrl(URL.createObjectURL(data)); }
    catch (error) { setNotice(message(error)); }
    finally { setViewing(false); }
  };
  const download = async () => {
    const version = resultVersion.current;
    try {
      // Stream large ZIPs directly to disk where supported, instead of buffering them in browser memory.
      if (window.showSaveFilePicker) {
        const handle = await window.showSaveFilePicker({ suggestedName: job.artifact_name });
        const response = await fetch(`${getApiBaseUrl()}/document-exports/${job.id}/download`, { headers: { Authorization: `Bearer ${getStoredAuthToken()}` } });
        if (!response.ok) { const error = await response.json(); throw new Error(error.message || 'Download failed'); }
        await response.body.pipeTo(await handle.createWritable());
      } else {
        if (Number(job.artifact_size) > 64 * 1024 * 1024) throw new Error('For large exports, use Chrome or Edge over HTTPS with Save File support.');
        const { data } = await api.get(`/document-exports/${job.id}/download`, { responseType: 'blob' });
        const url = URL.createObjectURL(data), link = window.document.createElement('a'); link.href = url; link.download = job.artifact_name; link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 60000);
      }
    } catch (error) { if (version === resultVersion.current && error.name !== 'AbortError') setNotice(message(error)); }
  };
  const errors = async (page) => {
    const version = resultVersion.current;
    try { const { data } = await api.get(`/document-exports/${job.id}`, { params: { errorPage: page } }); if (version === resultVersion.current) { setJob(data); setErrorPage(page); } }
    catch (error) { if (version === resultVersion.current) setNotice(message(error)); }
  };
  return <div className="generate-document flex h-full min-h-0 flex-col bg-white text-sm">
    <div className="gd-header">
      <div className="gd-heading"><h1 className="font-bold text-base text-blue-700">Generate Document</h1>
        <p role="status" className="gd-selection"><strong>{count.toLocaleString()} selected</strong><span> &middot; {allMatching ? 'Select All Matching across every result page' : 'Individual selected rows'}</span></p>
      </div>
      {notice && <p role="alert" className="text-red-700">{notice}</p>}
      <div className="gd-toolbar">
        <label className="gd-field gd-document">Document<select aria-label="Document" className="gd-control" value={templateId} disabled={working || catalogueLoading} onChange={(event) => { const next = event.target.value; if (reminder !== isReminderTemplate(templates.find((t) => String(t.id) === next))) resetSelection(); setTemplateId(next); }}>
          {!templates.length && <option value="">No active documents</option>}
          {templates.map((t) => <option key={t.id} value={t.id}>{t.templateName} ({t.languageCode === 'hi' ? 'Hindi' : 'English'})</option>)}
        </select></label>
        <button type="button" disabled={catalogueLoading || working} onClick={loadTemplates} className="gd-button">Refresh Templates</button>
        <button type="button" disabled={!document} onClick={() => downloadWordTemplate(document.id, document.originalFilename).catch((error) => setNotice(message(error)))} className="gd-button">Download Template</button>
        <fieldset className="gd-output"><legend className="sr-only">Output Type</legend><span aria-hidden="true" className="font-medium text-slate-600">Output Type</span>{[['DOCX', 'Word (.docx)'], ['PDF', 'PDF (.pdf)']].map(([value, label]) => <label key={value}><input type="radio" name="outputType" checked={format === value} disabled={working} onChange={() => setFormat(value)} /> {label}</label>)}</fieldset>
        <button type="button" disabled={!document || !count || !query.ready || catalogueLoading || working} onClick={generate} className="gd-button gd-generate bg-blue-700 font-bold text-white disabled:opacity-50">{working ? 'Generating...' : 'Generate'}</button>
      </div>
      {job && <div className="space-y-2 border bg-slate-50 p-3" aria-live="polite">
        <button type="button" onClick={clearResult} className="border px-3 py-2">Clear Result</button>
        <p>Export {job.id}: {job.status} ? {Number(job.success_count) + Number(job.failure_count)} / {job.total_count} processed; {job.success_count} succeeded, {job.failure_count} failed.</p>
        <progress value={Number(job.success_count) + Number(job.failure_count)} max={Number(job.total_count) || 1} className="w-full" />
        {job.failure_reason && <p className="text-red-700">{job.failure_reason}</p>}
        {['COMPLETED', 'COMPLETED_WITH_ERRORS'].includes(job.status) && <button type="button" onClick={download} className="border px-3 py-2">{job.status === 'COMPLETED_WITH_ERRORS' ? 'Download successful documents + errors.csv' : 'Download generated documents'}</button>}
        {Number(job.failure_count) > 0 && <div><p className="font-bold text-red-700">Failed contracts are listed below. Failed documents are never silently included or omitted.</p><table className="w-full"><thead><tr><th>Contract</th><th>Error</th></tr></thead><tbody>{job.errors?.map((e) => <tr key={e.contract_id}><td>{e.contract_number || e.contract_id}</td><td>{e.error_message}</td></tr>)}</tbody></table><button type="button" disabled={errorPage === 0} onClick={() => errors(errorPage - 1)}>Previous errors</button> <button type="button" disabled={(errorPage + 1) * 100 >= Number(job.failure_count)} onClick={() => errors(errorPage + 1)}>Next errors</button></div>}
      </div>}
    </div>
    <div className="min-h-0 flex-1"><SelectionGrid asOnDate={asOnDate} pendingOnly={pendingOnly}
      renderControls={(refreshButton) => <div className="gd-filters">
        <label className="gd-field">As-on Date<input aria-label="As-on Date" type="date" required value={asOnDate} disabled={working} onChange={(e) => { setAsOnDate(e.target.value); resetSelection(); }} className="gd-control" /></label>
        <label className="gd-field">Contracts<select aria-label="Reminder contract filter" value={pendingOnly ? 'pending' : 'all'} disabled={working} onChange={(e) => { setPendingOnly(e.target.value === 'pending'); resetSelection(); }} className="gd-control"><option value="pending">Pending Due Loans</option><option value="all">All Contracts</option></select></label>
        {refreshButton}
      </div>}
      title="Select Contracts" showCreate={false} viewOnly documentSelection={{ disabled: working || allMatching, onQueryChange, onResults,
      isSelected: (row) => allMatching || selected.includes(row.contractId),
      onToggle: (row) => setSelected((ids) => ids.includes(row.contractId) ? ids.filter((id) => id !== row.contractId) : [...ids, row.contractId]),
      renderActions: () => <div className="flex flex-wrap gap-2"><button type="button" className="gd-button" disabled={working || !query.ready || !query.total} onClick={() => { setAllMatching(true); setSelected([]); }}>Select All Matching ({query.total.toLocaleString()})</button><button type="button" className="gd-button" disabled={working} onClick={() => { setSelected([]); setAllMatching(false); }}>Clear Selection</button></div>,
    }} /></div>
    {viewUrl && <div role="dialog" aria-label="Original template preview" className="fixed inset-4 z-50 flex flex-col border bg-white shadow-xl"><div className="flex justify-between p-2"><span>Original template ? placeholders are unchanged</span><button type="button" onClick={() => setViewUrl('')}>Close preview</button></div><iframe title="Original uploaded template" src={viewUrl} className="min-h-0 flex-1" /></div>}
  </div>;
}
