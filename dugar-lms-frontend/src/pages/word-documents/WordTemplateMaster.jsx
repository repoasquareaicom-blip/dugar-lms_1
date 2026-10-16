import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, Copy, Download, Edit3, FilePlus2, Loader2, Plus, RefreshCw, Save, Search, Trash2, Upload, X } from 'lucide-react';
import {
  createWordTemplate,
  createWordTemplateGroup,
  deleteWordTemplate,
  deleteWordTemplateGroup,
  downloadWordTemplate,
  fetchWordTemplateGroups,
  fetchWordTemplates,
  fetchWordPlaceholders,
  replaceWordTemplateFile,
  updateWordTemplate,
  updateWordTemplateGroup,
  validateWordTemplate,
} from '../../services/wordTemplateService';

const groupEmpty = { groupName: '', description: '', dataSource: 'CONTRACT', displayOrder: 0, isActive: true };
const templateEmpty = { groupId: '', templateName: '', displayOrder: 0, isActive: true, file: null };

function cleanMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback;
}

function formatDate(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date);
}

function statusText(value) {
  return value === false ? 'Inactive' : 'Active';
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
  const [groups, setGroups] = useState([]);
  const [templates, setTemplates] = useState([]);
  const [selectedGroupId, setSelectedGroupId] = useState('');
  const [filters, setFilters] = useState({ keyword: '', isActive: '' });
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState(null);
  const [groupModal, setGroupModal] = useState(null);
  const [templateModal, setTemplateModal] = useState(null);
  const [replaceModal, setReplaceModal] = useState(null);
  const [fields, setFields] = useState([]);
  const [fieldKeyword, setFieldKeyword] = useState('');
  const [validationModal, setValidationModal] = useState(null);
  const [saving, setSaving] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const didInitialLoad = useRef(false);

  const selectedGroup = useMemo(() => groups.find((group) => String(group.id) === String(selectedGroupId)), [groups, selectedGroupId]);
  const filteredFields = useMemo(() => {
    const keyword = fieldKeyword.trim().toLowerCase();
    if (!keyword) return fields;
    return fields.filter((field) => [field.category, field.fieldName, field.placeholderCode, field.sourceTable, field.sourceColumn].some((value) => String(value || '').toLowerCase().includes(keyword)));
  }, [fieldKeyword, fields]);

  const load = useCallback(async (nextGroupId = selectedGroupId) => {
    setLoading(true);
    try {
      const activeParam = filters.isActive === '' ? undefined : filters.isActive === 'true';
      const [groupData, templateData] = await Promise.all([
        fetchWordTemplateGroups({ keyword: filters.keyword || undefined }),
        fetchWordTemplates({ groupId: nextGroupId || undefined, keyword: filters.keyword || undefined, isActive: activeParam }),
      ]);
      setGroups(Array.isArray(groupData) ? groupData : []);
      setTemplates(Array.isArray(templateData) ? templateData : []);
      const fieldData = await fetchWordPlaceholders({ dataSource: 'CONTRACT' });
      setFields(Array.isArray(fieldData) ? fieldData : []);
      if (!nextGroupId && Array.isArray(groupData) && groupData[0]?.id) {
        setSelectedGroupId(String(groupData[0].id));
      }
    } catch (error) {
      setNotice({ type: 'error', text: cleanMessage(error, 'Unable to load Word Template Master.') });
    } finally {
      setLoading(false);
    }
  }, [filters.isActive, filters.keyword, selectedGroupId]);

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    if (!didInitialLoad.current) {
      didInitialLoad.current = true;
      return;
    }
    load(selectedGroupId);
  }, [load, selectedGroupId]);

  const submitGroup = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      const payload = {
        groupName: groupModal.form.groupName.trim(),
        description: groupModal.form.description.trim(),
        dataSource: groupModal.form.dataSource || 'CONTRACT',
        displayOrder: Number(groupModal.form.displayOrder || 0),
        isActive: groupModal.form.isActive,
      };
      const saved = groupModal.record?.id
        ? await updateWordTemplateGroup(groupModal.record.id, payload)
        : await createWordTemplateGroup(payload);
      setGroupModal(null);
      setSelectedGroupId(String(saved.id));
      setNotice({ text: 'Template group saved.' });
      await load(String(saved.id));
    } catch (error) {
      setNotice({ type: 'error', text: cleanMessage(error, 'Unable to save template group.') });
    } finally {
      setSaving(false);
    }
  };

  const submitTemplate = async (event) => {
    event.preventDefault();
    setSaving(true);
    setUploadProgress(0);
    try {
      const payload = {
        groupId: Number(templateModal.form.groupId),
        templateName: templateModal.form.templateName.trim(),
        displayOrder: Number(templateModal.form.displayOrder || 0),
        isActive: templateModal.form.isActive,
        file: templateModal.form.file,
      };
      if (templateModal.record?.id) {
        await updateWordTemplate(templateModal.record.id, payload);
      } else {
        await createWordTemplate(payload, (event) => {
          if (event.total) setUploadProgress(Math.round((event.loaded * 100) / event.total));
        });
      }
      setTemplateModal(null);
      setNotice({ text: 'Word template saved.' });
      await load(selectedGroupId);
    } catch (error) {
      setNotice({ type: 'error', text: cleanMessage(error, 'Unable to save Word template.') });
    } finally {
      setSaving(false);
      setUploadProgress(0);
    }
  };

  const submitReplacement = async () => {
    if (!replaceModal?.file) return;
    setSaving(true);
    setUploadProgress(0);
    try {
      await replaceWordTemplateFile(replaceModal.record.id, replaceModal.file, (event) => {
        if (event.total) setUploadProgress(Math.round((event.loaded * 100) / event.total));
      });
      setReplaceModal(null);
      setNotice({ text: 'DOCX file replaced.' });
      await load(selectedGroupId);
    } catch (error) {
      setNotice({ type: 'error', text: cleanMessage(error, 'Unable to replace DOCX file.') });
    } finally {
      setSaving(false);
      setUploadProgress(0);
    }
  };

  const confirmDeleteGroup = async (group) => {
    if (!window.confirm(`Delete group "${group.groupName}"?`)) return;
    try {
      await deleteWordTemplateGroup(group.id);
      setNotice({ text: 'Template group deleted.' });
      setSelectedGroupId('');
      await load('');
    } catch (error) {
      setNotice({ type: 'error', text: cleanMessage(error, 'Unable to delete template group.') });
    }
  };

  const confirmDeleteTemplate = async (template) => {
    if (!window.confirm(`Delete template "${template.templateName}"?`)) return;
    try {
      await deleteWordTemplate(template.id);
      setNotice({ text: 'Word template deleted.' });
      await load(selectedGroupId);
    } catch (error) {
      setNotice({ type: 'error', text: cleanMessage(error, 'Unable to delete Word template.') });
    }
  };

  const openGroup = (record = null) => setGroupModal({ record, form: record ? { ...record } : groupEmpty });
  const openTemplate = (record = null) => setTemplateModal({
    record,
    form: record ? { ...record, file: null } : { ...templateEmpty, groupId: selectedGroupId || groups[0]?.id || '' },
  });

  return (
    <div className="flex h-full min-h-0 flex-col bg-white text-[12px] text-black">
      <Notice notice={notice} onClose={() => setNotice(null)} />
      <div className="shrink-0 border-b border-black/20 bg-[#f8fafc] px-3 py-2">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="text-[16px] font-black uppercase text-[#0052CC]">Word Template Master</div>
            <div className="text-[11px] font-bold uppercase text-black/50">Manage DOCX template groups and source files</div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <Search size={14} className="absolute left-2 top-1/2 -translate-y-1/2 text-black/40" />
              <input
                value={filters.keyword}
                onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))}
                onKeyDown={(event) => { if (event.key === 'Enter') load(selectedGroupId); }}
                placeholder="Search group/template"
                className="h-8 w-56 border border-black/25 pl-7 pr-2 text-[12px] font-bold outline-none focus:border-[#0052CC]"
              />
            </div>
            <select
              value={filters.isActive}
              onChange={(event) => setFilters((current) => ({ ...current, isActive: event.target.value }))}
              className="h-8 border border-black/25 bg-white px-2 text-[12px] font-bold outline-none"
            >
              <option value="">All Status</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </select>
            <button type="button" onClick={() => load(selectedGroupId)} className="inline-flex h-8 items-center gap-1 border border-black/25 bg-white px-3 font-black uppercase">
              <RefreshCw size={14} /> Refresh
            </button>
          </div>
        </div>
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 overflow-hidden p-3 xl:grid-cols-[300px_minmax(0,1fr)_390px]">
        <section className="flex min-h-0 flex-col border border-black/20 bg-white">
          <div className="flex items-center justify-between border-b border-black/20 bg-blue-50 px-3 py-2">
            <div className="font-black uppercase text-[#0052CC]">Template Groups</div>
            <button type="button" onClick={() => openGroup()} className="inline-flex h-7 items-center gap-1 border border-[#0052CC] bg-[#0052CC] px-2 font-black uppercase text-white">
              <Plus size={13} /> Add
            </button>
          </div>
          <div className="min-h-0 flex-1 overflow-auto">
            {groups.map((group) => (
              <button
                key={group.id}
                type="button"
                onClick={() => setSelectedGroupId(String(group.id))}
                className={`block w-full border-b border-black/10 px-3 py-2 text-left hover:bg-blue-50 ${String(group.id) === String(selectedGroupId) ? 'bg-blue-100' : 'bg-white'}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <div className="font-black uppercase text-black">{group.groupName}</div>
                    <div className="mt-0.5 line-clamp-2 text-[11px] font-bold text-black/60">{group.description || '-'}</div>
                    <div className="mt-1 text-[10px] font-black uppercase text-[#0052CC]">{group.dataSource || 'CONTRACT'}</div>
                  </div>
                  <span className={`shrink-0 border px-1.5 py-0.5 text-[10px] font-black uppercase ${group.isActive === false ? 'border-red-200 bg-red-50 text-red-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}`}>
                    {statusText(group.isActive)}
                  </span>
                </div>
                <div className="mt-2 flex items-center justify-between text-[11px] font-bold text-black/50">
                  <span>Order {group.displayOrder ?? 0} | {group.templateCount ?? 0} templates</span>
                  <span className="text-[#0052CC]">Select</span>
                </div>
              </button>
            ))}
            {!loading && groups.length === 0 && <div className="p-5 text-center font-black uppercase text-black/40">No groups found</div>}
          </div>
          {selectedGroup && (
            <div className="flex gap-2 border-t border-black/20 p-2">
              <button type="button" onClick={() => openGroup(selectedGroup)} className="inline-flex flex-1 items-center justify-center gap-1 border border-black/25 bg-white px-2 py-1.5 font-black uppercase">
                <Edit3 size={13} /> Edit
              </button>
              <button type="button" onClick={() => confirmDeleteGroup(selectedGroup)} className="inline-flex flex-1 items-center justify-center gap-1 border border-red-300 bg-red-50 px-2 py-1.5 font-black uppercase text-red-700">
                <Trash2 size={13} /> Delete
              </button>
            </div>
          )}
        </section>

        <section className="flex min-h-0 flex-col border border-black/20 bg-white">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-black/20 bg-blue-50 px-3 py-2">
            <div>
              <div className="font-black uppercase text-[#0052CC]">Word Templates</div>
              <div className="text-[11px] font-bold text-black/60">{selectedGroup?.groupName || 'All groups'}</div>
            </div>
            <button type="button" onClick={() => openTemplate()} disabled={groups.length === 0} className="inline-flex h-7 items-center gap-1 border border-[#0052CC] bg-[#0052CC] px-2 font-black uppercase text-white disabled:opacity-50">
              <FilePlus2 size={13} /> Add Template
            </button>
          </div>
          <div className="min-h-0 flex-1 overflow-auto">
            <table className="min-w-[900px] w-full border-collapse text-left">
              <thead className="sticky top-0 bg-[#e8edf5]">
                <tr>
                  <th className="border border-black/20 px-2 py-1">Template</th>
                  <th className="border border-black/20 px-2 py-1">File</th>
                  <th className="border border-black/20 px-2 py-1">Upload</th>
                  <th className="border border-black/20 px-2 py-1">Status</th>
                  <th className="border border-black/20 px-2 py-1 text-center">Actions</th>
                </tr>
              </thead>
              <tbody>
                {templates.map((template) => (
                  <tr key={template.id} className="hover:bg-blue-50">
                    <td className="border border-black/15 px-2 py-1 align-top">
                      <div className="font-black uppercase">{template.templateName}</div>
                      <div className="text-[11px] font-bold text-black/55">{template.groupName} | Order {template.displayOrder ?? 0}</div>
                    </td>
                    <td className="border border-black/15 px-2 py-1 align-top font-bold">{template.originalFilename}</td>
                    <td className="border border-black/15 px-2 py-1 align-top">
                      <div className="font-bold">{formatDate(template.createdAt)}</div>
                      <div className="text-[11px] text-black/55">By {template.createdBy || '-'}</div>
                      <div className="text-[11px] text-black/45">Updated {formatDate(template.updatedAt)}</div>
                    </td>
                    <td className="border border-black/15 px-2 py-1 align-top font-black uppercase">{statusText(template.isActive)}</td>
                    <td className="border border-black/15 px-2 py-1 align-top">
                      <div className="flex flex-wrap justify-center gap-1">
                        <button type="button" title="Edit metadata" onClick={() => openTemplate(template)} className="grid h-7 w-7 place-items-center border border-black/20 bg-white"><Edit3 size={13} /></button>
                        <button type="button" title="Download original" onClick={() => downloadWordTemplate(template.id, template.originalFilename)} className="grid h-7 w-7 place-items-center border border-black/20 bg-white"><Download size={13} /></button>
                        <button type="button" title="Validate placeholders" onClick={async () => setValidationModal({ template, result: await validateWordTemplate(template.id, { dataSource: selectedGroup?.dataSource || 'CONTRACT' }) })} className="grid h-7 w-7 place-items-center border border-black/20 bg-white"><CheckCircle2 size={13} /></button>
                        <button type="button" title="Replace DOCX" onClick={() => setReplaceModal({ record: template, file: null })} className="grid h-7 w-7 place-items-center border border-black/20 bg-white"><Upload size={13} /></button>
                        <button type="button" title="Delete" onClick={() => confirmDeleteTemplate(template)} className="grid h-7 w-7 place-items-center border border-red-300 bg-red-50 text-red-700"><Trash2 size={13} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
                {!loading && templates.length === 0 && (
                  <tr>
                    <td colSpan={5} className="border border-black/15 px-3 py-10 text-center font-black uppercase text-black/40">No templates found</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section className="flex min-h-0 flex-col border border-black/20 bg-white">
          <div className="border-b border-black/20 bg-blue-50 px-3 py-2">
            <div className="font-black uppercase text-[#0052CC]">Available Fields</div>
            <div className="text-[11px] font-bold text-black/60">CONTRACT placeholder master</div>
          </div>
          <div className="border-b border-black/15 p-2">
            <div className="relative">
              <Search size={14} className="absolute left-2 top-1/2 -translate-y-1/2 text-black/40" />
              <input value={fieldKeyword} onChange={(event) => setFieldKeyword(event.target.value)} placeholder="Search placeholders" className="h-8 w-full border border-black/25 pl-7 pr-2 text-[12px] font-bold outline-none focus:border-[#0052CC]" />
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-auto">
            <table className="w-full min-w-[620px] border-collapse text-left">
              <thead className="sticky top-0 bg-[#e8edf5]">
                <tr>
                  <th className="border border-black/20 px-2 py-1">Category</th>
                  <th className="border border-black/20 px-2 py-1">Field</th>
                  <th className="border border-black/20 px-2 py-1">Placeholder</th>
                  <th className="border border-black/20 px-2 py-1">Source</th>
                  <th className="border border-black/20 px-2 py-1 text-center">Copy</th>
                </tr>
              </thead>
              <tbody>
                {filteredFields.map((field) => (
                  <tr key={field.placeholderCode} className="hover:bg-blue-50">
                    <td className="border border-black/15 px-2 py-1 font-bold">{field.category}</td>
                    <td className="border border-black/15 px-2 py-1 font-bold">{field.fieldName}</td>
                    <td className="border border-black/15 px-2 py-1 font-black text-[#0052CC]">{field.placeholderCode}</td>
                    <td className="border border-black/15 px-2 py-1 text-[11px] font-bold text-black/60">{field.sourceTable}{field.sourceColumn ? `.${field.sourceColumn}` : ''}</td>
                    <td className="border border-black/15 px-2 py-1 text-center">
                      <button type="button" title="Copy placeholder" onClick={() => navigator.clipboard?.writeText(field.placeholderCode)} className="grid h-7 w-7 place-items-center border border-black/20 bg-white"><Copy size={13} /></button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {loading && (
        <div className="fixed inset-0 z-[200] grid place-items-center bg-black/20">
          <div className="flex items-center gap-2 border border-blue-200 bg-white px-4 py-3 font-black uppercase text-[#0052CC] shadow-xl">
            <Loader2 size={18} className="animate-spin" /> Loading
          </div>
        </div>
      )}

      {groupModal && (
        <Modal title={groupModal.record?.id ? 'Edit Template Group' : 'Add Template Group'} onClose={() => setGroupModal(null)}>
          <form onSubmit={submitGroup} className="space-y-3 p-4">
            <Field label="Group Name"><input required maxLength={150} value={groupModal.form.groupName} onChange={(e) => setGroupModal((m) => ({ ...m, form: { ...m.form, groupName: e.target.value } }))} className="field" /></Field>
            <Field label="Description"><textarea maxLength={500} value={groupModal.form.description || ''} onChange={(e) => setGroupModal((m) => ({ ...m, form: { ...m.form, description: e.target.value } }))} className="field h-20 resize-none" /></Field>
            <Field label="Data Source">
              <select value={groupModal.form.dataSource || 'CONTRACT'} onChange={(e) => setGroupModal((m) => ({ ...m, form: { ...m.form, dataSource: e.target.value } }))} className="field">
                <option value="CONTRACT">CONTRACT</option>
              </select>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Display Order"><input type="number" value={groupModal.form.displayOrder ?? 0} onChange={(e) => setGroupModal((m) => ({ ...m, form: { ...m.form, displayOrder: e.target.value } }))} className="field" /></Field>
              <label className="mt-5 flex items-center gap-2 font-black uppercase"><input type="checkbox" checked={groupModal.form.isActive !== false} onChange={(e) => setGroupModal((m) => ({ ...m, form: { ...m.form, isActive: e.target.checked } }))} /> Active</label>
            </div>
            <ModalActions saving={saving} onCancel={() => setGroupModal(null)} />
          </form>
        </Modal>
      )}

      {templateModal && (
        <Modal title={templateModal.record?.id ? 'Edit Template Metadata' : 'Add Word Template'} onClose={() => setTemplateModal(null)}>
          <form onSubmit={submitTemplate} className="space-y-3 p-4">
            <Field label="Template Group">
              <select required value={templateModal.form.groupId || ''} onChange={(e) => setTemplateModal((m) => ({ ...m, form: { ...m.form, groupId: e.target.value } }))} className="field">
                <option value="">Select Group</option>
                {groups.map((group) => <option key={group.id} value={group.id}>{group.groupName}</option>)}
              </select>
            </Field>
            <Field label="Template Name"><input required maxLength={150} value={templateModal.form.templateName || ''} onChange={(e) => setTemplateModal((m) => ({ ...m, form: { ...m.form, templateName: e.target.value } }))} className="field" /></Field>
            {!templateModal.record?.id && <Field label="DOCX File"><input required type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(e) => setTemplateModal((m) => ({ ...m, form: { ...m.form, file: e.target.files?.[0] || null } }))} className="field file:mr-3 file:border-0 file:bg-blue-50 file:px-3 file:py-1 file:font-black" /></Field>}
            <div className="grid grid-cols-2 gap-3">
              <Field label="Display Order"><input type="number" value={templateModal.form.displayOrder ?? 0} onChange={(e) => setTemplateModal((m) => ({ ...m, form: { ...m.form, displayOrder: e.target.value } }))} className="field" /></Field>
              <label className="mt-5 flex items-center gap-2 font-black uppercase"><input type="checkbox" checked={templateModal.form.isActive !== false} onChange={(e) => setTemplateModal((m) => ({ ...m, form: { ...m.form, isActive: e.target.checked } }))} /> Active</label>
            </div>
            {uploadProgress > 0 && <div className="h-2 bg-blue-100"><div className="h-full bg-[#0052CC]" style={{ width: `${uploadProgress}%` }} /></div>}
            <ModalActions saving={saving} onCancel={() => setTemplateModal(null)} />
          </form>
        </Modal>
      )}

      {replaceModal && (
        <Modal title={`Replace DOCX - ${replaceModal.record.templateName}`} onClose={() => setReplaceModal(null)}>
          <div className="space-y-4 p-4">
            <div className="border border-amber-200 bg-amber-50 p-3 font-bold text-amber-900">Replacing the file keeps template ID {replaceModal.record.id} and updates the original filename.</div>
            <input type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(e) => setReplaceModal((m) => ({ ...m, file: e.target.files?.[0] || null }))} className="field file:mr-3 file:border-0 file:bg-blue-50 file:px-3 file:py-1 file:font-black" />
            {uploadProgress > 0 && <div className="h-2 bg-blue-100"><div className="h-full bg-[#0052CC]" style={{ width: `${uploadProgress}%` }} /></div>}
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setReplaceModal(null)} className="border border-black/25 bg-white px-4 py-2 font-black uppercase">Cancel</button>
              <button type="button" disabled={!replaceModal.file || saving} onClick={submitReplacement} className="inline-flex items-center gap-2 border border-[#0052CC] bg-[#0052CC] px-4 py-2 font-black uppercase text-white disabled:opacity-50">
                {saving ? <Loader2 size={15} className="animate-spin" /> : <Upload size={15} />} Replace
              </button>
            </div>
          </div>
        </Modal>
      )}

      {validationModal && (
        <Modal title={`Validation - ${validationModal.template.templateName}`} onClose={() => setValidationModal(null)}>
          <div className="space-y-3 p-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="border border-emerald-200 bg-emerald-50 p-3">
                <div className="font-black uppercase text-emerald-800">Recognized</div>
                <div className="mt-2 max-h-40 overflow-auto font-bold text-emerald-900">{validationModal.result.recognized?.join(', ') || '-'}</div>
              </div>
              <div className="border border-red-200 bg-red-50 p-3">
                <div className="font-black uppercase text-red-800">Unknown</div>
                <div className="mt-2 max-h-40 overflow-auto font-bold text-red-900">{validationModal.result.unknown?.join(', ') || '-'}</div>
              </div>
            </div>
          </div>
        </Modal>
      )}

      <style>{`.field{height:2.25rem;width:100%;border:1px solid rgb(0 0 0 / .25);background:white;padding:0 .5rem;font-size:12px;font-weight:700;outline:none}.field:focus{border-color:#0052CC}`}</style>
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
