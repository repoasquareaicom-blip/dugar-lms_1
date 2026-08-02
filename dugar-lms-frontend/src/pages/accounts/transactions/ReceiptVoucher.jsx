import React, { useState, useEffect } from 'react';
import { 
  Save, Search, Calendar, Plus, Trash2, 
  Building2, ChevronDown, X, Check, Printer, 
  RefreshCcw, Info, FileText, History, AlertTriangle,
  LayoutList, Database, Edit3
} from 'lucide-react';
import { useLocation } from 'react-router-dom';

const ReceiptVoucher = () => {
  const location = useLocation();
  const selectedContract = location.state?.contract;
  const [showModal, setShowModal] = useState(false);
  const [modalTitle, setModalTitle] = useState("");
  
  // 1. STATE MANAGEMENT
  const [headerTotal, setHeaderTotal] = useState("0.00");
  const [rows, setRows] = useState([
    { id: Date.now(), detailsCode: "", loanRef: "", areaCode: "", amount: "", remarks: "" }
  ]);

  // 2. CALCULATION & VALIDATION LOGIC
  const columnTotal = rows.reduce((sum, row) => sum + (parseFloat(row.amount) || 0), 0);
  const diff = parseFloat(headerTotal || 0) - columnTotal;
  const isMatch = Math.abs(diff) < 0.01 && columnTotal > 0;

  // 3. KEYBOARD SHORTCUTS
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'F10') {
        e.preventDefault();
        if (isMatch) alert("Voucher Posted Successfully!");
        else alert("Balance Mismatch: Check Header vs Table");
      }
      if (e.key === 'F4') {
        e.preventDefault();
        openSearch("Global Transaction Find");
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isMatch]);

  const openSearch = (title) => {
    setModalTitle(title);
    setShowModal(true);
  };

  const addNewRow = () => {
    setRows([...rows, { id: Date.now(), detailsCode: "", loanRef: "", areaCode: "", amount: "", remarks: "" }]);
  };

  const removeRow = (id) => {
    if (rows.length > 1) setRows(rows.filter(row => row.id !== id));
  };

  const handleAmountChange = (id, val) => {
    setRows(rows.map(row => row.id === id ? { ...row, amount: val } : row));
  };

  return (
    <div className="min-h-screen bg-[#f1f5f9] flex flex-col font-sans text-black" style={{ fontFamily: 'Calibri, "Segoe UI", sans-serif' }}>
      
      {/* UNIVERSAL COMMAND BAR */}
      <div className="sticky top-0 z-[110] bg-white border-b-2 border-slate-300 px-6 py-2 flex justify-between items-center shadow-md">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-3 border-r border-slate-200 pr-6">
            <div className="bg-[#1e3a8a] p-1.5 rounded shadow-sm">
              <Building2 size={20} className="text-white" />
            </div>
            <div>
              <h1 className="text-[14px] font-black uppercase tracking-tighter leading-none text-black">
                Dugar Loan Edge <span className="text-blue-600">v2.0</span>
              </h1>
              <p className="text-[11px] font-bold text-slate-500 uppercase tracking-widest mt-0.5">Terminal 04 • Chennai HQ</p>
            </div>
          </div>
          <div className="flex items-center gap-2 bg-slate-100 px-3 py-1 rounded-full border border-slate-200">
            <div className={`w-2.5 h-2.5 rounded-full ${isMatch ? 'bg-emerald-500' : 'bg-amber-500'} animate-pulse`}></div>
            <span className="text-[13px] font-black uppercase text-slate-700 tracking-tight">
              {isMatch ? 'Ready to Post' : 'Entry In-Progress'}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* NAVIGATION GROUP */}
          <div className="flex items-center bg-slate-50 border border-slate-300 rounded p-1 mr-2">
            <button className="flex items-center gap-1.5 px-3 py-1 text-[14px] font-black text-slate-600 hover:bg-white hover:text-[#1e3a8a] hover:shadow-sm rounded transition-all uppercase">
              <LayoutList size={16} /> List
            </button>
            <div className="w-[1px] h-4 bg-slate-300 mx-1"></div>
            <button onClick={() => openSearch("Global Find")} className="flex items-center gap-1.5 px-3 py-1 text-[14px] font-black text-slate-600 hover:bg-white hover:text-[#1e3a8a] hover:shadow-sm rounded transition-all uppercase">
              <Search size={16} /> Find (F4)
            </button>
          </div>

          {/* RECORD ACTIONS */}
          <div className="flex items-center gap-1 mr-2">
            <button onClick={() => window.location.reload()} className="p-2 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors" title="New Entry">
              <Plus size={22} strokeWidth={3} />
            </button>
            <button className="p-2 text-slate-500 hover:text-amber-600 hover:bg-amber-50 rounded transition-colors" title="Edit Master">
              <Edit3 size={20} />
            </button>
            <button className="p-2 text-slate-500 hover:text-rose-600 hover:bg-rose-50 rounded transition-colors" title="Delete Entry">
              <Trash2 size={20} />
            </button>
          </div>

          <div className="h-8 w-[1px] bg-slate-300 mx-2" />

          {/* OUTPUT ACTIONS */}
          <div className="flex items-center gap-1 mr-4">
            <button className="p-2 text-slate-600 hover:bg-slate-100 rounded" title="Refresh Data"><RefreshCcw size={18} /></button>
            <button className="p-2 text-slate-600 hover:bg-slate-100 rounded" title="Audit History"><History size={18} /></button>
            <button className="p-2 text-slate-600 hover:bg-slate-100 rounded" title="Print (F12)"><Printer size={18} /></button>
          </div>

          <button className={`flex items-center gap-3 px-8 py-2.5 rounded text-[14px] font-black uppercase tracking-wider transition-all shadow-lg ${isMatch ? 'bg-emerald-600 text-white hover:bg-emerald-700' : 'bg-[#1e3a8a] text-white hover:bg-blue-900'}`}>
            <Save size={18} strokeWidth={3} />
            {isMatch ? 'Post Voucher' : 'Save Draft'}
            <span className="opacity-60 text-[11px] ml-2 bg-black/20 px-1.5 py-0.5 rounded">F10</span>
          </button>
        </div>
      </div>

      {/* VOUCHER WORKSPACE */}
      <div className="flex-1 overflow-y-auto py-6 px-4 flex justify-center">
        <div className="w-full max-w-5xl bg-white border border-slate-400 shadow-2xl flex flex-col h-fit">
          
          <div className="bg-slate-50 border-b border-slate-300 px-8 py-6 flex justify-between items-center">
            <div className="space-y-1">
              <h2 className="text-2xl font-black text-black tracking-tighter flex items-center gap-3">
                RECEIPT <span className="text-[#1e3a8a]">VOUCHER</span>
              </h2>
              <div className="flex items-center gap-3">
                <span className="px-2 py-0.5 bg-amber-400 text-[12px] font-black uppercase rounded">Draft Mode</span>
                <span className="text-[13px] font-bold text-slate-500 uppercase font-mono tracking-tighter">Series: RV-2026-CH</span>
              </div>
            </div>
            
            <div className="flex gap-6">
                <div className="text-right border-r border-slate-300 pr-6">
                    <p className="text-[11px] font-black text-slate-400 uppercase">System Date</p>
                    <p className="text-[16px] font-black text-black">10-APR-2026</p>
                </div>
                <div className="text-right">
                    <p className="text-[11px] font-black text-slate-400 uppercase">Voucher ID</p>
                    <p className="text-[16px] font-black text-blue-700 font-mono">#RV-000941</p>
                </div>
            </div>
          </div>

          <div className="p-8 space-y-8">
            {selectedContract && (
              <div className="grid grid-cols-4 gap-4 rounded border-2 border-blue-200 bg-blue-50 px-4 py-3 text-[12px] font-black uppercase text-black">
                <div>
                  <p className="text-blue-700">Contract ID</p>
                  <p>{selectedContract.contractId || '-'}</p>
                </div>
                <div>
                  <p className="text-blue-700">Borrower</p>
                  <p>{selectedContract.customerName || '-'}</p>
                </div>
                <div>
                  <p className="text-blue-700">Loan Amount</p>
                  <p>{selectedContract.loanAmount || '-'}</p>
                </div>
                <div>
                  <p className="text-blue-700">Branch</p>
                  <p>{selectedContract.branch || '-'}</p>
                </div>
              </div>
            )}

            {/* MASTER FIELDS */}
            <div className="grid grid-cols-4 gap-6 items-end">
              <div className="relative border-b-2 border-black group">
                <label className="text-[14px] font-black uppercase text-black mb-1 block">Category</label>
                <select className="w-full bg-transparent py-2 text-[14px] font-black text-black outline-none appearance-none uppercase cursor-pointer">
                  <option>GENERAL</option>
                  <option>LOAN</option>
                </select>
                <ChevronDown size={18} className="absolute right-0 bottom-2.5 text-black" />
              </div>

              <div className="relative border-b-2 border-black group">
                <label className="text-[14px] font-black uppercase text-black mb-1 block">Voucher Date</label>
                <input type="date" className="w-full bg-transparent py-2 text-[14px] font-black text-black outline-none" defaultValue="2026-04-10" />
              </div>

              <div className="relative border-b-2 border-black group cursor-pointer" onClick={() => openSearch("Ledger Directory")}>
                <label className="text-[14px] font-black uppercase text-black mb-1 block">Header Control Code</label>
                <div className="flex items-center justify-between py-2">
                  <span className="text-[14px] font-black text-black opacity-60 uppercase">Search Ledger...</span>
                  <Search size={18} className="text-black" />
                </div>
              </div>

              <div className={`p-3 rounded border-b-4 transition-all duration-300 ${isMatch ? 'bg-emerald-600 border-emerald-800' : 'bg-[#1e3a8a] border-blue-900 shadow-lg shadow-blue-100'}`}>
                <div className="flex justify-between items-center mb-1 border-b border-white/20 pb-1">
                  <label className="text-[12px] font-black text-white uppercase">Amount (INR)</label>
                  <Database size={12} className="text-white/50" />
                </div>
                <div className="flex items-center justify-center gap-2">
                  <span className="text-lg font-black text-white">₹</span>
                  <input type="number" value={headerTotal} onChange={(e) => setHeaderTotal(e.target.value)} className="bg-transparent text-xl font-black text-white outline-none w-full text-center" />
                </div>
              </div>
            </div>

            {/* GRID SECTION */}
            <div className="space-y-4">
              <div className="flex justify-between items-center border-b border-slate-300 pb-2">
                <h3 className="text-[14px] font-black uppercase tracking-widest text-slate-500 flex items-center gap-2">
                  <FileText size={16} /> Transaction Breakdown
                </h3>
                <button onClick={addNewRow} className="flex items-center gap-2 bg-black text-white px-4 py-2 text-[13px] font-black uppercase hover:bg-blue-700 transition-all rounded shadow-md">
                  <Plus size={16} /> Add Row
                </button>
              </div>

              <table className="w-full border-collapse border-2 border-black">
                <thead>
                  <tr className="bg-slate-800 text-white">
                    {/* First 3 columns set to equal width (e.g., 25% each) and 16px font */}
                    <th className="p-3 text-[16px] font-black uppercase text-left border-r border-slate-600 w-[25%]">
                      Details Code
                    </th>
                    <th className="p-3 text-[16px] font-black uppercase text-left border-r border-slate-600 w-[25%]">
                      Loan / Reference
                    </th>
                    <th className="p-3 text-[16px] font-black uppercase text-left border-r border-slate-600 w-[25%]">
                      Area
                    </th>
                    
                    {/* Remaining columns with specific or auto width */}
                    <th className="p-3 text-[16px] font-black uppercase text-right border-r border-slate-600 w-40">
                      Amount
                    </th>
                    <th className="p-3 text-[16px] font-black uppercase text-center w-24">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <React.Fragment key={row.id}>
                      <tr className="border-b border-black hover:bg-slate-50 transition-colors">
                        <td className="p-2 border-r border-black cursor-pointer" onClick={() => openSearch("Line Code Lookup")}>
                          <div className="flex justify-between items-center text-[14px] font-black text-black opacity-70 uppercase">SEARCH... <Search size={14}/></div>
                        </td>
                        <td className="p-2 border-r border-black cursor-pointer" onClick={() => openSearch("Loan Reference")}>
                          <div className="flex justify-between items-center text-[14px] font-black text-black opacity-70 uppercase">LINK... <Search size={14}/></div>
                        </td>
                        <td className="p-2 border-r border-black cursor-pointer text-center" onClick={() => openSearch("Area Selection")}>
                          <div className="text-[14px] font-black text-black opacity-70">---</div>
                        </td>
                        <td className="p-2 border-r border-black bg-slate-50/50">
                          <input type="number" value={row.amount} onChange={(e) => handleAmountChange(row.id, e.target.value)} className="w-full text-[14px] font-black text-black text-right outline-none bg-transparent" placeholder="0.00" />
                        </td>
                        <td className="p-2 text-center flex items-center justify-center gap-2">
                          <button className="text-slate-400 hover:text-amber-600 transition-colors" title="Edit Row"><Edit3 size={16} /></button>
                          <button onClick={() => removeRow(row.id)} className="text-slate-400 hover:text-rose-600 transition-colors" title="Delete Row"><Trash2 size={16} /></button>
                        </td>
                      </tr>
                      <tr className="bg-slate-100 border-b border-black">
                        <td colSpan="5" className="px-3 py-1.5">
                          <div className="flex items-center gap-2">
                            <Info size={14} className="text-blue-600" />
                            <input 
                              placeholder="ENTER NARRATION / REMARKS..." 
                              className="w-full bg-transparent text-[13px] font-black text-black outline-none uppercase placeholder:text-slate-600" 
                            />
                          </div>
                        </td>
                      </tr>
                    </React.Fragment>
                  ))}
                </tbody>
                <tfoot>
                  <tr className={`border-t-4 border-black font-black ${isMatch ? 'bg-emerald-100' : 'bg-rose-50'}`}>
                    <td colSpan="3" className="p-3 text-right text-[14px] uppercase border-r border-black">
                      Table Calculated Total:
                    </td>
                    <td className={`p-3 text-right text-[18px] border-r border-black font-mono ${isMatch ? 'text-emerald-700' : 'text-rose-600'}`}>
                      ₹ {columnTotal.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                    </td>
                    <td className="bg-white"></td>
                  </tr>
                </tfoot>
              </table>

              {!isMatch && columnTotal > 0 && (
                <div className="flex items-center justify-center gap-3 bg-rose-600 text-white p-3 rounded shadow-lg">
                  <AlertTriangle size={20} />
                  <span className="text-[14px] font-black uppercase tracking-widest">
                    Discrepancy: ₹ {Math.abs(diff).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* MODAL SEARCH Overlay */}
      {showModal && (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-white w-full max-w-lg border-4 border-black flex flex-col shadow-2xl animate-in zoom-in duration-150">
            <div className="p-4 bg-slate-100 border-b-2 border-black flex justify-between items-center">
              <h4 className="text-[14px] font-black uppercase text-black tracking-tight">Lookup: {modalTitle}</h4>
              <button onClick={() => setShowModal(false)} className="hover:scale-110 transition-transform"><X size={24} /></button>
            </div>
            <div className="p-6">
              <div className="relative mb-6">
                <input 
                  type="text" 
                  placeholder="FILTER SEARCH..." 
                  className="w-full p-3 pl-10 border-2 border-black text-[14px] font-black text-black outline-none uppercase placeholder:text-slate-600" 
                  autoFocus 
                />
                <Search size={18} className="absolute left-3 top-3.5 text-black" />
              </div>
              <div className="max-h-80 overflow-y-auto border-2 border-black rounded-sm">
                {[1, 2, 3, 4, 5].map(i => (
                  <div key={i} onClick={() => setShowModal(false)} className="p-4 border-b-2 border-slate-100 flex justify-between items-center hover:bg-[#1e3a8a] hover:text-white cursor-pointer group transition-colors">
                    <div>
                      <p className="text-[14px] font-black uppercase">Sample Account Entry 1000{i}</p>
                      <p className="text-[11px] opacity-70 font-bold group-hover:text-white/80 uppercase">Financial Master DB</p>
                    </div>
                    <Check size={20} className="opacity-0 group-hover:opacity-100" />
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ReceiptVoucher;
