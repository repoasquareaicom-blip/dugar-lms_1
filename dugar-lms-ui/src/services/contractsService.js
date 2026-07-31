import apiClient from '../api/apiClient.js'

function withValue(target, key, value) {
  if (value !== null && value !== undefined && value !== '') {
    target[key] = value
  }
}

export async function fetchContractsPage({ filters = {}, keyword, page, pageSize, sortColumn, sortDirection }) {
  const params = { page, size: pageSize, sortColumn, sortDirection }

  withValue(params, 'keyword', keyword)
  withValue(params, 'branch', filters.branch)
  withValue(params, 'status', filters.status)
  withValue(params, 'product', filters.product)
  withValue(params, 'customerName', filters.customerName)
  withValue(params, 'contractDateFrom', filters.contractDateFrom)
  withValue(params, 'contractDateTo', filters.contractDateTo)
  withValue(params, 'minimumLoanAmount', filters.minimumLoanAmount)
  withValue(params, 'maximumLoanAmount', filters.maximumLoanAmount)

  const response = await apiClient.get('/contracts', { params })
  return response.data
}