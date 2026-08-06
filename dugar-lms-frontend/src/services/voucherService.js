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

export async function fetchAuthorisationQueue(filters = {}) {
  const params = {};
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params[key] = value;
  });
  const response = await apiClient.get('/accounts/vouchers/authorisation-queue', { params });
  return response.data;
}

export async function fetchVoucherReview(voucherHeaderId) {
  const response = await apiClient.get(`/accounts/vouchers/authorisation-queue/${voucherHeaderId}`);
  return response.data;
}

export async function authoriseVoucher(voucherHeaderId) {
  const response = await apiClient.post(`/accounts/vouchers/${voucherHeaderId}/authorise`);
  return response.data;
}

export async function rejectVoucher(voucherHeaderId, reason) {
  const response = await apiClient.post(`/accounts/vouchers/${voucherHeaderId}/reject`, { reason });
  return response.data;
}

export async function cancelVoucher(voucherHeaderId, reason) {
  const response = await apiClient.post(`/accounts/vouchers/${voucherHeaderId}/cancel`, { reason });
  return response.data;
}

export async function reopenVoucher(voucherHeaderId, reason) {
  const response = await apiClient.post(`/accounts/vouchers/${voucherHeaderId}/reopen`, { reason });
  return response.data;
}

export async function resubmitVoucher(voucherHeaderId) {
  const response = await apiClient.post(`/accounts/vouchers/${voucherHeaderId}/resubmit`);
  return response.data;
}
