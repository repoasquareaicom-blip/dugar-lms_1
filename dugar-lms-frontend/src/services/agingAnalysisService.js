import apiClient from '../api/apiClient';

function addParam(params, key, value) {
  if (value !== null && value !== undefined && value !== '') {
    params[key] = value;
  }
}

function paramsFrom(filters = {}) {
  const params = {};
  addParam(params, 'asOnDate', filters.asOnDate);
  addParam(params, 'areaCode', String(filters.areaCode || '').trim());
  addParam(params, 'contractNumber', String(filters.contractNumber || '').trim());
  return params;
}

export async function fetchAgingAnalysis(filters) {
  const response = await apiClient.get('/reports/aging-analysis', {
    params: paramsFrom(filters),
    timeout: 120000,
  });
  return response.data;
}

function matrixParams(filters = {}) {
  const params = {};
  addParam(params, 'asOnDate', filters.asOnDate);
  addParam(params, 'areaCode', String(filters.areaCode || '').trim());
  return params;
}

export async function fetchLoanTicketWiseAging(filters) {
  const response = await apiClient.get('/reports/aging-analysis/loan-ticket-wise', { params: matrixParams(filters), timeout: 120000 });
  return response.data;
}

export async function fetchInterestWiseAging(filters) {
  const response = await apiClient.get('/reports/aging-analysis/interest-wise', { params: matrixParams(filters), timeout: 120000 });
  return response.data;
}

export async function fetchAgingContracts({ areaCode, bucket, asOnDate } = {}) {
  const params = {};
  addParam(params, 'areaCode', String(areaCode || '').trim());
  addParam(params, 'bucket', bucket);
  addParam(params, 'asOnDate', asOnDate);
  const response = await apiClient.get('/reports/aging-analysis/contracts', { params, timeout: 120000 });
  return response.data;
}

export async function fetchAgingContractDetail(contractId, { asOnDate } = {}) {
  const params = {};
  addParam(params, 'asOnDate', asOnDate);
  const response = await apiClient.get(`/reports/aging-analysis/contracts/${contractId}`, { params });
  return response.data;
}

export async function fetchAgingContractEmis(contractId, { asOnDate } = {}) {
  const params = {};
  addParam(params, 'asOnDate', asOnDate);
  const response = await apiClient.get(`/reports/aging-analysis/contracts/${contractId}/emis`, { params });
  return response.data;
}

export async function fetchAgingContractReceipts(contractId, { asOnDate } = {}) {
  const params = {};
  addParam(params, 'asOnDate', asOnDate);
  const response = await apiClient.get(`/reports/aging-analysis/contracts/${contractId}/receipts`, { params });
  return response.data;
}

export async function fetchAgingRawVoucher(contractId, { voucherNumber, voucherType } = {}) {
  const params = {};
  addParam(params, 'voucherNumber', voucherNumber);
  addParam(params, 'voucherType', voucherType);
  const response = await apiClient.get(`/reports/aging-analysis/contracts/${contractId}/raw-voucher`, { params });
  return response.data;
}
