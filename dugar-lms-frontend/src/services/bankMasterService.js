import apiClient from '../api/apiClient';

export async function fetchBankMasters({
  filters = {},
  keyword = '',
  page = 0,
  pageSize = 25,
  sortColumn = 'bankCode',
  sortDirection = 'asc',
} = {}) {
  const params = { page, size: pageSize, sortColumn, sortDirection };
  if (keyword) params.keyword = keyword;
  const isActive = filters.isActive ?? '';
  if (isActive !== '') params.isActive = isActive;
  const response = await apiClient.get('/bank-masters', { params });
  return response.data;
}

export async function fetchActiveBankMasters() {
  const response = await apiClient.get('/bank-masters/active');
  return Array.isArray(response.data) ? response.data : [];
}

export async function createBankMaster(payload) {
  const response = await apiClient.post('/bank-masters', payload);
  return response.data;
}

export async function updateBankMaster(id, payload) {
  const response = await apiClient.put(`/bank-masters/${id}`, payload);
  return response.data;
}
