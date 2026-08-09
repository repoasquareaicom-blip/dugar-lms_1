import apiClient from '../api/apiClient';

function addParam(params, key, value) {
  if (value !== null && value !== undefined && value !== '') {
    params[key] = value;
  }
}

function paramsFrom(filters = {}, options = {}) {
  const params = {};
  addParam(params, 'asOnDate', filters.asOnDate);
  addParam(params, 'areaCode', filters.areaCode);
  addParam(params, 'contractNumber', filters.contractNumber);
  addParam(params, 'overdueInstallmentCount', filters.overdueInstallmentCount);
  addParam(params, 'page', options.page);
  addParam(params, 'size', options.size);
  addParam(params, 'sortColumn', options.sortColumn);
  addParam(params, 'sortDirection', options.sortDirection);
  return params;
}

export async function fetchDemandList(filters, options) {
  const response = await apiClient.get('/reports/demand-list', {
    params: paramsFrom(filters, options),
  });
  return response.data;
}

export async function fetchDemandListPrint(filters, options = {}) {
  const response = await apiClient.get('/reports/demand-list/print', {
    params: paramsFrom(filters, options),
  });
  return response.data;
}
