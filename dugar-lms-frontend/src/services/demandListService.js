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

export async function fetchDemandFollowUps(contractId) {
  const response = await apiClient.get(`/reports/demand-list/contracts/${contractId}/follow-ups`);
  return response.data;
}

export async function addDemandFollowUp(contractId, payload) {
  const response = await apiClient.post(`/reports/demand-list/contracts/${contractId}/follow-ups`, payload);
  return response.data;
}
