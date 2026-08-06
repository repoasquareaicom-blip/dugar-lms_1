const NAME_ROUTE_MAP = [
  { keys: ['dashboard'], route: '/dashboard' },
  { keys: ['party code modify', 'party code', 'party master'], route: '/credit/masters/party-code' },
  { keys: ['active contracts'], route: '/credit/trans/contract-management/active-contracts' },
  { keys: ['edit contract', 'modify contract', 'contract edit', 'contract list', 'contracts'], route: '/credit/trans/contract/edit' },
  { keys: ['contract form'], route: '/credit/trans/contract/form' },
  { keys: ['ledger code', 'ledger master'], route: '/accounts/masters/ledger-code' },
  {
    keys: [
      'voucher request sent for authorization',
      'voucher request sent for authorisation',
      'voucher request sent for authorizateion',
      'authorization request',
      'authorisation request',
      'authorizateion request',
      'voucher authorization',
      'voucher authorisation',
      'voucher authorizateion',
      'sent for authorization',
      'sent for authorisation',
      'sent for authorizateion',
    ],
    route: '/accounts/transaction/voucher-authorisation',
  },
  { keys: ['edit payment voucher - cash', 'edit payment cash'], route: '/accounts/transaction/edit/payment/cash' },
  { keys: ['edit payment voucher - bank', 'edit payment bank'], route: '/accounts/transaction/edit/payment/bank' },
  { keys: ['edit receipt voucher - cash', 'edit receipt cash'], route: '/accounts/transaction/edit/receipt/cash' },
  { keys: ['edit receipt voucher - bank', 'edit receipt bank'], route: '/accounts/transaction/edit/receipt/bank' },
  { keys: ['edit journal voucher'], route: '/accounts/transaction/edit/journal' },
  { keys: ['payment voucher - cash', 'payment cash'], route: '/accounts/transaction/entry/payment/cash' },
  { keys: ['payment voucher - bank', 'payment bank'], route: '/accounts/transaction/entry/payment/bank' },
  { keys: ['receipt voucher - cash', 'receipt cash'], route: '/accounts/transaction/entry/receipt/cash' },
  { keys: ['receipt voucher - bank', 'receipt bank'], route: '/accounts/transaction/entry/receipt/bank' },
  { keys: ['journal voucher'], route: '/accounts/transaction/entry/journal' },
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

  if (!lookupText) {
    return rawPath;
  }

  for (const entry of NAME_ROUTE_MAP) {
    if (entry.keys.some((key) => lookupText.includes(key))) {
      return entry.route;
    }
  }

  if (rawPath.startsWith('/') && !rawPath.startsWith('/menu/')) {
    return rawPath;
  }

  return rawPath;
}
