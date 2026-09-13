import apiClient from '../api/apiClient';

export async function fetchRoles({
  filters = {},
  keyword = '',
  page = 0,
  pageSize = 25,
  sortColumn = 'roleName',
  sortDirection = 'asc',
} = {}) {
  const params = { page, size: pageSize, sortColumn, sortDirection };
  if (keyword) params.keyword = keyword;
  const isActive = filters.isActive ?? '';
  if (isActive !== '') params.isActive = isActive;
  const response = await apiClient.get('/access-management/roles', { params });
  return response.data;
}

export async function createRole(payload) {
  const response = await apiClient.post('/access-management/roles', payload);
  return response.data;
}

export async function updateRole(roleId, payload) {
  const response = await apiClient.put(`/access-management/roles/${encodeURIComponent(roleId)}`, payload);
  return response.data;
}
