import apiClient from '../api/apiClient';

export async function fetchContractDashboard({ disbursementPeriod = 'YTD', accountYear } = {}) {
  const params = { disbursementPeriod };
  if (accountYear) {
    params.accountYear = accountYear;
  }

  const response = await apiClient.get('/contracts/dashboard', {
    params,
  });
  return response.data || {};
}
