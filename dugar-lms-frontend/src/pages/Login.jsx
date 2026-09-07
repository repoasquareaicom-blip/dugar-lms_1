import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, User, Eye, EyeOff, Loader2, ShieldCheck, ChevronRight, Activity, UserCheck, Fingerprint, Database, Cpu, Terminal } from 'lucide-react';
import { motion as Motion, AnimatePresence } from 'framer-motion';
import apiClient, { saveCleanAuthToken } from '../api/apiClient';
import { normalizeMenuTree } from '../utils/menuNormalizer';

const Login = () => {
    const lmsName = import.meta.env.VITE_LMS_NAME || 'PROLMS';
    const [isSplash, setIsSplash] = useState(true);
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const navigate = useNavigate();

    // Splash timeout set to 5.5s to allow all status bullets to "load"
    useEffect(() => {
        const timer = setTimeout(() => setIsSplash(false), 5500);
        return () => clearTimeout(timer);
    }, []);

    const triggerFullScreen = () => {
        const elem = document.documentElement;
        const requestMethod = elem.requestFullscreen || elem.webkitRequestFullscreen || elem.mozRequestFullScreen || elem.msRequestFullscreen;
        if (requestMethod) {
            requestMethod.call(elem).catch(() => console.warn("Fullscreen blocked"));
        }
    };

    const handleLogin = async (e) => {
        e.preventDefault();
        triggerFullScreen();
        setLoading(true);
        setError('');

        try {
            const response = await apiClient.post('/auth/login', { username, password });
            if (response.data?.token) {
                const menuTree = normalizeMenuTree(response.data.menus || []);
                const token = saveCleanAuthToken(response.data.token);
                localStorage.setItem('user', JSON.stringify({
                    ...response.data.user,
                    token,
                    menus: response.data.menus || [],
                    menuTree,
                }));
                setTimeout(() => navigate('/dashboard'), 300);
            } else {
                setError(response.data?.message || 'Login failed. Token not received.');
            }
        } catch (err) {
            if (err.response?.status === 401) {
                setError("Access Denied: Invalid Credentials");
            } else if (err.response?.data?.message) {
                setError(`API Error: ${err.response.data.message}`);
            } else if (err.code === 'ERR_NETWORK') {
                setError('Network Error: API Gateway Unreachable');
            } else {
                setError('Login failed. Please try again.');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-[#020617] flex items-center justify-center overflow-hidden font-sans select-none selection:bg-cyan-500/30">
            <AnimatePresence mode="wait">
                {isSplash ? (
                    <Motion.div 
                        key="splash"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0, scale: 1.05, filter: "blur(15px)" }}
                        transition={{ duration: 1 }}
                        className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-[#020617]"
                    >
                        {/* Background Scanline Effect */}
                        <div className="absolute inset-0 opacity-[0.05] pointer-events-none bg-[linear-gradient(rgba(18,16,16,0)_50%,rgba(0,0,0,0.25)_50%),linear-gradient(90deg,rgba(255,0,0,0.06),rgba(0,255,0,0.02),rgba(0,0,255,0.06))] z-10 bg-[length:100%_2px,3px_100%]" />
                        
                        <div className="relative z-20 flex flex-col items-center text-center">
                            {/* Brand Header */}
                            <Motion.div
                                initial={{ y: -20, opacity: 0 }}
                                animate={{ y: 0, opacity: 1 }}
                                transition={{ duration: 1 }}
                                className="mb-16"
                            >
                                <div className="text-5xl font-black tracking-[0.18em] uppercase text-transparent bg-clip-text bg-gradient-to-r from-cyan-200 via-white to-emerald-300 drop-shadow-[0_0_24px_rgba(34,211,238,0.45)]">
                                      {lmsName}
                                </div>
                                <div className="h-[1px] w-full bg-gradient-to-r from-transparent via-emerald-400/60 to-transparent mt-4" />
                            </Motion.div>

                            {/* Technical Status Bullets - Cyan & Bold */}
                            <div className="space-y-5 text-left w-full max-w-md px-6">
                                {[
                                    { icon: <Terminal size={18}/>, text: "Loan Edge 2.0.0 initializing...", delay: 0.8 },
                                    { icon: <Cpu size={18}/>, text: "Spring Boot Server: STATUS_OK", delay: 1.8 },
                                    { icon: <Database size={18}/>, text: "PostgreSQL Connection: ACTIVE", delay: 2.8 },
                                    { icon: <ShieldCheck size={18}/>, text: "Security Environment: VERIFIED", delay: 3.8 }
                                ].map((item, index) => (
                                    <Motion.div 
                                        key={index}
                                        initial={{ opacity: 0, x: -20 }}
                                        animate={{ opacity: 1, x: 0 }}
                                        transition={{ delay: item.delay, duration: 0.5 }}
                                        className="flex items-center gap-5 text-cyan-400 font-mono text-[14px] uppercase tracking-widest"
                                    >
                                        <Motion.span 
                                            animate={{ opacity: [1, 0.5, 1] }}
                                            transition={{ repeat: Infinity, duration: 2, delay: item.delay }}
                                            className="text-cyan-300 drop-shadow-[0_0_8px_rgba(34,211,238,0.8)]"
                                        >
                                            {item.icon}
                                        </Motion.span>
                                        <span className="font-bold drop-shadow-[0_0_5px_rgba(34,211,238,0.3)]">
                                            {item.text}
                                        </span>
                                    </Motion.div>
                                ))}
                            </div>

                            {/* Progress Bar */}
                            <div className="w-64 h-[2px] bg-white/5 mt-16 overflow-hidden rounded-full">
                                <Motion.div 
                                    initial={{ width: 0 }}
                                    animate={{ width: "100%" }}
                                    transition={{ duration: 4.5, ease: "easeInOut", delay: 0.5 }}
                                    className="h-full bg-cyan-500 shadow-[0_0_15px_rgba(6,182,212,0.8)]"
                                />
                            </div>
                        </div>
                    </Motion.div>
                ) : (
                    <Motion.div 
                        key="login"
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        className="w-full max-w-[850px] flex h-[520px] bg-white rounded-[2.5rem] shadow-[0_50px_100px_-30px_rgba(0,0,0,0.6)] overflow-hidden m-4 border border-white/10"
                    >
                        {/* Side Branding */}
                        <div className="hidden md:flex w-[38%] bg-[#0f172a] p-10 flex-col justify-between relative overflow-hidden">
                            <div className="absolute top-0 right-0 w-64 h-64 bg-cyan-600/10 blur-[80px] -mr-32 -mt-32" />
                            
                            <div className="relative z-10">
                                <div className="mb-8 text-3xl font-black uppercase text-transparent bg-clip-text bg-gradient-to-r from-cyan-300 via-white to-emerald-300">
                                      {lmsName}
                                </div>
                                <p className="text-[10px] font-black text-cyan-500 uppercase tracking-[0.3em] mb-8">Access Portal</p>
                                <h2 className="text-4xl font-light text-white leading-tight">
                                    System<br /><span className="font-bold text-cyan-400">Authorization</span>
                                </h2>
                            </div>

                            <div className="relative z-10">
                                <p className="text-[9px] text-slate-500 font-medium uppercase tracking-[0.2em] ml-1">Copyright 2026 asquareai.com</p>
                            </div>
                        </div>

                        {/* Login Form Section */}
                        <div className="flex-1 p-10 lg:p-14 flex flex-col justify-center bg-white">
                            <div className="mb-8 self-start text-2xl font-black uppercase text-transparent bg-clip-text bg-gradient-to-r from-slate-900 via-cyan-700 to-emerald-600 md:hidden">
                                  {lmsName}
                            </div>
                            <div className="mb-10 flex items-center justify-between">
                                <div>
                                    <h3 className="text-2xl font-black text-slate-900 tracking-tight">Login</h3>
                                    <div className="h-1 w-8 bg-cyan-500 rounded-full mt-2" />
                                </div>
                                <UserCheck className="text-cyan-500/20" size={32} strokeWidth={1.5} />
                            </div>

                            {error && (
                                <Motion.div 
                                    initial={{ opacity: 0, x: -5 }} 
                                    animate={{ opacity: 1, x: 0 }}
                                    className="mb-6 p-3 bg-red-50 border-l-2 border-red-500 text-red-600 text-[11px] font-bold rounded flex items-center gap-2"
                                >
                                    <Activity size={14} /> {error}
                                </Motion.div>
                            )}

                            <form onSubmit={handleLogin} className="space-y-5">
                                <div className="space-y-1.5 group">
                                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-1">Identity ID</label>
                                    <div className="relative">
                                        <User className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-cyan-600 transition-colors" size={18} />
                                        <input 
                                            type="text" 
                                            className="w-full pl-12 pr-4 py-3.5 bg-slate-50 border border-slate-100 rounded-xl focus:border-cyan-500 focus:bg-white focus:ring-4 focus:ring-cyan-500/5 outline-none transition-all text-sm font-bold text-slate-700 placeholder:text-slate-300"
                                            placeholder="Enter ID"
                                            value={username}
                                            onChange={(e) => setUsername(e.target.value)}
                                            required
                                        />
                                    </div>
                                </div>

                                <div className="space-y-1.5 group">
                                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-1">Passkey</label>
                                    <div className="relative">
                                        <Lock className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-cyan-600 transition-colors" size={18} />
                                        <input 
                                            type={showPassword ? "text" : "password"} 
                                            autoComplete="new-password"
                                            className="w-full pl-12 pr-12 py-3.5 bg-slate-50 border border-slate-100 rounded-xl focus:border-cyan-500 focus:bg-white focus:ring-4 focus:ring-cyan-500/5 outline-none transition-all text-sm font-bold text-slate-700"
                                            placeholder="••••••••"
                                            value={password}
                                            onChange={(e) => setPassword(e.target.value)}
                                            required
                                        />
                                        <button 
                                            type="button"
                                            onClick={() => setShowPassword(!showPassword)}
                                            className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-300 hover:text-slate-600 transition-colors"
                                        >
                                            {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                                        </button>
                                    </div>
                                </div>

                                <button 
                                    type="submit" 
                                    disabled={loading}
                                    className="w-full bg-[#0f172a] hover:bg-cyan-600 text-white font-black text-[11px] uppercase tracking-widest py-4 rounded-xl shadow-xl shadow-slate-900/10 transition-all flex items-center justify-center gap-3 active:scale-[0.98] disabled:opacity-70 mt-6"
                                >
                                    {loading ? <Loader2 className="animate-spin" size={18} /> : (
                                        <>
                                            <span>Initialize Session</span>
                                            <ChevronRight size={16} />
                                        </>
                                    )}
                                </button>
                            </form>
                            
                            <p className="mt-10 text-[10px] text-center text-slate-400 font-bold uppercase tracking-[0.2em]">
                                Secured Terminal v2.0.0
                            </p>
                        </div>
                    </Motion.div>
                )}
            </AnimatePresence>
        </div>
    );
};

export default Login;
