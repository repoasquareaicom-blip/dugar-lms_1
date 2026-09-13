import { resolveMenuPath } from './menuRouteMap';

const ICON_BY_NAME = [
  { keys: ['dashboard'], icon: 'LayoutDashboard' },
  { keys: ['credit'], icon: 'HandCoins' },
  { keys: ['master'], icon: 'FolderTree' },
  { keys: ['transaction'], icon: 'ArrowLeftRight' },
  { keys: ['contract'], icon: 'FileSpreadsheet' },
  { keys: ['party'], icon: 'Users' },
  { keys: ['account'], icon: 'BookOpenCheck' },
  { keys: ['voucher', 'receipt'], icon: 'ReceiptIndianRupee' },
  { keys: ['report', 'ratio'], icon: 'FileBarChart2' },
  { keys: ['collection'], icon: 'HandHelping' },
  { keys: ['committee'], icon: 'UsersRound' },
  { keys: ['demand'], icon: 'FileSpreadsheet' },
  { keys: ['legal'], icon: 'Scale' },
  { keys: ['analytics'], icon: 'ChartNoAxesCombined' },
  { keys: ['admin'], icon: 'Settings2' },
];

function normalizeText(value) {
  return String(value || '').toLowerCase().replace(/\s+/g, ' ').trim();
}

function resolveIcon(menu) {
  if (menu?.icon) return menu.icon;

  const menuName = normalizeText(menu?.menuName || menu?.menu_name);
  const menuCode = normalizeText(menu?.menuCode || menu?.menu_code);
  const source = `${menuName} ${menuCode}`;
  const match = ICON_BY_NAME.find((entry) => entry.keys.some((key) => source.includes(key)));

  return match?.icon || 'Circle';
}

function sortMenus(items) {
  const hasDisplayOrder = items.some((item) => item.displayOrder !== undefined || item.display_order !== undefined);

  if (!hasDisplayOrder) {
    return [...items];
  }

  return [...items].sort((a, b) => {
    const orderA = Number(a.displayOrder ?? a.display_order ?? 0);
    const orderB = Number(b.displayOrder ?? b.display_order ?? 0);

    if (orderA !== orderB) return orderA - orderB;
    return 0;
  });
}

function isInactive(menu) {
  return menu?.isActive === false
    || menu?.is_active === false
    || menu?.isVisible === false
    || menu?.is_visible === false;
}

function dedupeMenus(items) {
  const seen = new Set();
  return items.filter((item) => {
    const key = item.path && item.path !== '#'
      ? `path:${item.path}`
      : `name:${normalizeText(`${item.menuName || ''} ${item.menuCode || ''}`)}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function normalizeMenuItems(menus = []) {
  if (!Array.isArray(menus)) return [];

  const normalizedItems = sortMenus(menus).filter((menu) => !isInactive(menu)).map((menu, index) => {
    const rawOrder = menu.displayOrder ?? menu.display_order;
    const normalized = {
      ...menu,
      menuId: menu.menuId ?? menu.menu_id ?? `${menu.menuCode || menu.menuName || 'menu'}-${index}`,
      menuName: menu.menuName || menu.menu_name || menu.label || 'Menu',
      menuCode: menu.menuCode || menu.menu_code || '',
      path: menu.urlPath || menu.path || '#',
      urlPath: menu.urlPath || menu.path || '#',
      icon: resolveIcon(menu),
      displayOrder: rawOrder === undefined ? index + 1 : Number(rawOrder),
      subMenus: normalizeMenuItems(menu.children || menu.subMenus || menu.submenus || []),
    };

    normalized.path = resolveMenuPath(normalized);
    normalized.urlPath = normalized.path;
    return normalized;
  });

  return dedupeMenus(normalizedItems);
}

function menuMatches(menu, names) {
  const source = normalizeText(`${menu.menuName || ''} ${menu.menuCode || ''}`);
  return names.some((name) => source.includes(name));
}

function nextOrder(items) {
  return Math.max(0, ...items.map((item) => Number(item.displayOrder || item.display_order || 0))) + 1;
}

function ensureMenu(parent, menu) {
  const existing = parent.subMenus.find((item) => menuMatches(item, [normalizeText(menu.menuName), normalizeText(menu.menuCode)]));

  if (existing) {
    existing.path = menu.path;
    existing.urlPath = menu.urlPath;
    existing.icon = existing.icon || menu.icon;
    return existing;
  }

  parent.subMenus.push({
    ...menu,
    displayOrder: menu.displayOrder ?? nextOrder(parent.subMenus),
    subMenus: menu.subMenus || [],
  });

  parent.subMenus = sortMenus(parent.subMenus);
  return parent.subMenus[parent.subMenus.length - 1];
}

function ensureActiveContractsMenu(menuTree) {
  const credit = menuTree.find((menu) => menuMatches(menu, ['credit']));
  if (!credit) return menuTree;

  const transaction = credit.subMenus.find((menu) => menuMatches(menu, ['transaction']));
  if (!transaction) return menuTree;

  let contractManagement = transaction.subMenus.find((menu) => menuMatches(menu, ['contract management']));

  if (!contractManagement) {
    contractManagement = transaction.subMenus.find((menu) => menuMatches(menu, ['contract']));
  }

  if (contractManagement) {
    contractManagement.menuName = 'Contract Management';
    contractManagement.icon = contractManagement.icon || 'FileSpreadsheet';
    contractManagement.path = '#';
    contractManagement.urlPath = '#';
    contractManagement.subMenus = contractManagement.subMenus || [];
  } else {
    contractManagement = ensureMenu(transaction, {
      menuId: 'frontend-contract-management',
      menuName: 'Contract Management',
      menuCode: 'CONTRACT_MANAGEMENT',
      icon: 'FileSpreadsheet',
      path: '#',
      urlPath: '#',
      displayOrder: nextOrder(transaction.subMenus),
      subMenus: [],
    });
  }

  ensureMenu(contractManagement, {
    menuId: 'frontend-active-contracts',
    menuName: 'Active Contracts',
    menuCode: 'ACTIVE_CONTRACTS',
    icon: 'CheckCircle',
    path: '/credit/trans/contract-management/active-contracts',
    urlPath: '/credit/trans/contract-management/active-contracts',
    displayOrder: nextOrder(contractManagement.subMenus),
    subMenus: [],
  });

  ensureMenu(contractManagement, {
    menuId: 'frontend-draft-contracts',
    menuName: 'Draft Contracts',
    menuCode: 'DRAFT_CONTRACTS',
    icon: 'FileClock',
    path: '/credit/trans/contract-management/draft-contracts',
    urlPath: '/credit/trans/contract-management/draft-contracts',
    displayOrder: nextOrder(contractManagement.subMenus),
    subMenus: [],
  });

  return menuTree;
}

function ensureCommitteeDemandListMenu(menuTree) {
  let committee = menuTree.find((menu) => menuMatches(menu, ['committee']));
  if (!committee) {
    committee = {
      menuId: 'frontend-committee',
      menuName: 'Committee',
      menuCode: 'COMMITTEE',
      icon: 'UsersRound',
      path: '#',
      urlPath: '#',
      displayOrder: nextOrder(menuTree),
      subMenus: [],
    };
    menuTree.push(committee);
  }

  committee.path = '#';
  committee.urlPath = '#';
  committee.icon = committee.icon || 'UsersRound';
  committee.subMenus = committee.subMenus || [];

  const reports = ensureMenu(committee, {
    menuId: 'frontend-committee-reports',
    menuName: 'Reports',
    menuCode: 'COMMITTEE_REPORTS',
    icon: 'FileBarChart2',
    path: '#',
    urlPath: '#',
    displayOrder: nextOrder(committee.subMenus),
    subMenus: [],
  });

  ensureMenu(reports, {
    menuId: 'frontend-demand-list',
    menuName: 'Demand List',
    menuCode: 'COMMITTEE_DEMAND_LIST',
    icon: 'FileSpreadsheet',
    path: '/committee/reports/demand-list',
    urlPath: '/committee/reports/demand-list',
    displayOrder: nextOrder(reports.subMenus),
    subMenus: [],
  });

  ensureMenu(reports, {
    menuId: 'frontend-afc-report',
    menuName: 'AFC Report',
    menuCode: 'COMMITTEE_AFC_REPORT',
    icon: 'FileText',
    path: '/committee/reports/afc',
    urlPath: '/committee/reports/afc',
    displayOrder: nextOrder(reports.subMenus),
    subMenus: [],
  });

  reports.subMenus = reports.subMenus.filter((menu) => !menuMatches(menu, ['consolidated portfolio']));

  reports.subMenus = reports.subMenus.filter((menu) => {
    if (!menuMatches(menu, ['aging analysis', 'ageing analysis'])) return true;
    const path = menu.path || menu.urlPath || '#';
    const code = normalizeText(menu.menuCode || menu.menu_code);
    const hasChildren = (menu.subMenus || []).length > 0;
    return code === 'committee_aging_analysis' || path === '#' || hasChildren;
  });

  const agingAnalysis = ensureMenu(reports, {
    menuId: 'frontend-aging-analysis',
    menuName: 'Aging Analysis',
    menuCode: 'COMMITTEE_AGING_ANALYSIS',
    icon: 'BarChart3',
    path: '#',
    urlPath: '#',
    displayOrder: nextOrder(reports.subMenus),
    subMenus: [],
  });

  agingAnalysis.path = '#';
  agingAnalysis.urlPath = '#';
  agingAnalysis.subMenus = agingAnalysis.subMenus || [];

  ensureMenu(agingAnalysis, {
    menuId: 'frontend-aging-branch-wise',
    menuName: 'Branch Wise',
    menuCode: 'COMMITTEE_AGING_BRANCH_WISE',
    icon: 'GitBranch',
    path: '/committee/aging-analysis/branch-wise',
    urlPath: '/committee/aging-analysis/branch-wise',
    displayOrder: 1,
    subMenus: [],
  });

  ensureMenu(agingAnalysis, {
    menuId: 'frontend-aging-consolidated',
    menuName: 'Consolidated',
    menuCode: 'COMMITTEE_AGING_CONSOLIDATED',
    icon: 'Table2',
    path: '/committee/aging-analysis/consolidated',
    urlPath: '/committee/aging-analysis/consolidated',
    displayOrder: 2,
    subMenus: [],
  });

  ensureMenu(agingAnalysis, {
    menuId: 'frontend-aging-loan-ticket-wise',
    menuName: 'Loan Ticket Wise',
    menuCode: 'COMMITTEE_AGING_LOAN_TICKET_WISE',
    icon: 'IndianRupee',
    path: '/committee/aging-analysis/loan-ticket-wise',
    urlPath: '/committee/aging-analysis/loan-ticket-wise',
    displayOrder: 3,
    subMenus: [],
  });

  ensureMenu(agingAnalysis, {
    menuId: 'frontend-aging-interest-wise',
    menuName: 'Interest Wise',
    menuCode: 'COMMITTEE_AGING_INTEREST_WISE',
    icon: 'Percent',
    path: '/committee/aging-analysis/interest-wise',
    urlPath: '/committee/aging-analysis/interest-wise',
    displayOrder: 4,
    subMenus: [],
  });

  ensureMenu(agingAnalysis, {
    menuId: 'frontend-aging-consolidated-portfolio',
    menuName: 'Consolidated Portfolio',
    menuCode: 'COMMITTEE_AGING_CONSOLIDATED_PORTFOLIO',
    icon: 'PieChart',
    path: '/committee/aging-analysis/consolidated-portfolio',
    urlPath: '/committee/aging-analysis/consolidated-portfolio',
    displayOrder: 5,
    subMenus: [],
  });

  return menuTree;
}

export function normalizeMenuTree(menus = []) {
  const menuTree = normalizeMenuItems(menus);
  const existingDashboard = menuTree.find((menu) => menuMatches(menu, ['dashboard']));

  if (existingDashboard) {
    existingDashboard.menuName = 'Dashboard';
    existingDashboard.icon = existingDashboard.icon || 'LayoutDashboard';
    existingDashboard.path = '/dashboard';
    existingDashboard.urlPath = '/dashboard';
    existingDashboard.displayOrder = 0;
  } else {
    menuTree.unshift({
      menuId: 'frontend-dashboard',
      menuName: 'Dashboard',
      menuCode: 'DASHBOARD',
      icon: 'LayoutDashboard',
      path: '/dashboard',
      urlPath: '/dashboard',
      displayOrder: 0,
      subMenus: [],
    });
  }

  return sortMenus(ensureActiveContractsMenu(menuTree));
}
