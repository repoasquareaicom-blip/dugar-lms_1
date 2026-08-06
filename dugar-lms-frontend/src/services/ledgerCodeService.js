import apiClient from '../api/apiClient';

export async function fetchLedgerCodes({
  filters = {},
  keyword = '',
  page = 0,
  pageSize = 25,
  sortColumn = 'ledgerCode',
  sortDirection = 'asc',
} = {}) {
  const params = { page, size: pageSize, sortColumn, sortDirection };
  if (keyword) params.keyword = keyword;
  const isActive = filters.isActive ?? '';
  if (isActive !== '') params.isActive = isActive;
  const response = await apiClient.get('/accounts/ledger-codes', { params });
  return response.data;
}

export async function createLedgerCode(payload) {
  const response = await apiClient.post('/accounts/ledger-codes', payload);
  return response.data;
}

export async function updateLedgerCode(ledgerCode, payload) {
  const response = await apiClient.put(`/accounts/ledger-codes/${encodeURIComponent(ledgerCode)}`, payload);
  return response.data;
}

export async function updateLedgerCodeActive(ledgerCode, isActive) {
  const response = await apiClient.patch(`/accounts/ledger-codes/${encodeURIComponent(ledgerCode)}/active`, { isActive });
  return response.data;
}
