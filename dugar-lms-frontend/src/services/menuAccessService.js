import apiClient from '../api/apiClient';

export async function verifyMenuAccess(menuCode) {
  const response = await apiClient.get(`/access-management/menu-access/${encodeURIComponent(menuCode)}`);
  return response.data;
}
