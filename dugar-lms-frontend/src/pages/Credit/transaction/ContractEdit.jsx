import React, { useState } from 'react';
import { 
  ChevronLeft, User, Car, CreditCard, 
  FileSearch, ShieldCheck, CheckCircle2, 
  Edit3, Search, Plus, ListFilter, FileEdit 
} from 'lucide-react';

const ContractManager = () => {
  const [view, setView] = useState('list'); 
  const [activeIdx, setActiveIdx] = useState(0);

  const mockContracts = [
    { id: '98234', name: 'VENKATESAN M', category: 'A', status: 'High Risk', amount: '₹ 4,50,000', date: '05-Apr-2026' },
    { id: '98235', name: 'MEENAKSHI R', category: 'B', status: 'Medium Risk', amount: '₹ 12,00,000', date: '06-Apr-2026' },
    { id: '98236', name: 'AMIRTHA V', category: 'A', status: 'Low Risk', amount: '₹ 8,25,000', date: '07-Apr-2026' },
  ];

  const tabs = [
    { id: 'Customer', label: 'Customer Details', icon: <User size={14} /> },
    { id: 'Asset', label: 'Asset Details', icon: <Car size={14} /> },
    { id: 'Financial', label: 'Financial Terms', icon: <CreditCard size={14} /> },
    { id: 'Documentation', label: 'Documentation', icon: <FileSearch size={14} /> },
    { id: 'Approval', label: 'Final Approval', icon: <ShieldCheck size={14} /> },
  ];

  // --- VIEW 1: LIST VIEW ---
  if (view === 'list') {
    return (
      <div className="h-screen w-full flex flex-col bg-[#F8FAFC] overflow-hidden">
        <header className="h-14 bg-[#003366] flex items-center justify-between px-6 shrink-0 shadow-md z-20">
          <h1 className="text-white text-[12px] font-black uppercase tracking-widest italic">Dugar LMS 2.0</h1>
          <button className="bg-[#FF8C00] text-white px-4 py-1.5 rounded text-[11px] font-bold uppercase shadow-md">
            New Proposal
          </button>
        </header>

        <div className="bg-white border-b border-slate-200 px-6 py-3 flex justify-between items-center shrink-0">
          <div className="relative">
            <Search className="absolute left-3 top-2.5 text-slate-400" size={15} />
            <input className="pl-10 pr-4 py-2 w-80 border rounded-lg text-[13px] outline-none" placeholder="Search..." />
          </div>
        </div>

        {/* Scrollable Table Area */}
        <div className="flex-1 overflow-auto">
          <table className="w-full border-collapse">
            <thead className="sticky top-0 bg-slate-50 z-10">
              <tr className="text-[11px] uppercase font-black text-slate-600 border-b">
                <th className="px-6 py-4 text-left">ID</th>
                <th className="px-6 py-4 text-left">Borrower</th>
                <th className="px-6 py-4 text-center">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {mockContracts.map((item) => (
                <tr key={item.id} className="hover:bg-blue-50/50">
                  <td className="px-6 py-4 font-bold text-[#003366]">#{item.id}</td>
                  <td className="px-6 py-4 font-bold uppercase">{item.name}</td>
                  <td className="px-6 py-4 text-center">
                    <button onClick={() => setView('edit')} className="bg-slate-900 text-white px-4 py-1.5 rounded text-[10px] font-black uppercase">
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  // --- VIEW 2: EDIT VIEW ---
  return (
    <div className="h-screen w-full flex flex-col bg-white overflow-hidden font-[Calibri,sans-serif]">
      
      {/* 1. FIXED HEADER (No Scroll) */}
      <header className="h-14 bg-[#003366] flex items-center justify-between px-6 shrink-0 z-30 shadow-md">
        <div className="flex items-center gap-4">
          <button 
            onClick={() => setView('list')}
            className="flex items-center gap-2 bg-white/10 hover:bg-white/20 px-3 py-1.5 rounded transition-all border border-white/20"
          >
            <ChevronLeft size={16} className="text-white" />
            <span className="text-white text-[11px] font-bold uppercase">Back to List</span>
          </button>
          <div className="flex items-center gap-3 text-white">
            <span className="text-sm font-bold tracking-tight">TEMP #98234</span>
            <span className="px-2 py-0.5 rounded text-[10px] font-black uppercase bg-red-500">High Risk</span>
          </div>
        </div>
        
        <div className="flex items-center gap-2">
          <button className="bg-[#FF8C00] text-white px-5 py-1.5 rounded text-[11px] font-bold uppercase shadow-lg active:scale-95 flex items-center gap-2">
            <CheckCircle2 size={13} strokeWidth={3} /> Submit & Active
          </button>
        </div>
      </header>

      {/* 2. FIXED TABS (No Scroll) */}
      <nav className="h-12 bg-[#F1F5F9] flex items-center px-2 shrink-0 z-20 border-b border-slate-300">
        <div className="flex items-center h-full">
          {tabs.map((tab, index) => {
            const isActive = activeIdx === index;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveIdx(index)}
                className={`px-8 h-full flex items-center gap-3 transition-all relative shrink-0
                  ${isActive ? 'bg-white shadow-sm' : 'opacity-60 hover:opacity-100'}`}
              >
                <span className={isActive ? 'text-[#FF8C00]' : 'text-slate-600'}>{tab.icon}</span>
                <span className={`text-[11px] font-black uppercase tracking-wider ${isActive ? 'text-[#003366]' : ''}`}>
                  {tab.label}
                </span>
                {isActive && <div className="absolute bottom-0 left-0 w-full h-[4px] bg-[#FF8C00] z-20"></div>}
              </button>
            );
          })}
        </div>
      </nav>

      {/* 3. SCROLLABLE AREA (This is the only part that scrolls) */}
      <main className="flex-1 overflow-y-auto bg-white">
        <div className="p-10 max-w-[1600px] mx-auto">
          
          <div className="flex items-center gap-3 border-b-2 border-slate-100 pb-5 mb-10">
             <div className="w-2 h-8 bg-[#FF8C00] rounded-sm"></div>
             <h2 className="text-2xl font-black text-[#003366] uppercase tracking-tight">{tabs[activeIdx].label}</h2>
          </div>

          {/* Form Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-x-12 gap-y-8">
            {[
              "Borrower Name", "Constitution", "Mobile Number", "Email Address", 
              "Date of Birth", "PAN Card No", "Aadhar No", "Monthly Income",
              "Occupation", "Company Name", "Experience (Yrs)", "City",
              "State", "Pincode", "Nationality", "Resident Status"
            ].map((label, i) => (
              <div key={i} className="flex flex-col gap-2 group">
                <label className="text-[10px] font-black text-slate-400 uppercase tracking-[1px] group-focus-within:text-[#FF8C00]">
                  {label}
                </label>
                <input 
                  type="text" 
                  className="h-11 px-4 border-b-2 border-slate-200 bg-slate-50/50 text-[13px] font-bold text-slate-800 outline-none focus:border-[#FF8C00] transition-all uppercase"
                  placeholder="..."
                />
              </div>
            ))}
          </div>

          {/* This long div ensures we can test the internal scrollbar */}
          <div className="mt-12 h-[800px] bg-slate-50 border-2 border-dashed border-slate-200 rounded-3xl flex items-center justify-center">
             <p className="text-slate-300 font-black uppercase tracking-widest">Tab Content Area (Scroll test)</p>
          </div>

        </div>
      </main>
    </div>
  );
};

export default ContractManager;