const NAME_ROUTE_MAP = [
  { keys: ['dashboard'], route: '/dashboard' },
  { keys: ['party code modify', 'party code', 'party master'], route: '/credit/masters/party-code' },
  { keys: ['active contracts'], route: '/credit/trans/contract-management/active-contracts' },
  { keys: ['edit contract', 'modify contract', 'contract edit', 'contract list', 'contracts'], route: '/credit/trans/contract/edit' },
  { keys: ['contract form'], route: '/credit/trans/contract/form' },
  { keys: ['receipt voucher', 'receipt voucher - cash & bank'], route: '/accounts/trans/voucher/receipt' },
  { keys: ['various ratios', 'ratio reports', 'ratios'], route: '/accounts/reports/ratios' },
];

function normalize(value) {
  return String(value || '').toLowerCase().replace(/\s+/g, ' ').trim();
}

export function resolveMenuPath(item) {
  const rawPath = item?.urlPath || item?.path || '#';
  const name = normalize(item?.menuName || item?.menu_name);
  const code = normalize(item?.menuCode || item?.menu_code);
  const lookupText = `${name} ${code}`.trim();

  if (['/contracts', '/contracts/active', '/active-contracts'].includes(rawPath)) {
    return '/credit/trans/contract-management/active-contracts';
  }

  if (rawPath.startsWith('/') && !rawPath.startsWith('/menu/')) {
    return rawPath;
  }

  if (!lookupText) {
    return rawPath;
  }

  for (const entry of NAME_ROUTE_MAP) {
    if (entry.keys.some((key) => lookupText.includes(key))) {
      return entry.route;
    }
  }

  return rawPath;
}
