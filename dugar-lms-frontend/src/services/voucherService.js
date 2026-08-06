import apiClient from '../api/apiClient';

export async function saveVoucher(payload) {
  const response = await apiClient.post('/accounts/vouchers', payload);
  return response.data;
}

export async function searchVouchers(filters = {}) {
  const params = {};
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params[key] = value;
  });
  const response = await apiClient.get('/accounts/vouchers', { params });
  return response.data;
}

export async function fetchVoucher(voucherHeaderId) {
  const response = await apiClient.get(`/accounts/vouchers/${voucherHeaderId}`);
  return response.data;
}

export async function updateVoucher(voucherHeaderId, payload) {
  const response = await apiClient.put(`/accounts/vouchers/${voucherHeaderId}`, payload);
  return response.data;
}
