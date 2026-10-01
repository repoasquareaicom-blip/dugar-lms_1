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
  addParam(params, 'overdueInstallmentCount', filters.overdueInstallmentCount);
  addParam(params, 'reportType', filters.reportType);
  return params;
}

export async function fetchDemandList(filters) {
  const response = await apiClient.get('/reports/demand-list', {
    params: paramsFrom(filters),
  });
  return response.data;
}

export async function fetchDemandListPrint(filters) {
  const response = await apiClient.get('/reports/demand-list/print', {
    params: paramsFrom(filters),
  });
  return response.data;
}

export async function fetchDemandComments(contractId) {
  const response = await apiClient.get(`/reports/demand-list/contracts/${contractId}/comments`);
  return response.data;
}

export async function addDemandComment(contractId, payload) {
  const response = await apiClient.post(`/reports/demand-list/contracts/${contractId}/comments`, payload);
  return response.data;
}

export async function fetchDemandPtps(contractId) {
  const response = await apiClient.get(`/reports/demand-list/contracts/${contractId}/ptps`);
  return response.data;
}

export async function addDemandPtp(contractId, payload) {
  const response = await apiClient.post(`/reports/demand-list/contracts/${contractId}/ptps`, payload);
  return response.data;
}
