import apiClient from '../api/apiClient';

function addParam(params, key, value) {
  if (value !== null && value !== undefined && value !== '') {
    params[key] = value;
  }
}

export async function fetchContractsPage({
  filters = {},
  keyword = '',
  page = 0,
  pageSize = 25,
  sortColumn = 'contractDate',
  sortDirection = 'desc',
  isDraft,
  workflowStatus,
}) {
  const params = {
    page,
    size: pageSize,
    sortColumn,
    sortDirection,
  };

  addParam(params, 'keyword', keyword);
  addParam(params, 'branch', filters.branch);
  addParam(params, 'status', filters.status);
  addParam(params, 'product', filters.product);
  addParam(params, 'customerName', filters.customerName);
  addParam(params, 'contractDateFrom', filters.contractDateFrom);
  addParam(params, 'contractDateTo', filters.contractDateTo);
  addParam(params, 'minimumLoanAmount', filters.minimumLoanAmount);
  addParam(params, 'maximumLoanAmount', filters.maximumLoanAmount);
  addParam(params, 'isDraft', isDraft);
  addParam(params, 'workflowStatus', workflowStatus);

  const response = await apiClient.get('/contracts', { params });
  return response.data;
}

export async function saveContractPartyDraft({ contractId, contractNumber, contractDate, parties }) {
  const response = await apiClient.post('/contracts/draft/parties', { contractId, contractNumber, contractDate, parties });
  return response.data;
}

export async function fetchContractAreas({ keyword = '', limit = 20 } = {}) {
  const options = await fetchContractAreaOptions({ keyword, limit });
  return options.map((area) => area.areaCode);
}

export async function fetchContractAreaOptions({ keyword = '', limit = 20 } = {}) {
  const params = {};
  addParam(params, 'keyword', keyword);
  addParam(params, 'limit', limit);
  const response = await apiClient.get('/contracts/areas', { params });
  const rows = Array.isArray(response.data) ? response.data : response.data?.content || [];
  const areas = rows
    .map((area) => {
      if (area && typeof area === 'object') {
        const areaCode = String(area.areaCode || area.area_code || '').trim();
        const areaName = String(area.areaName || area.area_name || '').trim();
        return areaCode ? { areaCode, areaName } : null;
      }
      const areaCode = String(area || '').trim();
      return areaCode ? { areaCode, areaName: '' } : null;
    })
    .filter(Boolean)
    .filter((area, index, list) => list.findIndex((item) => item.areaCode === area.areaCode) === index)
    .sort((left, right) => left.areaCode.localeCompare(right.areaCode));
  return areas.slice(0, Math.max(1, Math.min(limit, 50)));
}

export async function saveContractHeaderDraft(contractId, header) {
  const response = await apiClient.post(`/contracts/draft/${contractId}/header`, header);
  return response.data;
}

export async function fetchContractPartyDraft(contractId) {
  const response = await apiClient.get(`/contracts/draft/${contractId}/parties`);
  return response.data;
}

export async function fetchContractAssetDraft(contractId) {
  const response = await apiClient.get(`/contracts/draft/${contractId}/asset`);
  return response.data || {};
}

export async function saveContractAssetDraft(contractId, asset) {
  const response = await apiClient.post(`/contracts/draft/${contractId}/asset`, asset);
  return response.data;
}

export async function fetchContractFinancialDraft(contractId) {
  const response = await apiClient.get(`/contracts/draft/${contractId}/financial`);
  return response.data || {};
}

export async function saveContractFinancialDraft(contractId, financial) {
  const response = await apiClient.post(`/contracts/draft/${contractId}/financial`, financial);
  return response.data;
}

export async function fetchContractDocumentationDraft(contractId) {
  const response = await apiClient.get(`/contracts/draft/${contractId}/documentation`);
  return response.data || {};
}

export async function saveContractDocumentationDraft(contractId, documentation) {
  const response = await apiClient.post(`/contracts/draft/${contractId}/documentation`, documentation);
  return response.data;
}

export async function fetchContractCoLendingDraft(contractId) {
  const response = await apiClient.get(`/contracts/draft/${contractId}/co-lending`);
  return response.data || {};
}

export async function saveContractCoLendingDraft(contractId, coLending) {
  const response = await apiClient.post(`/contracts/draft/${contractId}/co-lending`, coLending);
  return response.data;
}

export async function submitContractForEdit(contractId) {
  await apiClient.post(`/contracts/draft/${contractId}/submit-for-edit`);
}

export async function submitContractToActive(contractId) {
  await apiClient.post(`/contracts/draft/${contractId}/submit-to-active`);
}

export async function updateContractStatus(contractId, status) {
  await apiClient.post(`/contracts/draft/${contractId}/status`, { status });
}
