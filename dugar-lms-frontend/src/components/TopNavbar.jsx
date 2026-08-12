import React, { useState, useMemo } from 'react';
import NavItem from './NavItem';
import MobileSidebar from './MobileSideBar';
import { Menu, Bell, User } from 'lucide-react';
import { Link } from 'react-router-dom';
import logoIcon from '../assets/images/logo-icon.png';

const FALLBACK_MENU = [
  { menuId: 1, menuName: 'Dashboard', icon: 'LayoutDashboard', path: '/dashboard', displayOrder: 1, subMenus: [] },
  {
    menuId: 2,
    menuName: 'Credit',
    icon: 'HandCoins',
    path: '#',
    displayOrder: 2,
    subMenus: [
      {
        menuId: 3,
        menuName: 'Masters',
        icon: 'FolderTree',
        path: '#',
        displayOrder: 1,
        subMenus: [
          { menuId: 4, menuName: 'Party Code', icon: 'Users', path: '/credit/masters/party-code', displayOrder: 1, subMenus: [] },
        ],
      },
      {
        menuId: 5,
        menuName: 'Transaction',
        icon: 'ArrowLeftRight',
        path: '#',
        displayOrder: 2,
        subMenus: [
          {
            menuId: 6,
            menuName: 'Contract Management',
            icon: 'FileSpreadsheet',
            path: '#',
            displayOrder: 1,
            subMenus: [
              { menuId: 7, menuName: 'Active Contracts', icon: 'CheckCircle', path: '/credit/trans/contract-management/active-contracts', displayOrder: 1, subMenus: [] },
              { menuId: 8, menuName: 'Draft Contracts', icon: 'FileClock', path: '/credit/trans/contract-management/draft-contracts', displayOrder: 2, subMenus: [] },
              { menuId: 9, menuName: 'Edit Contracts', icon: 'FilePenLine', path: '/credit/trans/contract-management/edit-contracts', displayOrder: 3, subMenus: [] },
              { menuId: 29, menuName: 'Contract Form', icon: 'FilePlus2', path: '/credit/trans/contract/form', displayOrder: 4, subMenus: [] },
            ],
          },
        ],
      },
    ],
  },
  {
    menuId: 9,
    menuName: 'Accounts',
    icon: 'BookOpenCheck',
    path: '#',
    displayOrder: 3,
    subMenus: [
      {
        menuId: 10,
        menuName: 'Transactions',
        icon: 'ReceiptIndianRupee',
        path: '#',
        displayOrder: 1,
        subMenus: [
          { menuId: 11, menuName: 'Receipt Voucher', icon: 'Receipt', path: '/accounts/trans/voucher/receipt', displayOrder: 1, subMenus: [] },
        ],
      },
      {
        menuId: 12,
        menuName: 'Reports',
        icon: 'FileBarChart2',
        path: '#',
        displayOrder: 2,
        subMenus: [
          { menuId: 13, menuName: 'Ratios', icon: 'BarChart3', path: '/accounts/reports/ratios', displayOrder: 1, subMenus: [] },
        ],
      },
    ],
  },
  {
    menuId: 14,
    menuName: 'Committee',
    icon: 'UsersRound',
    path: '#',
    displayOrder: 4,
    subMenus: [
      {
        menuId: 141,
        menuName: 'Reports',
        icon: 'FileBarChart2',
        path: '#',
        displayOrder: 1,
        subMenus: [
          { menuId: 142, menuName: 'Demand List', icon: 'FileSpreadsheet', path: '/committee/reports/demand-list', displayOrder: 1, subMenus: [] },
          { menuId: 143, menuName: 'AFC Report', icon: 'FileText', path: '/committee/reports/afc', displayOrder: 2, subMenus: [] },
          {
            menuId: 144,
            menuName: 'Aging Analysis',
            icon: 'BarChart3',
            path: '#',
            displayOrder: 3,
            subMenus: [
              { menuId: 145, menuName: 'Branch Wise', icon: 'GitBranch', path: '/committee/aging-analysis/branch-wise', displayOrder: 1, subMenus: [] },
              { menuId: 146, menuName: 'Consolidated', icon: 'Table2', path: '/committee/aging-analysis/consolidated', displayOrder: 2, subMenus: [] },
              { menuId: 147, menuName: 'Loan Ticket Wise', icon: 'IndianRupee', path: '/committee/aging-analysis/loan-ticket-wise', displayOrder: 3, subMenus: [] },
              { menuId: 148, menuName: 'Interest Wise', icon: 'Percent', path: '/committee/aging-analysis/interest-wise', displayOrder: 4, subMenus: [] },
            ],
          },
        ],
      },
    ],
  },
  {
    menuId: 15,
    menuName: 'Collections',
    icon: 'HandHelping',
    path: '#',
    displayOrder: 5,
    subMenus: [
      { menuId: 151, menuName: 'Daily Collection', icon: 'CalendarCheck2', path: '#', displayOrder: 1, subMenus: [] },
      { menuId: 152, menuName: 'Arrear Followup', icon: 'AlarmClockCheck', path: '#', displayOrder: 2, subMenus: [] },
    ],
  },
  {
    menuId: 17,
    menuName: 'Legal',
    icon: 'Scale',
    path: '#',
    displayOrder: 6,
    subMenus: [
      { menuId: 18, menuName: 'Notices', icon: 'FileWarning', path: '#', displayOrder: 1, subMenus: [] },
      { menuId: 19, menuName: 'Case Tracker', icon: 'Gavel', path: '#', displayOrder: 2, subMenus: [] },
    ],
  },
  {
    menuId: 20,
    menuName: 'Analytics',
    icon: 'ChartNoAxesCombined',
    path: '#',
    displayOrder: 6,
    subMenus: [
      { menuId: 21, menuName: 'Portfolio', icon: 'PieChart', path: '#', displayOrder: 1, subMenus: [] },
      { menuId: 22, menuName: 'Branch Performance', icon: 'Building2', path: '#', displayOrder: 2, subMenus: [] },
      { menuId: 23, menuName: 'NPA Monitor', icon: 'ShieldAlert', path: '#', displayOrder: 3, subMenus: [] },
    ],
  },
  {
    menuId: 24,
    menuName: 'Administration',
    icon: 'Settings2',
    path: '#',
    displayOrder: 7,
    subMenus: [
      { menuId: 25, menuName: 'User Master', icon: 'UserCog', path: '#', displayOrder: 1, subMenus: [] },
      { menuId: 26, menuName: 'Role Master', icon: 'UserRoundCog', path: '#', displayOrder: 2, subMenus: [] },
      { menuId: 27, menuName: 'Menu Setup', icon: 'ListTree', path: '#', displayOrder: 3, subMenus: [] },
      { menuId: 28, menuName: 'Audit Trail', icon: 'History', path: '#', displayOrder: 4, subMenus: [] },
    ],
  },
];

const TopNavbar = ({ menuTree, userData, notificationsCount = 5 }) => {
  const [isMobileOpen, setIsMobileOpen] = useState(false);

  // BULLETPROOF SORTING LOGIC
  const sortedMenu = useMemo(() => {
    const sourceMenu = Array.isArray(menuTree) && menuTree.length > 0 ? menuTree : FALLBACK_MENU;
    
    // Create a copy to avoid mutating props
    return [...sourceMenu].sort((a, b) => {
      // Your NavItem uses item.menuName, so we must check that property
      const nameA = (a.menuName || a.menu_name || "").toLowerCase().trim();
      const nameB = (b.menuName || b.menu_name || "").toLowerCase().trim();

      // 1. FORCE 'DASHBOARD' TO THE FRONT (-1 moves it to the start)
      if (nameA === 'dashboard') return -1;
      if (nameB === 'dashboard') return 1;

      // 2. FOR EVERYTHING ELSE, USE THE DB DISPLAY ORDER
      const orderA = a.displayOrder || a.display_order || 0;
      const orderB = b.displayOrder || b.display_order || 0;
      
      return orderA - orderB;
    });
  }, [menuTree]);

  return (
    <>
      <nav className="relative z-[1000] w-full h-[72px] shrink-0 bg-gradient-to-b from-white to-blue-100 border-b-2 border-blue-300 px-3 md:px-6 flex items-center justify-between shadow-md">
        
        {/* LEFT: Mobile Toggle & Brand */}
        <div className="flex items-center h-full gap-3 md:gap-8">
          <button 
            onClick={() => setIsMobileOpen(true)}
            className="lg:hidden p-2 bg-white/50 hover:bg-white rounded-md text-slate-900 border border-blue-200 shadow-sm transition-all"
          >
            <Menu size={24} strokeWidth={2.5} />
          </button>

          <Link 
  to="/dashboard" 
  className="flex items-center gap-4 pr-3 md:pr-6 md:border-r border-blue-300/40 h-12 shrink-0 cursor-pointer hover:opacity-80 transition-opacity"
>
  <img src={logoIcon} alt="Dugar Loan Edge" className="h-11 w-auto object-contain" />
</Link>

          {/* Desktop Navigation - Using the Sorted Menu */}
          <div className="hidden lg:flex items-center h-full gap-1">
            {sortedMenu.map((item) => (
              <NavItem key={item.menuId} item={item} depth={0} />
            ))}
          </div>
        </div>

        {/* RIGHT: User & Notifications */}
        <div className="flex items-center gap-2 md:gap-4">
          <button className="p-2 text-[#0052CC] hover:bg-white rounded-full transition-all relative border border-blue-200/50 bg-white/30">
            <Bell size={20} strokeWidth={2.5} />
            {notificationsCount > 0 && (
              <span className="absolute top-0 right-0 flex h-4 w-4 items-center justify-center rounded-full bg-red-600 text-[10px] font-bold text-white ring-2 ring-white">
                {notificationsCount}
              </span>
            )}
          </button>

          <div className="flex items-center gap-3 pl-3 border-l-2 border-blue-300/40 h-10">
            <div className="flex flex-col items-end leading-tight">
              <span className="text-[13px] font-black text-black uppercase">
                {userData?.fullName}
              </span>
              <span className="text-[10px] font-black text-[#0052CC] uppercase tracking-wider">
                {userData?.roleName}
              </span>
            </div>
            <div className="h-9 w-9 rounded-full bg-[#0052CC] text-white flex items-center justify-center border-2 border-white shadow-sm font-bold text-sm">
                {userData?.fullName?.charAt(0) || <User size={18} />}
            </div>
          </div>
        </div>
      </nav>

      {/* Mobile Sidebar */}
      {isMobileOpen && (
        <MobileSidebar 
            isOpen={isMobileOpen} 
            onClose={() => setIsMobileOpen(false)} 
            menuTree={sortedMenu} 
        />
      )}
    </>
  );
};

export default TopNavbar;
