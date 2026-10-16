export default function WordDocumentPlaceholder({ title }) {
  return (
    <div className="flex h-full min-h-[320px] items-center justify-center bg-white p-6 text-center">
      <div className="max-w-lg border border-black/20 bg-slate-50 p-6 shadow-sm">
        <div className="text-[12px] font-black uppercase tracking-wide text-[#0052CC]">Stage 1</div>
        <h1 className="mt-2 text-[22px] font-black uppercase text-black">{title}</h1>
        <p className="mt-3 text-[13px] font-bold leading-5 text-black/70">
          Database, menu, and permission foundations are ready. Functional upload, editing, merge, and download flows are reserved for the next stage.
        </p>
      </div>
    </div>
  );
}
