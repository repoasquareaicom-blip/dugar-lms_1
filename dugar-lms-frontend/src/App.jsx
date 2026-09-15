import React, { useEffect, useMemo, useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { 
  ChevronRight, ChevronLeft, LayoutDashboard, Bell, AlertCircle, 
  CheckSquare, Wallet, TrendingUp, CircleDot, Zap, ArrowUpRight,
  Clock, History, ShieldCheck, Activity, MessageSquare, ClipboardCheck, 
  HandCoins, HelpCircle, Users, User
} from 'lucide-react';

// Import Recharts
import { 
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, 
  CartesianGrid, AreaChart, Area
} from 'recharts';
import * as Icons from 'lucide-react';
// Components
import Login from './pages/Login';
import TopNavbar from './components/TopNavbar';
import Footer from './components/Footer';
import PartyCodeModify from './pages/Credit/Masters/PartyCodeModify';
import ContractGrid from './pages/credit/transaction/contract/ContractGrid';
import ContractEditForm from './pages/credit/transaction/contract/ContractEditForm.jsx';
import LedgerCodeMaster from './pages/accounts/masters/LedgerCodeMaster';
import ReceiptVoucher from './pages/accounts/transactions/ReceiptVoucher';
import VoucherAuthorisation from './pages/accounts/transactions/VoucherAuthorisation';
import Ratios from './pages/accounts/reports/ratios.jsx';
import DemandListPage from './pages/committee/reports/DemandListPage.jsx';
import AfcReportPage from './pages/committee/reports/AfcReportPage.jsx';
import AgingAnalysisPage from './pages/committee/reports/AgingAnalysisPage.jsx';
import AgingMatrixReportPage from './pages/committee/reports/AgingMatrixReportPage.jsx';
import ConsolidatedPortfolioPage from './pages/committee/reports/ConsolidatedPortfolioPage.jsx';
import RolesMaster from './pages/committee/masters/RolesMaster.jsx';
import RoleMenuPermissions from './pages/committee/masters/RoleMenuPermissions.jsx';
import UserManagement from './pages/committee/masters/UserManagement.jsx';
import { normalizeMenuTree } from './utils/menuNormalizer';
import { fetchContractDashboard } from './services/dashboardService';

const ProtectedRoute = ({ children }) => {
  const user = localStorage.getItem('user');
  const token = localStorage.getItem('token');
  if (!user || !token) return <Navigate to="/login" replace />;
  return children;
};

// 1. THE ENHANCED DUAL-PANE SHELL
const MainDashboardLayout = () => {
  const [isPropBoxOpen, setIsPropBoxOpen] = useState(false);
  const savedUser = localStorage.getItem('user');
  const userData = savedUser ? JSON.parse(savedUser) : null;
  const menuTree = normalizeMenuTree(userData?.menus?.length ? userData.menus : userData?.menuTree || []);
  const location = useLocation();
  const navigate = useNavigate();

  const isFullScreenForm = location.pathname.includes('/contract/form');

  const calibriBlackStyle = { 
    fontFamily: 'Calibri, Candara, Segoe, "Segoe UI", Optima, Arial, sans-serif',
    color: '#000000' 
  };

  return (
    <div style={calibriBlackStyle} className="dugar-global-scale h-full min-h-0 bg-[#F0F2F5] flex flex-col overflow-hidden">
      {!isFullScreenForm && (
          <TopNavbar menuTree={menuTree} userData={userData} />
      )}

      {isFullScreenForm && (
        <div className="bg-white border-b border-black/60 px-4 py-2 flex items-center shadow-sm z-[110]">
          <button 
            onClick={() => navigate('/dashboard')}
            className="flex items-center gap-2 text-[14px] font-bold text-blue-900 hover:text-blue-700 transition-colors uppercase tracking-tight"
          >
            <ChevronLeft size={18} strokeWidth={3} /> Back to Dashboard
          </button>
          <div className="h-4 w-[1px] bg-black/20 mx-4"></div>
          <span className="text-[12px] font-bold text-black/40 uppercase tracking-widest">
            Focused Entry Mode
          </span>
        </div>
      )}

      <div className="flex flex-1 min-h-0 overflow-hidden relative">
        <main className="flex-1 min-h-0 overflow-hidden transition-all duration-500 ease-in-out bg-[#F0F2F5]">
          <div className={`w-full h-full min-h-0 ${isFullScreenForm ? 'p-0' : 'p-3'}`}>
            <div className={`${isFullScreenForm ? 'bg-transparent' : 'p-3 bg-white rounded-2xl border border-gray-300 shadow-xl'} h-full min-h-0 overflow-hidden`}>
              <Outlet /> 
            </div>
          </div>
        </main>

        {!isFullScreenForm && (
          <>
            <button 
              onClick={() => setIsPropBoxOpen(!isPropBoxOpen)}
              className={`absolute right-0 top-1/2 -translate-y-1/2 z-50 bg-white border border-gray-300 shadow-lg p-2 rounded-l-xl hover:bg-blue-50 text-black transition-all duration-300 ${
                isPropBoxOpen ? 'mr-80' : 'mr-0'
              }`}
            >
              {isPropBoxOpen ? <ChevronRight size={20} strokeWidth={3} /> : <ChevronLeft size={20} strokeWidth={3} />}
            </button>

            <aside 
              className={`bg-white border-l border-gray-300 flex flex-col transition-all duration-500 ease-in-out overflow-hidden shrink-0 ${
                isPropBoxOpen ? 'w-80 opacity-100' : 'w-0 opacity-0 pointer-events-none'
              }`}
            >
              <div className="w-80 flex flex-col h-full bg-gray-50/50" style={{ fontFamily: 'Calibri, sans-serif' }}>
                <div className="p-6 border-b border-gray-300 flex items-center justify-between bg-white sticky top-0 z-10">
                  <h3 className="font-bold text-black text-[14px] uppercase tracking-wider flex items-center gap-2">
                    <Zap size={16} className="text-[#0052CC]" />
                    Module Intelligence
                  </h3>
                  <Activity size={16} className="text-emerald-600 animate-pulse" />
                </div>
                
                <div className="p-5 space-y-8 overflow-y-auto custom-scrollbar">
                  <section>
                    <p className="text-[12px] font-bold text-black uppercase tracking-widest mb-4 flex items-center gap-2">
                      <ClipboardCheck size={14} className="text-[#0052CC]" /> Operational Backlog
                    </p>
                    <div className="grid grid-cols-2 gap-3">
                      <div className="p-4 bg-white rounded-xl border border-gray-300 shadow-sm hover:border-blue-400 transition-colors">
                        <p className="text-2xl font-bold text-black leading-none">14</p>
                        <p className="text-[11px] font-bold text-black uppercase mt-2 leading-tight">Vouchers for Auth</p>
                      </div>
                      <div className="p-4 bg-white rounded-xl border border-gray-300 shadow-sm hover:border-blue-400 transition-colors">
                        <p className="text-2xl font-bold text-black leading-none">08</p>
                        <p className="text-[11px] font-bold text-black uppercase mt-2 leading-tight">Pending Disb.</p>
                      </div>
                      <div className="p-4 bg-white rounded-xl border border-gray-300 shadow-sm hover:border-rose-400 transition-colors">
                        <p className="text-2xl font-bold text-black">03</p>
                        <p className="text-[11px] font-bold text-black uppercase mt-2 leading-tight">Grievances</p>
                      </div>
                      <div className="p-4 bg-white rounded-xl border border-gray-300 shadow-sm hover:border-amber-400 transition-colors">
                        <p className="text-2xl font-bold text-black">05</p>
                        <p className="text-[11px] font-bold text-black uppercase mt-2 leading-tight">Dept. Requests</p>
                      </div>
                    </div>
                  </section>

                  <section>
                    <p className="text-[12px] font-black text-black uppercase tracking-widest mb-4 flex items-center gap-2">
                      <CheckSquare size={14} className="text-[#7C3AED]" /> Task Management
                    </p>
                    <div className="space-y-3">
                      {[
                        { task: "Complete KYC Audit for case 14235", priority: "High Priority" },
                        { task: "Collect bank statement for case 14531", priority: "Medium Priority" },
                        { task: "Update contact details for case 14600", priority: "Normal Priority" }
                      ].map((item, i) => {
                        const getPriorityStyles = (p) => {
                          if (p === 'High Priority') return 'bg-rose-600 text-white border-rose-700';
                          if (p === 'Medium Priority') return 'bg-[#0052CC] text-white border-[#003D99]';
                          return 'bg-[#059669] text-white border-emerald-700';
                        };

                        return (
                          <div key={i} className="p-3 bg-white border border-gray-300 rounded-lg hover:shadow-md transition-shadow group">
                            <div className="flex justify-between items-center mb-2">
                              <span className={`text-[10px] font-black px-2 py-0.5 rounded border uppercase tracking-wider ${getPriorityStyles(item.priority)}`}>
                                {item.priority}
                              </span>
                              <div className="flex bg-slate-100 p-0.5 rounded border border-slate-200">
                                <button className="px-2 py-0.5 text-[10px] font-black uppercase rounded-sm bg-white text-blue-700 shadow-sm border border-slate-200">
                                  Received
                                </button>
                                <button className="px-2 py-0.5 text-[10px] font-black uppercase rounded-sm text-slate-500 hover:text-black transition-colors">
                                  Sent
                                </button>
                              </div>
                            </div>
                            <p className="text-[13px] font-bold text-black leading-tight">
                              {item.task}
                            </p>
                          </div>
                        );
                      })}
                    </div>
                  </section>
                </div>
              </div>
            </aside>
          </>
        )}
      </div>
      <div className="shrink-0">
        <Footer userData={userData} />
      </div>
    </div>
  );
};

// 2. WELCOME DASHBOARD
const WelcomeDashboard = () => {
  const currentYear = new Date().getFullYear();
  const [dashboardData, setDashboardData] = useState({ branchData: [], disbursementData: [] });
  const [dashboardError, setDashboardError] = useState('');
  const [branchLimit, setBranchLimit] = useState(10);
  const [disbursementPeriod, setDisbursementPeriod] = useState('YTD');
  const [accountYear, setAccountYear] = useState(currentYear);
  const branchData = dashboardData.branchData || [];
  const availableYears = useMemo(() => {
    const years = Array.isArray(dashboardData.availableYears) ? dashboardData.availableYears : [];
    return Array.from(new Set([currentYear, ...years])).sort((a, b) => b - a);
  }, [currentYear, dashboardData.availableYears]);
  const limitBranchRows = (rows, sortField) => (
    [...rows]
      .sort((a, b) => Number(b[sortField] || 0) - Number(a[sortField] || 0))
      .slice(0, branchLimit === 'ALL' ? rows.length : Number(branchLimit))
  );
  const activeLoansChartData = useMemo(() => limitBranchRows(branchData, 'loans'), [branchData, branchLimit]);
  const aumChartData = useMemo(() => (
    limitBranchRows(branchData, 'aum')
  ), [branchData, branchLimit]);
  const disbursementData = dashboardData.disbursementData || [];
  const activeLoanCount = Number(dashboardData.totalActiveContracts || 0);
  const totalAumLakhs = Number(dashboardData.totalAum || 0);
  const disbursementLakhs = useMemo(
    () => disbursementData.reduce((total, item) => total + Number(item.v || 0), 0),
    [disbursementData],
  );
  const formatCrores = (lakhs) => (Number(lakhs || 0) / 100).toLocaleString('en-IN', { maximumFractionDigits: 2 });
  const disbursementLabel = disbursementPeriod === 'MTD'
    ? `Contract Value MTD ${accountYear} - Day Wise`
    : disbursementPeriod === 'ALL'
      ? 'Contract Value All - Year Wise'
      : `Contract Value YTD ${accountYear} - Month Wise`;

  useEffect(() => {
    let active = true;
    fetchContractDashboard({ disbursementPeriod, accountYear })
      .then((data) => {
        if (!active) return;
        const years = Array.isArray(data.availableYears) ? data.availableYears : [];
        setDashboardData({
          totalActiveContracts: data.totalActiveContracts || 0,
          totalAum: data.totalAum || 0,
          accountYear: data.accountYear || accountYear,
          availableYears: years,
          branchData: Array.isArray(data.branchData) ? data.branchData : [],
          disbursementData: Array.isArray(data.disbursementData) ? data.disbursementData : [],
        });
        setDashboardError('');
      })
      .catch(() => {
        if (active) setDashboardError('Unable to load active contract graphs.');
      });

    return () => {
      active = false;
    };
  }, [disbursementPeriod, accountYear]);
  
const metrics = [
  { 
    label: '2.45%', 
    count: 'NPA', 
    icon: <AlertCircle size={18} />, 
    // bg-[#0052CC]/15 creates a light blue tint
    bg: 'bg-[#0052CC]/65 border-[#0052CC]/30 text-[#0052CC]', 
    trend: '-0.2%' 
  },
  { 
    label: '42', 
    count: 'Litigation Cases', 
    icon: <CheckSquare size={18} />, 
    // bg-[#D97706]/15 creates a light amber tint
    bg: 'bg-red-600/45 border-red-600/30 text-red-600',
    trend: '+2' 
  },
  { 
    label: '18', 
    count: 'Repo Inventory', 
    icon: <Wallet size={18} />, 
    // bg-[#059669]/15 creates a light emerald tint
    bg: 'bg-[#059669]/65 border-[#059669]/30 text-[#059669]', 
    trend: 'Stable' 
  },
  { 
    label: '14.8%', 
    count: 'Average IRR', 
    icon: <TrendingUp size={18} />, 
    // bg-[#7C3AED]/15 creates a light purple tint
    bg: 'bg-[#7C3AED]/65 border-[#7C3AED]/30 text-[#7C3AED]', 
    trend: '+0.5%' 
  },
];

  return (
    <div className="h-full min-h-0 overflow-y-auto overflow-x-hidden space-y-4 2xl:space-y-8 pr-1 animate-in fade-in duration-700 custom-scrollbar">
      <div className="flex flex-col gap-3 lg:flex-row lg:justify-between lg:items-center border-b border-gray-200 pb-3 2xl:pb-4">
        <div>
          <div className="flex items-center gap-4">
            <h2 className="text-2xl font-black text-slate-900 uppercase tracking-tighter">Enterprise</h2>
            <div className="h-8 w-[2px] bg-blue-200 rotate-[20deg]"></div>
            <span className="bg-blue-600 text-white px-3 py-0.5 rounded text-[12px] font-bold tracking-[0.2em]">OVERVIEW</span>
          </div>
          <p className="text-xs font-bold text-black uppercase tracking-[0.2em] mt-1 flex items-center gap-2">
            <span className="w-2 h-2 bg-emerald-500 rounded-full animate-pulse" /> Live Data: All Branches
          </p>
          {dashboardError && (
            <p className="mt-1 text-[11px] font-black uppercase tracking-wide text-red-600">{dashboardError}</p>
          )}
        </div>
        <div className="bg-gray-100 p-3 rounded-2xl px-6 border border-gray-300 text-right">
          <p className="text-[10px] font-bold text-black uppercase leading-none mb-1">Business Date</p>
          <p className="text-lg font-bold text-black tracking-tight">10 APR 2026</p>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3 2xl:gap-6">
        {metrics.map((m, i) => (
          <div key={i} className={`relative overflow-hidden p-4 2xl:p-6 rounded-2xl 2xl:rounded-3xl transition-all duration-500 hover:-translate-y-1 group shadow-xl ${m.bg}`}>
            <div className="relative z-10">
              <div className="flex justify-between items-start mb-3 2xl:mb-6">
                <div className="bg-white/20 backdrop-blur-md text-white p-2.5 2xl:p-3 rounded-xl 2xl:rounded-2xl shadow-lg">{m.icon}</div>
                <span className="text-[18px] 2xl:text-[24px] font-bold px-2.5 py-1 rounded bg-black/20 text-white border border-white/10">{m.trend}</span>
              </div>
              <p className="text-[18px] 2xl:text-[24px] font-bold text-white mb-1 tracking-tight">{m.count}</p>
              <p className="text-[18px] 2xl:text-[24px] font-bold text-white uppercase tracking-[0.1em]">{m.label}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-3 2xl:gap-6">
        <div className="bg-white border border-gray-300 rounded-2xl 2xl:rounded-3xl p-4 2xl:p-6 shadow-md">
          <h3 className="text-[16px] 2xl:text-[24px] font-bold text-[#0052CC] uppercase tracking-widest mb-3 2xl:mb-6 flex items-center gap-2">
            <LayoutDashboard size={24} className="text-[#0052CC]" /> 
            <div className="min-w-0 flex-1">
              ACTIVE LOANS 
              <span className="block normal-case font-medium text-[16px] 2xl:text-[24px]">
                ({activeLoanCount.toLocaleString('en-IN')})
              </span>
            </div>
            <select
              value={branchLimit}
              onChange={(event) => setBranchLimit(event.target.value === 'ALL' ? 'ALL' : Number(event.target.value))}
              className="h-8 rounded border border-slate-300 bg-white px-2 text-[11px] font-black uppercase text-slate-800 outline-none"
            >
              <option value={5}>Top 5</option>
              <option value={10}>Top 10</option>
              <option value={15}>Top 15</option>
              <option value="ALL">All</option>
            </select>
          </h3>
          <div className="h-52 2xl:h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={activeLoansChartData}>
                <XAxis dataKey="name" fontSize={11} fontWeight="900" axisLine={false} tickLine={false} tick={{ fill: '#000' }} interval={0} />
                <Tooltip cursor={{ fill: '#f8f9fa' }} />
                <Bar 
                  dataKey="loans" 
                  fill="#0052CC" 
                  radius={[4, 4, 0, 0]} 
                  barSize={32}
                  label={{ position: 'top', fill: '#000', fontSize: 10, fontWeight: 'bold' }} 
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="bg-white border border-gray-300 rounded-2xl 2xl:rounded-3xl p-4 2xl:p-6 shadow-md">
          <h3 className="text-[16px] 2xl:text-[24px] font-bold text-[#0052CC] uppercase tracking-widest mb-3 2xl:mb-6 flex items-center gap-2">
            <LayoutDashboard size={24} className="text-[#0052CC]" /> 
            <div className="min-w-0 flex-1">
              AUM
              <span className="block normal-case font-medium text-[16px] 2xl:text-[24px]">
                ({formatCrores(totalAumLakhs)} Cr)
              </span>
            </div>
            <select
              value={branchLimit}
              onChange={(event) => setBranchLimit(event.target.value === 'ALL' ? 'ALL' : Number(event.target.value))}
              className="h-8 rounded border border-slate-300 bg-white px-2 text-[11px] font-black uppercase text-slate-800 outline-none"
            >
              <option value={5}>Top 5</option>
              <option value={10}>Top 10</option>
              <option value={15}>Top 15</option>
              <option value="ALL">All</option>
            </select>
          </h3>
          <div className="h-52 2xl:h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={aumChartData} layout="vertical" margin={{ top: 4, right: 18, bottom: 4, left: 8 }}>
                <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#e2e8f0" />
                <XAxis type="number" fontSize={10} fontWeight="bold" axisLine={false} tickLine={false} tick={{ fill: '#000' }} />
                <YAxis type="category" dataKey="name" width={58} fontSize={10} fontWeight="900" axisLine={false} tickLine={false} tick={{ fill: '#000' }} />
                <Tooltip cursor={{ fill: '#f8f9fa' }} formatter={(value) => [`${formatCrores(value)} Cr`, 'AUM']} />
                <Bar
                  dataKey="aum"
                  fill="#059669"
                  radius={[0, 4, 4, 0]}
                  barSize={14}
                  label={{ position: 'right', fill: '#000', fontSize: 10, fontWeight: 'bold', formatter: (value) => formatCrores(value) }}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="bg-white border border-gray-300 rounded-2xl 2xl:rounded-3xl p-4 2xl:p-6 shadow-md">
          
          <h3 className="text-[16px] 2xl:text-[24px] font-bold text-[#0052CC] uppercase tracking-widest mb-3 2xl:mb-6 flex items-center gap-2">
            <LayoutDashboard size={24} className="text-[#0052CC]" /> 
            <div className="min-w-0 flex-1">
              {disbursementLabel}
              <span className="block normal-case font-medium text-[16px] 2xl:text-[24px]">
                ({formatCrores(disbursementLakhs)} Cr)
              </span>
            </div>
          </h3>
          <div className="mb-3 flex flex-wrap items-center justify-end gap-2">
            <select
              value={accountYear || ''}
              onChange={(event) => setAccountYear(Number(event.target.value))}
              className="h-7 rounded border border-slate-300 bg-white px-2 text-[11px] font-black uppercase text-slate-800 outline-none"
            >
              {availableYears.map((year) => (
                <option key={year} value={year}>{year}</option>
              ))}
            </select>
            {[
              { value: 'YTD', label: 'YTD Month' },
              { value: 'MTD', label: 'MTD Day' },
              { value: 'ALL', label: 'All Year' },
            ].map((period) => (
              <button
                key={period.value}
                type="button"
                onClick={() => setDisbursementPeriod(period.value)}
                className={`h-7 rounded border px-3 text-[11px] font-black uppercase transition-colors ${disbursementPeriod === period.value ? 'border-[#0052CC] bg-[#0052CC] text-white' : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'}`}
              >
                {period.label}
              </button>
            ))}
          </div>
          <div className="h-52 2xl:h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={disbursementData}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                <XAxis dataKey="d" fontSize={10} fontWeight="bold" axisLine={false} tickLine={false} tick={{fill: '#000'}} />
                <Tooltip formatter={(value) => [`${formatCrores(value)} Cr`, 'Contract Value']} />
                <Area 
                  type="monotone" 
                  dataKey="v" 
                  stroke="#0052CC" 
                  strokeWidth={4} 
                  fill="#0052CC" 
                  fillOpacity={0.1}
                  label={{ fill: '#0052CC', fontSize: 10, fontWeight: 'bold', offset: 10 }}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </div>
  );
};

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Navigate replace to="/login" />} />
        <Route path="/login" element={<Login />} />
        <Route element={<ProtectedRoute><MainDashboardLayout /></ProtectedRoute>}>
          <Route path="/dashboard" element={<WelcomeDashboard />} />
          <Route path="/credit/masters/party-code" element={<PartyCodeModify />} />
          <Route path="/credit/transaction/contract/edit" element={<ContractGrid isDraft workflowStatus="E" title="Edit Contracts" showCreate={false} />} />
          <Route path="/credit/trans/contract/edit" element={<ContractGrid isDraft workflowStatus="E" title="Edit Contracts" showCreate={false} />} />
          <Route path="/credit/trans/contract-management/active-contracts" element={<ContractGrid />} />
          <Route path="/credit/trans/contract-management/draft-contracts" element={<ContractGrid isDraft title="Draft Contracts" />} />
          <Route path="/credit/trans/contract-management/edit-contracts" element={<ContractGrid isDraft workflowStatus="E" title="Edit Contracts" showCreate={false} />} />
          <Route path="/branch/transaction/request-to-flag-loan" element={<ContractGrid title="Request To Flag Loan" showCreate={false} enableFlagging />} />
          <Route path="/credit/trans/contract/form" element={<ContractEditForm />} />
          <Route path="/accounts/masters/ledger-code" element={<LedgerCodeMaster />} />
          <Route path="/accounts/masters/ledger" element={<LedgerCodeMaster />} />
          <Route path="/accounts/trans/voucher/receipt" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/entry/payment-cash" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/entry/payment-bank" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/entry/receipt-cash" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/entry/receipt-bank" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/entry/payment/cash" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/entry/payment/bank" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/entry/receipt/cash" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/entry/receipt/bank" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/entry/journal" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/authorisation" element={<VoucherAuthorisation />} />
          <Route path="/accounts/transaction/authorization" element={<VoucherAuthorisation />} />
          <Route path="/accounts/transaction/voucher-authorisation" element={<VoucherAuthorisation />} />
          <Route path="/accounts/transaction/voucher-authorization" element={<VoucherAuthorisation />} />
          <Route path="/accounts/transaction/authorization-request" element={<VoucherAuthorisation />} />
          <Route path="/accounts/transaction/authorisation-request" element={<VoucherAuthorisation />} />
          <Route path="/accounts/transaction/authorizateion-request" element={<VoucherAuthorisation />} />
          <Route path="/accounts/transaction/voucher-edit" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/edit/payment/cash" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/edit/payment/bank" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/edit/receipt/cash" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/edit/receipt/bank" element={<ReceiptVoucher />} />
          <Route path="/accounts/transaction/edit/journal" element={<ReceiptVoucher />} />
          <Route path="/accounts/reports/ratios" element={<Ratios />} />
          <Route path="/committee/reports/demand-list" element={<DemandListPage />} />
          <Route path="/committee/reports/afc" element={<AfcReportPage />} />
          <Route path="/committee/reports/aging-analysis" element={<AgingAnalysisPage />} />
          <Route path="/committee/aging-analysis/branch-wise" element={<AgingAnalysisPage initialTab="branch" />} />
          <Route path="/committee/aging-analysis/consolidated" element={<AgingAnalysisPage initialTab="consolidated" />} />
          <Route path="/committee/aging-analysis/loan-ticket-wise" element={<AgingMatrixReportPage type="loan-ticket" />} />
          <Route path="/committee/aging-analysis/interest-wise" element={<AgingMatrixReportPage type="interest" />} />
          <Route path="/committee/aging-analysis/consolidated-portfolio" element={<ConsolidatedPortfolioPage />} />
          <Route path="/committee/masters/user-management/roles" element={<RolesMaster />} />
          <Route path="/committee/masters/user-management/role-menu-permissions" element={<RoleMenuPermissions />} />
          <Route path="/committee/masters/user-management" element={<UserManagement />} />
          <Route 
  path="*" 
  element={
    <div className="flex flex-col items-center justify-center min-h-[60vh] p-20 text-center animate-in fade-in duration-500">
      <div className="bg-blue-50 p-6 rounded-full mb-6">
        <Icons.Clock size={48} className="text-[#0052CC] animate-pulse" />
      </div>
      <h2 className="text-3xl font-bold text-[#0052CC] uppercase tracking-tight mb-2">
        Coming Soon
      </h2>
      <p className="text-slate-600 text-[18px] max-w-md mx-auto">
        We're currently building this module to give you a better experience. 
        Please check back shortly!
      </p>
      <button 
        onClick={() => window.history.back()}
        className="mt-8 px-6 py-2 bg-[#0052CC] text-white rounded-lg font-bold hover:bg-blue-700 transition-colors"
      >
        GO BACK
      </button>
    </div>
  } 
/>
        </Route>
      </Routes>
    </Router>
  );
}

export default App;
