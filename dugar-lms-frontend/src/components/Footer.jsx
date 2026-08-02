import React from 'react';
import { useEffect, useState } from 'react';
import { Clock, Maximize2, Minimize2, Minus, Plus, Power, Type } from 'lucide-react';

const MIN_UI_SCALE = 0.9;
const MAX_UI_SCALE = 1.15;
const SCALE_STEP = 0.05;

function applyUiScale(scale) {
  document.documentElement.style.setProperty('--dugar-ui-scale', String(scale));
}

const Shortcut = ({ label, keyCombo }) => (
  <div className="flex items-center gap-1.5 px-2 py-0.5 bg-white/10 border border-white/20 rounded">
    <span className="text-[9px] font-bold text-white uppercase tracking-tighter">{label}</span>
    <kbd className="px-1 rounded bg-white/20 text-white text-[9px] font-bold border border-white/30">
      {keyCombo}
    </kbd>
  </div>
);

const StatusIconButton = ({ children, disabled = false, label, onClick }) => (
  <button
    type="button"
    onClick={onClick}
    disabled={disabled}
    title={label}
    aria-label={label}
    className="h-5 w-5 inline-flex items-center justify-center rounded border border-white/20 bg-white/10 text-white transition-all hover:bg-white/20 disabled:cursor-not-allowed disabled:opacity-40"
  >
    {children}
  </button>
);

const Footer = ({ userData }) => {
  const [isFullscreen, setIsFullscreen] = useState(Boolean(document.fullscreenElement));
  const [uiScale, setUiScale] = useState(() => {
    const storedScale = Number(localStorage.getItem('dugarUiScale') || 1);
    return Number.isFinite(storedScale) ? Math.min(Math.max(storedScale, MIN_UI_SCALE), MAX_UI_SCALE) : 1;
  });

  useEffect(() => {
    applyUiScale(uiScale);
    localStorage.setItem('dugarUiScale', String(uiScale));
  }, [uiScale]);

  useEffect(() => {
    const handleFullscreenChange = () => setIsFullscreen(Boolean(document.fullscreenElement));

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, []);

  const changeUiScale = (direction) => {
    setUiScale((current) => {
      const next = Number((current + direction * SCALE_STEP).toFixed(2));
      return Math.min(Math.max(next, MIN_UI_SCALE), MAX_UI_SCALE);
    });
  };

  const toggleFullscreen = async () => {
    if (!document.fullscreenElement) {
      await document.documentElement.requestFullscreen();
      return;
    }

    await document.exitFullscreen();
  };

  const handleLogout = () => {
    localStorage.clear();
    window.location.href = '/login';
  };

  return (
    <footer className="w-full shrink-0 bg-gradient-to-b from-[#003B94] to-[#002B6B] border-t border-white/10 py-1 px-4 z-50 shadow-[0_-4px_10px_rgba(0,0,0,0.2)]">
      <div className="w-full flex items-center justify-between gap-3 overflow-hidden">
        
        {/* LEFT: Shortcuts - Text changed to White */}
        <div className="flex shrink-0 items-center gap-3">
          <p className="text-[9px] text-white font-extrabold uppercase tracking-[0.15em] border-r border-white/20 pr-4">
            Command Center
          </p>
          <div className="flex items-center gap-2">
            <Shortcut label="New" keyCombo="Alt+N" />
            <Shortcut label="Save" keyCombo="Alt+S" />
            <Shortcut label="Search" keyCombo="Alt+F" />
          </div>
        </div>

        {/* RIGHT: Status, Login Info & Logout */}
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex shrink-0 items-center gap-1.5 border-r border-white/10 pr-3">
            <StatusIconButton label={isFullscreen ? 'Back to normal' : 'Full screen'} onClick={toggleFullscreen}>
              {isFullscreen ? <Minimize2 size={12} strokeWidth={3} /> : <Maximize2 size={12} strokeWidth={3} />}
            </StatusIconButton>
            <span className="mx-1 inline-flex items-center text-white/80">
              <Type size={12} strokeWidth={3} />
            </span>
            <StatusIconButton
              label="Decrease font size"
              onClick={() => changeUiScale(-1)}
              disabled={uiScale <= MIN_UI_SCALE}
            >
              <Minus size={12} strokeWidth={3} />
            </StatusIconButton>
            <span className="w-8 text-center text-[9px] font-black text-white tabular-nums">
              {Math.round(uiScale * 100)}
            </span>
            <StatusIconButton
              label="Increase font size"
              onClick={() => changeUiScale(1)}
              disabled={uiScale >= MAX_UI_SCALE}
            >
              <Plus size={12} strokeWidth={3} />
            </StatusIconButton>
          </div>
          
          {/* Last Login - Changed text to White */}
          <div className="hidden xl:flex items-center gap-2 px-3 py-0.5 bg-white/10 border border-white/10 rounded">
            <Clock size={10} className="text-white" />
            <span className="text-[9px] font-bold text-white uppercase tracking-tight">
              Last Login: <span className="font-black">{userData?.lastLogin || "10-Apr-2026 | 10:15"}</span>
            </span>
          </div>

          {/* Status Indicator */}
          <div className="flex shrink-0 items-center gap-2 px-2.5 py-0.5 bg-black/30 border border-white/10 rounded-full">
            <div className="relative flex h-1.5 w-1.5">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-40"></span>
              <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-green-500 shadow-[0_0_4px_rgba(34,197,94,0.8)]"></span>
            </div>
            <span className="text-[9px] font-bold text-white uppercase tracking-widest">Live</span>
          </div>

          {/* Versioning & Brand */}
          <div className="hidden lg:flex items-center gap-2 border-r border-white/10 pr-3">
            <span className="text-[9px] font-bold text-blue-200 uppercase tracking-tighter">v2.0.0</span>
            <span className="h-2 w-[1px] bg-white/10" />
            <span className="text-[9px] font-medium text-white uppercase tracking-[0.2em]">
              Dugar <span className="font-black">Edge</span>
            </span>
          </div>

          {/* Logout Button - Default Red Background */}
          <button 
            onClick={handleLogout}
            className="flex items-center gap-2 px-3 py-1 bg-red-600 hover:bg-red-700 text-white border border-red-500 rounded shadow-sm transition-all duration-200 group"
          >
            <Power size={11} strokeWidth={3} className="group-hover:scale-110 transition-transform" />
            <span className="text-[9px] font-black uppercase tracking-widest">End Session</span>
          </button>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
