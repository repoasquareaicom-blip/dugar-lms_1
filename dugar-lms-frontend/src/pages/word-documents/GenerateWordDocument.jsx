import { useCallback, useEffect, useMemo, useState } from 'react';
import { Download, FileText, Loader2, RefreshCw, Search } from 'lucide-react';
import {
  fetchWordTemplateGroups,
  fetchWordTemplates,
  generateWordDocument,
  searchWordContracts,
  validateWordTemplate,
} from '../../services/wordTemplateService';

function cleanMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback;
}

export default function GenerateWordDocument() {
  const [keyword, setKeyword] = useState('');
  const [contracts, setContracts] = useState([]);
  const [selectedContract, setSelectedContract] = useState(null);
  const [groups, setGroups] = useState([]);
  const [templates, setTemplates] = useState([]);
  const [groupId, setGroupId] = useState('');
  const [templateId, setTemplateId] = useState('');
  const [validation, setValidation] = useState(null);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [notice, setNotice] = useState(null);

  const activeGroups = useMemo(() => groups.filter((group) => group.isActive !== false && (group.dataSource || 'CONTRACT') === 'CONTRACT'), [groups]);
  const activeTemplates = useMemo(() => templates.filter((template) => template.isActive !== false), [templates]);

  const loadGroups = useCallback(async () => {
    try {
      const data = await fetchWordTemplateGroups({ isActive: true });
      const nextGroups = Array.isArray(data) ? data : [];
      setGroups(nextGroups);
      if (!groupId && nextGroups[0]?.id) setGroupId(String(nextGroups[0].id));
    } catch (error) {
      setNotice({ type: 'error', text: cleanMessage(error, 'Unable to load template groups.') });
    }
  }, [groupId]);

  const searchContracts = useCallback(async () => {
    setLoading(true);
    try {
      const data = await searchWordContracts({ keyword, limit: 20 });
      setContracts(Array.isArray(data) ? data : []);
    } catch (error) {
      setNotice({ type: 'error', text: cleanMessage(error, 'Unable to search contracts.') });
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    loadGroups();
    searchContracts();
  }, []);

  useEffect(() => {
    if (!groupId) return;
    fetchWordTemplates({ groupId, isActive: true })
      .then((data) => {
        const nextTemplates = Array.isArray(data) ? data : [];
        setTemplates(nextTemplates);
        setTemplateId(nextTemplates[0]?.id ? String(nextTemplates[0].id) : '');
      })
      .catch((error) => setNotice({ type: 'error', text: cleanMessage(error, 'Unable to load templates.') }));
  }, [groupId]);

  useEffect(() => {
    if (!templateId) {
      setValidation(null);
      return;
    }
    validateWordTemplate(templateId, { dataSource: 'CONTRACT' })
      .then(setValidation)
      .catch((error) => setNotice({ type: 'error', text: cleanMessage(error, 'Unable to validate template.') }));
  }, [templateId]);

  const submit = async () => {
    if (!selectedContract || !templateId) return;
    setGenerating(true);
    try {
      await generateWordDocument({ dataSource: 'CONTRACT', contractId: selectedContract.contractId, templateId: Number(templateId) });
      setNotice({ text: 'Word document generated.' });
    } catch (error) {
      setNotice({ type: 'error', text: cleanMessage(error, 'Unable to generate Word document.') });
    } finally {
      setGenerating(false);
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col bg-white text-[12px] text-black">
      <div className="shrink-0 border-b border-black/20 bg-[#f8fafc] px-3 py-2">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="text-[16px] font-black uppercase text-[#0052CC]">Generate Word Document</div>
            <div className="text-[11px] font-bold uppercase text-black/50">CONTRACT document generation</div>
          </div>
          {notice && <div className={`border px-3 py-1.5 font-black uppercase ${notice.type === 'error' ? 'border-red-200 bg-red-50 text-red-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}`}>{notice.text}</div>}
        </div>
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 overflow-hidden p-3 lg:grid-cols-[420px_minmax(0,1fr)]">
        <section className="flex min-h-0 flex-col border border-black/20 bg-white">
          <div className="border-b border-black/20 bg-blue-50 px-3 py-2 font-black uppercase text-[#0052CC]">Select Contract</div>
          <div className="flex gap-2 border-b border-black/15 p-2">
            <div className="relative min-w-0 flex-1">
              <Search size={14} className="absolute left-2 top-1/2 -translate-y-1/2 text-black/40" />
              <input value={keyword} onChange={(event) => setKeyword(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') searchContracts(); }} placeholder="Contract no / borrower" className="h-8 w-full border border-black/25 pl-7 pr-2 font-bold outline-none focus:border-[#0052CC]" />
            </div>
            <button type="button" onClick={searchContracts} className="grid h-8 w-8 place-items-center border border-black/25 bg-white" title="Search"><RefreshCw size={14} /></button>
          </div>
          <div className="min-h-0 flex-1 overflow-auto">
            {contracts.map((contract) => (
              <button key={contract.contractId} type="button" onClick={() => setSelectedContract(contract)} className={`block w-full border-b border-black/10 px-3 py-2 text-left hover:bg-blue-50 ${selectedContract?.contractId === contract.contractId ? 'bg-blue-100' : 'bg-white'}`}>
                <div className="font-black uppercase">{contract.contractNumber}</div>
                <div className="mt-1 text-[11px] font-bold text-black/60">{contract.borrowerName} | {contract.borrowerCode || '-'}</div>
              </button>
            ))}
            {!loading && contracts.length === 0 && <div className="p-6 text-center font-black uppercase text-black/40">No contracts found</div>}
            {loading && <div className="p-6 text-center font-black uppercase text-[#0052CC]">Loading</div>}
          </div>
        </section>

        <section className="flex min-h-0 flex-col border border-black/20 bg-white">
          <div className="border-b border-black/20 bg-blue-50 px-3 py-2 font-black uppercase text-[#0052CC]">Template & Confirmation</div>
          <div className="grid gap-3 p-4">
            <div className="border border-black/15 bg-slate-50 p-3">
              <div className="text-[11px] font-black uppercase text-black/50">Selected Contract</div>
              <div className="mt-1 text-[18px] font-black text-black">{selectedContract?.contractNumber || '-'}</div>
              <div className="mt-1 font-bold text-black/65">{selectedContract?.borrowerName || '-'}</div>
            </div>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              <Field label="Data Source"><select value="CONTRACT" disabled className="field"><option>CONTRACT</option></select></Field>
              <Field label="Template Group">
                <select value={groupId} onChange={(event) => setGroupId(event.target.value)} className="field">
                  {activeGroups.map((group) => <option key={group.id} value={group.id}>{group.groupName}</option>)}
                </select>
              </Field>
              <Field label="Word Template">
                <select value={templateId} onChange={(event) => setTemplateId(event.target.value)} className="field">
                  {activeTemplates.map((template) => <option key={template.id} value={template.id}>{template.templateName}</option>)}
                </select>
              </Field>
              <div className="border border-black/15 bg-slate-50 p-3">
                <div className="text-[11px] font-black uppercase text-black/50">Validation</div>
                <div className={`mt-1 font-black uppercase ${validation?.unknown?.length ? 'text-red-700' : 'text-emerald-700'}`}>
                  {validation ? `${validation.recognized?.length || 0} recognized, ${validation.unknown?.length || 0} unknown` : '-'}
                </div>
              </div>
            </div>
            <button type="button" onClick={submit} disabled={!selectedContract || !templateId || generating || validation?.unknown?.length > 0} className="inline-flex h-10 w-fit items-center gap-2 border border-[#0052CC] bg-[#0052CC] px-4 font-black uppercase text-white disabled:opacity-50">
              {generating ? <Loader2 size={16} className="animate-spin" /> : <Download size={16} />} Generate & Download
            </button>
            {validation?.unknown?.length > 0 && (
              <div className="border border-red-200 bg-red-50 p-3 font-bold text-red-800">Unknown placeholders: {validation.unknown.join(', ')}</div>
            )}
          </div>
        </section>
      </div>
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
