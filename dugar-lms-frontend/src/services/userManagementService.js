import apiClient from '../api/apiClient';

export async function fetchUsers({
  filters = {},
  keyword = '',
  page = 0,
  pageSize = 25,
  sortColumn = 'username',
  sortDirection = 'asc',
} = {}) {
  const params = { page, size: pageSize, sortColumn, sortDirection };
  if (keyword) params.keyword = keyword;
  const isActive = filters.isActive ?? '';
  if (isActive !== '') params.isActive = isActive;
  const response = await apiClient.get('/access-management/users', { params });
  return response.data;
}

export async function createUser(payload) {
  const response = await apiClient.post('/access-management/users', payload);
  return response.data;
}

export async function updateUser(userId, payload) {
  const response = await apiClient.put(`/access-management/users/${encodeURIComponent(userId)}`, payload);
  return response.data;
}

export async function fetchActiveUserContracts(keyword = '') {
  const params = { limit: 25 };
  if (keyword) params.keyword = keyword;
  const response = await apiClient.get('/access-management/users/active-contracts', { params });
  return response.data;
}
