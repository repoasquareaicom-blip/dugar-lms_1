import React, { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { ChevronRight, ChevronLeft, LayoutDashboard, ShieldCheck } from 'lucide-react';
import TopNavbar from '../components/TopNavbar';
import Footer from '../components/Footer';
import { normalizeMenuTree } from '../utils/menuNormalizer';

const MainDashboardLayout = () => {
  const [isPropBoxOpen, setIsPropBoxOpen] = useState(false);
  const [isInitialized, setIsInitialized] = useState(false);
  const location = useLocation();
  
  const savedUser = localStorage.getItem('user');
  const userData = savedUser ? JSON.parse(savedUser) : null;
  const menuTree = normalizeMenuTree(userData?.menus?.length ? userData.menus : userData?.menuTree || []);

  // Function to fix the Fullscreen issue
  const initializeWorkspace = () => {
    const elem = document.documentElement;
    if (elem.requestFullscreen) {
      elem.requestFullscreen().catch(() => {});
    } else if (elem.webkitRequestFullscreen) {
      elem.webkitRequestFullscreen();
    }
    setIsInitialized(true);
  };

  // Logic: Hide sidebar for the massive 80-field contract forms
  const isFullScreenForm = 
    location.pathname.includes('/contract/form') || 
    location.pathname.includes('/contract/edit');

  return (
    <div className="h-dvh bg-[#F0F2F5] flex flex-col overflow-hidden relative font-sans">
      
      {/* 1. FULLSCREEN OVERLAY (Prevents the "Immediate Normal Mode" issue) */}
      {!isInitialized && (
        <div className="fixed inset-0 z-[9999] bg-[#1e3a8a] flex items-center justify-center p-6">
          <div className="bg-white p-10 rounded-[2.5rem] shadow-2xl text-center max-w-sm w-full border border-white">
            <div className="w-20 h-20 bg-blue-50 rounded-2xl flex items-center justify-center mx-auto mb-6">
              <ShieldCheck size={44} className="text-[#1e3a8a]" />
            </div>
            <h2 className="text-2xl font-black text-slate-900 mb-2">Workspace Ready</h2>
            <p className="text-slate-500 text-sm mb-8 font-medium leading-relaxed">
              Click to initialize the Dugar LMS terminal and activate F11 Fullscreen mode.
            </p>
            <button 
              onClick={initializeWorkspace}
              className="w-full bg-[#1e3a8a] hover:bg-black text-white py-4 rounded-xl font-black uppercase tracking-widest transition-all shadow-lg active:scale-95"
            >
              Initialize Session
            </button>
          </div>
        </div>
      )}

      {/* 2. TOP NAVBAR */}
      <TopNavbar menuTree={menuTree} userData={userData} />

      <div className="flex flex-1 min-h-0 overflow-hidden relative">
        
        {/* 3. MAIN CONTENT AREA */}
        <main className={`flex-1 min-h-0 overflow-hidden transition-all bg-[#F0F2F5] ${isFullScreenForm ? 'p-0' : 'p-3'}`}>
          <div className={`${isFullScreenForm ? '' : 'bg-white rounded-xl border border-slate-300 shadow-sm'} h-full min-h-0 overflow-hidden p-3`}>
            {/* This is where your pages like PartyCodeModify will appear */}
            <Outlet /> 
          </div>
        </main>

        {/* 4. SIDEBAR (Hidden on full screen forms) */}
        {!isFullScreenForm && (
          <aside className={`bg-white border-l transition-all duration-500 overflow-hidden shrink-0 ${isPropBoxOpen ? 'w-80' : 'w-0'}`}>
            <div className="w-80 p-6">
              <h3 className="font-black text-slate-900 text-[11px] uppercase tracking-widest flex items-center gap-2 mb-6">
                <LayoutDashboard size={14} className="text-[#1e3a8a]" />
                Module Intelligence
              </h3>
              <div className="bg-blue-50 p-4 rounded-lg border border-blue-100">
                <p className="text-xs font-bold text-blue-600 uppercase mb-1">Status</p>
                <p className="text-xl font-black text-slate-900">System Live</p>
              </div>
            </div>
          </aside>
        )}

        {/* 5. SIDEBAR TOGGLE */}
        {!isFullScreenForm && (
          <button 
            onClick={() => setIsPropBoxOpen(!isPropBoxOpen)}
            className={`absolute right-0 top-1/2 -translate-y-1/2 z-50 bg-white border border-slate-300 shadow-md p-1.5 rounded-l-md transition-all ${isPropBoxOpen ? 'mr-80' : 'mr-0'}`}
          >
            {isPropBoxOpen ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
          </button>
        )}
      </div>

      <Footer />
    </div>
  );
};

export default MainDashboardLayout;
