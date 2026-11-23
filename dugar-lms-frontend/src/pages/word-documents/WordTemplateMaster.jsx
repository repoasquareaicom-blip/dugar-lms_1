import apiClient from '../../api/apiClient';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, Copy, Loader2, RefreshCw, Save, Search, Upload, X } from 'lucide-react';
import {
  fetchWordTemplates,
  fetchWordPlaceholders,
  replaceWordTemplateFile,
  setWordTemplateActive,
} from '../../services/wordTemplateService';

function cleanMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback;
}

function Notice({ notice, onClose }) {
  if (!notice) return null;
  const ok = notice.type !== 'error';
  return (
    <div className="fixed inset-0 z-[300] grid place-items-center bg-black/35 p-4">
      <div className={`w-full max-w-md border-2 bg-white shadow-2xl ${ok ? 'border-emerald-200' : 'border-red-200'}`}>
        <div className={`flex items-center justify-between border-b px-4 py-3 ${ok ? 'bg-emerald-50 text-emerald-900' : 'bg-red-50 text-red-900'}`}>
          <div className="flex items-center gap-2 text-[13px] font-black uppercase">
            <CheckCircle2 size={18} />
            {ok ? 'Success' : 'Error'}
          </div>
          <button type="button" onClick={onClose} className="grid h-8 w-8 place-items-center hover:bg-white/60">
            <X size={16} />
          </button>
        </div>
        <div className="px-5 py-5 text-center text-[14px] font-bold text-black">{notice.text}</div>
      </div>
    </div>
  );
}

function Modal({ title, children, onClose }) {
  return (
    <div className="fixed inset-0 z-[250] flex items-center justify-center bg-black/35 p-4" onMouseDown={onClose}>
      <div className="w-full max-w-xl border border-black/20 bg-white shadow-2xl" onMouseDown={(event) => event.stopPropagation()}>
        <div className="flex items-center justify-between border-b border-black/20 bg-blue-50 px-4 py-3">
          <div className="text-[13px] font-black uppercase text-[#0052CC]">{title}</div>
          <button type="button" onClick={onClose} className="grid h-8 w-8 place-items-center hover:bg-white">
            <X size={16} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

export default function WordTemplateMaster() {
  const [templates, setTemplates] = useState([]);
  const [filters, setFilters] = useState({ keyword: '' });
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState(null);
  const [templateModal, setTemplateModal] = useState(null);
  const [aliasDrafts, setAliasDrafts] = useState({});
  const [aliasSaving, setAliasSaving] = useState({});
  const [aliasMessages, setAliasMessages] = useState({});
  const [fieldMetadata, setFieldMetadata] = useState([]);
  const [fields, setFields] = useState([]);
  const [fieldKeyword, setFieldKeyword] = useState('');
  const [contractNumber, setContractNumber] = useState('');
  const [previewContract, setPreviewContract] = useState('');
  const [previewValues, setPreviewValues] = useState({});
  const [previewing, setPreviewing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const listRequest = useRef(0);

  const filteredFields = useMemo(() => {
    const keyword = fieldKeyword.trim().toLowerCase();
    return fields.filter((field) => !keyword || [field.category, field.fieldName, field.placeholderCode,
      previewValues[field.placeholderCode], ...(fieldMetadata.find((m) => m.canonicalCode === field.placeholderCode)?.aliases || []).map((a) => a.alias)]
      .some((value) => String(value ?? '').toLowerCase().includes(keyword)));
  }, [fieldKeyword, fields, fieldMetadata, previewValues]);

  const loadTemplates = useCallback(async () => {
    const request = ++listRequest.current;
    setLoading(true);
    try {
      const data = await fetchWordTemplates({ keyword: filters.keyword || undefined });
      if (request === listRequest.current) setTemplates(Array.isArray(data) ? data : []);
    } catch (error) {
      if (request === listRequest.current) setNotice({ type: 'error', text: cleanMessage(error, 'Unable to load templates.') });
    } finally { if (request === listRequest.current) setLoading(false); }
  }, [filters.keyword]);

  const loadFields = useCallback(async () => {
    const [fieldData, metadata] = await Promise.all([
      fetchWordPlaceholders({ dataSource: 'CONTRACT' }), apiClient.get('/document-automation/fields'),
    ]);
    setFields(Array.isArray(fieldData) ? fieldData : []);
    setFieldMetadata(Array.isArray(metadata.data) ? metadata.data : []);
  }, []);
  useEffect(() => { loadTemplates(); }, [loadTemplates]);
  useEffect(() => { loadFields().catch((error) => setNotice({ type: 'error', text: cleanMessage(error, 'Unable to load fields.') })); }, [loadFields]);

  const previewContractFields = async (event) => {
    event.preventDefault();
    if (previewing) return;
    setPreviewing(true);
    try {
      const { data } = await apiClient.get('/document-automation/contract-fields', { params: { contractNumber: contractNumber.trim() } });
      setPreviewValues(data.values || {});
      setPreviewContract(data.contractNumber);
    } catch (error) { setNotice({ type: 'error', text: cleanMessage(error, 'Unable to preview contract values.') }); }
    finally { setPreviewing(false); }
  };

  const saveAlias = async (field, alias) => {
    const key = alias.id ?? field.placeholderCode;
    const value = (aliasDrafts[key] ?? alias.alias ?? '').trim().replace(/^\{\{(.*)}}$/, '$1').trim();
    if (aliasSaving[key]) return;
    setAliasSaving((current) => ({ ...current, [key]: true }));
    setAliasMessages((current) => ({ ...current, [key]: '' }));
    try {
      if (!value) {
        if (alias.id != null) await apiClient.delete(`/document-automation/aliases/${alias.id}`);
      } else {
        await apiClient.post('/document-automation/aliases', { id: alias.id ?? null, alias: value, canonicalCode: field.placeholderCode });
      }
      await loadFields();
      setAliasDrafts((current) => { const next = { ...current }; delete next[key]; return next; });
    } catch (error) {
      setAliasMessages((current) => ({ ...current, [key]: cleanMessage(error, 'Unable to save alias.') }));
    } finally { setAliasSaving((current) => ({ ...current, [key]: false })); }
  };

  const submitTemplate = async (event) => {
    event.preventDefault();
    if (saving) return;
    setSaving(true); setUploadProgress(0);
    try {
      const progress = (event) => { if (event.total) setUploadProgress(Math.round((event.loaded * 100) / event.total)); };
      await replaceWordTemplateFile(templateModal.record.id, templateModal.file, progress);
      setTemplateModal(null); setNotice({ text: 'Document template saved.' });
      await loadTemplates();
    } catch (error) { setNotice({ type: 'error', text: cleanMessage(error, 'Unable to save document template.') }); }
    finally { setSaving(false); setUploadProgress(0); }
  };
  const toggleActive = async (template) => {
    if (saving) return;
    setSaving(true);
    try {
      await setWordTemplateActive(template.id, !template.isActive);
      await loadTemplates();
    } catch (error) { setNotice({ type: 'error', text: cleanMessage(error, 'Unable to change activation.') }); }
    finally { setSaving(false); }
  };
  const openTemplate = (record) => setTemplateModal({ record, file: null });

  return (
    <div className="flex h-full min-h-0 flex-col bg-white text-[12px] text-black">
      <Notice notice={notice} onClose={() => setNotice(null)} />
      <div className="shrink-0 border-b border-black/20 bg-[#f8fafc] px-3 py-2">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="text-[16px] font-black uppercase text-[#0052CC]">Document Template Master</div>
            <div className="text-[11px] font-bold uppercase text-black/50">Upload DOCX files and activate registered document templates</div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <Search size={14} className="absolute left-2 top-1/2 -translate-y-1/2 text-black/40" />
              <input
                value={filters.keyword}
                onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))}
                onKeyDown={(event) => { if (event.key === 'Enter') loadTemplates(); }}
                placeholder="Search templates"
                className="h-8 w-56 border border-black/25 pl-7 pr-2 text-[12px] font-bold outline-none focus:border-[#0052CC]"
              />
            </div>
            <button type="button" onClick={() => loadTemplates()} className="inline-flex h-8 items-center gap-1 border border-black/25 bg-white px-3 font-black uppercase">
              <RefreshCw size={14} /> Refresh
            </button>
          </div>
        </div>
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 overflow-hidden p-3 xl:grid-cols-[minmax(0,65fr)_minmax(0,35fr)]">
        <section className="flex min-h-0 flex-col border border-black/20 bg-white">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-black/20 bg-blue-50 px-3 py-2">
            <div>
              <div className="font-black uppercase text-[#0052CC]">Document Templates</div>
            </div>

          </div>
          <div className="min-h-0 flex-1 overflow-auto">
            <table className="min-w-[900px] w-full border-collapse text-left">
              <thead className="sticky top-0 bg-[#e8edf5]">
                <tr>
                  <th className="border border-black/20 px-2 py-1">ID</th><th className="border border-black/20 px-2 py-1">Document Name</th>
                  <th className="border border-black/20 px-2 py-1">Language</th><th className="border border-black/20 px-2 py-1">File</th>
                  <th className="border border-black/20 px-2 py-1">Status</th><th className="border border-black/20 px-2 py-1 text-center">Actions</th>
                </tr>
              </thead>
              <tbody>
                {templates.map((template) => (
                  <tr key={template.id} className="hover:bg-blue-50"><td className="border border-black/15 px-2 py-1">{template.id}</td>
                    <td className="border border-black/15 px-2 py-1 align-top">
                      <div className="font-black uppercase">{template.templateName}</div>
                    </td>
                    <td className="border border-black/15 px-2 py-1 align-top font-bold">{template.languageCode === 'hi' ? 'Hindi' : 'English'}</td><td className="border border-black/15 px-2 py-1 align-top font-bold">{template.hasFile ? template.originalFilename : 'DOCX upload required'}</td>
                    <td className="border border-black/15 px-2 py-1">{template.isActive ? 'Active' : 'Inactive'}</td>
                    <td className="border border-black/15 px-2 py-1 align-top">
                      <div className="flex flex-wrap justify-center gap-1">
                        <button type="button" disabled={saving} onClick={() => openTemplate(template)} className="inline-flex items-center gap-1 border px-2 py-1 disabled:opacity-50"><Upload size={13} /> {template.hasFile ? 'Replace DOCX' : 'Upload DOCX'}</button>
                        <button type="button" disabled={saving || (!template.isActive && !template.hasFile)} onClick={() => toggleActive(template)} className="border px-2 py-1 disabled:opacity-50">{template.isActive ? 'Deactivate' : 'Activate'}</button>

                      </div>
                    </td>
                  </tr>
                ))}
                {!loading && templates.length === 0 && (
                  <tr>
                    <td colSpan={6} className="border border-black/15 px-3 py-10 text-center font-black uppercase text-black/40">No templates found</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section className="flex min-h-0 flex-col border border-black/20 bg-white">
          <div className="border-b border-black/20 bg-blue-50 px-3 py-2 font-black uppercase text-[#0052CC]">Available Fields</div>
          <form onSubmit={previewContractFields} className="flex items-end gap-2 border-b border-black/15 p-2">
            <label className="min-w-0 flex-1 font-bold">Contract Number<input required aria-label="Contract Number" value={contractNumber} onChange={(e) => setContractNumber(e.target.value)} className="field mt-1" /></label>
            <button type="submit" disabled={previewing || !contractNumber.trim()} className="h-9 border border-[#0052CC] bg-[#0052CC] px-3 font-black text-white disabled:opacity-50">{previewing ? 'Loading...' : 'Preview'}</button>
          </form>
          {previewContract && <div className="border-b px-2 py-1 font-bold">English values for {previewContract}</div>}
          <div className="border-b border-black/15 p-2"><div className="relative">
            <Search size={14} className="absolute left-2 top-1/2 -translate-y-1/2 text-black/40" />
            <input aria-label="Search available fields" value={fieldKeyword} onChange={(e) => setFieldKeyword(e.target.value)} placeholder="Search category, field, placeholder, alias or value" className="h-8 w-full border border-black/25 pl-7 pr-2 text-[12px] font-bold outline-none focus:border-[#0052CC]" />
          </div></div>
          <div className="min-h-0 flex-1 overflow-auto"><table className="w-full min-w-[720px] border-collapse text-left">
            <thead className="sticky top-0 bg-[#e8edf5]"><tr>{['Category', 'Field', 'Placeholder', 'Alias', 'Value'].map((heading) => <th key={heading} className="border border-black/20 px-2 py-1">{heading}</th>)}</tr></thead>
            <tbody>{filteredFields.map((field) => <tr key={field.placeholderCode} className="hover:bg-blue-50">
              <td className="border border-black/15 px-2 py-1 font-bold">{field.category}</td>
              <td className="border border-black/15 px-2 py-1 font-bold">{field.fieldName}</td>
              <td className="border border-black/15 px-2 py-1"><span className="font-black text-[#0052CC]">{field.placeholderCode}</span><button type="button" title="Copy placeholder" onClick={() => navigator.clipboard?.writeText(field.placeholderCode)} className="ml-1 inline-grid h-7 w-7 place-items-center border border-black/20 bg-white"><Copy size={13} /></button></td>
              <td className="border border-black/15 px-2 py-1">{(() => {
                const aliases = fieldMetadata.find((m) => m.canonicalCode === field.placeholderCode)?.aliases || [];
                return (aliases.length ? aliases : [{ alias: '' }]).map((alias) => {
                  const key = alias.id ?? field.placeholderCode;
                  return <div key={key} className="py-1">
                    <div className="flex items-center gap-1">
                      <input aria-label={`Alias for ${field.placeholderCode}${alias.id != null ? ` (${alias.alias})` : ''}`} placeholder="{{alias}}" maxLength={154} disabled={aliasSaving[key]} value={aliasDrafts[key] ?? (alias.alias ? `{{${alias.alias}}}` : '')} onChange={(event) => { const value = event.target.value; setAliasDrafts((current) => ({ ...current, [key]: value })); }} className="field min-w-32" />
                      <button type="button" disabled={aliasSaving[key]} onClick={() => saveAlias(field, alias)} className="whitespace-nowrap border px-2 py-1 disabled:opacity-50">{aliasSaving[key] ? 'Saving...' : '✓ Save'}</button>
                    </div>
                    {aliasMessages[key] && <p role="alert" className="mt-1 text-red-700">{aliasMessages[key]}</p>}
                  </div>;
                });
              })()}</td>
              <td className="border border-black/15 px-2 py-1 whitespace-pre-wrap">{previewValues[field.placeholderCode] ?? ''}</td>
            </tr>)}</tbody>
          </table>{filteredFields.length === 0 && <p className="p-4 text-center">No matching fields</p>}</div>
        </section>
      </div>

      {loading && (
        <div className="fixed inset-0 z-[200] grid place-items-center bg-black/20">
          <div className="flex items-center gap-2 border border-blue-200 bg-white px-4 py-3 font-black uppercase text-[#0052CC] shadow-xl">
            <Loader2 size={18} className="animate-spin" /> Loading
          </div>
        </div>
      )}


      {templateModal && (
        <Modal title={`Upload DOCX - ${templateModal.record.templateName}`} onClose={() => setTemplateModal(null)}>
          <form onSubmit={submitTemplate} className="space-y-3 p-4">
            <p className="font-bold">{templateModal.record.templateName} ? {templateModal.record.languageCode === 'hi' ? 'Hindi' : 'English'}</p>
            <Field label="DOCX File"><input required type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(e) => setTemplateModal((m) => ({ ...m, file: e.target.files?.[0] || null }))} className="field" /></Field>
            {uploadProgress > 0 && <div className="h-2 bg-blue-100"><div className="h-full bg-[#0052CC]" style={{ width: `${uploadProgress}%` }} /></div>}
            <ModalActions saving={saving} onCancel={() => setTemplateModal(null)} />
          </form>
        </Modal>
      )}


    </div>
  );
}

function Field({ label, children }) {
  return (
    <label className="block">
      <span className="mb-1 block text-[11px] font-black uppercase text-black/60">{label}</span>
      {children}
    </label>
  );
}

function ModalActions({ saving, onCancel }) {
  return (
    <div className="flex justify-end gap-2 border-t border-black/10 pt-3">
      <button type="button" onClick={onCancel} disabled={saving} className="border border-black/25 bg-white px-4 py-2 font-black uppercase">Cancel</button>
      <button type="submit" disabled={saving} className="inline-flex items-center gap-2 border border-[#0052CC] bg-[#0052CC] px-4 py-2 font-black uppercase text-white disabled:opacity-50">
        {saving ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />} Save
      </button>
    </div>
  );
}
