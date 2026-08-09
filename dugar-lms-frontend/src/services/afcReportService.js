import apiClient from '../api/apiClient';

function paramsFrom(filters = {}) {
  return {
    loanNumber: filters.loanNumber,
    asOnDate: filters.asOnDate,
    areaCode: filters.areaCode,
  };
}

export async function fetchAfcReport(filters) {
  const response = await apiClient.get('/reports/afc', { params: paramsFrom(filters) });
  return response.data;
}

export async function fetchAfcReportPrint(filters) {
  const response = await apiClient.get('/reports/afc/print', { params: paramsFrom(filters) });
  return response.data;
}
