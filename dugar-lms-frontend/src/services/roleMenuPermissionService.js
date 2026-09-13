import apiClient from '../api/apiClient';

export async function fetchPermissionMenus() {
  const response = await apiClient.get('/access-management/role-menu-permissions/menus');
  return response.data;
}

export async function fetchRoleMenuPermissions(roleId) {
  const response = await apiClient.get(`/access-management/role-menu-permissions/${encodeURIComponent(roleId)}`);
  return response.data;
}

export async function saveRoleMenuPermissions(roleId, menuIds) {
  const response = await apiClient.put(`/access-management/role-menu-permissions/${encodeURIComponent(roleId)}`, { menuIds });
  return response.data;
}
