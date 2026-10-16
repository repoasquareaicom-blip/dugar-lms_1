import apiClient from '../api/apiClient';

export async function fetchStockStatementDate() {
  return (await apiClient.get('/reports/stock-statement/business-date')).data.businessDate;
}

export async function fetchStockStatement(bankId, asOnDate) {
  return (await apiClient.get('/reports/stock-statement', { params: { bankId, asOnDate } })).data;
}

export async function downloadStockStatement(report) {
  try {
    return (await apiClient.get('/reports/stock-statement/export', {
      params: { bankId: report.bankId, asOnDate: report.businessDate, fingerprint: report.fingerprint }, responseType: 'blob',
    })).data;
  } catch (error) {
    if (error.response?.data instanceof Blob) {
      try { error.message = JSON.parse(await error.response.data.text()).message || error.message; } catch { /* Keep HTTP message. */ }
    }
    throw error;
  }
}
