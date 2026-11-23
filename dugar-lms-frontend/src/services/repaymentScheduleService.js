import apiClient from '../api/apiClient';

export async function fetchRepaymentSchedulePdf(contractId) {
  try {
    const response = await apiClient.get(`/contracts/${encodeURIComponent(contractId)}/repayment-schedule/pdf`, {
      responseType: 'blob',
    });
    return response.data;
  } catch (error) {
    let message;
    const data = error?.response?.data;
    if (data instanceof Blob) {
      try { message = JSON.parse(await data.text()).message; } catch { /* Use status fallback below. */ }
    } else {
      message = data?.message;
    }
    throw new Error(message || (error?.response?.status === 401
      ? 'Your session has expired. Please sign in again.'
      : 'Unable to generate the repayment schedule. Please try again.'));
  }
}

// Open synchronously during the click, before awaiting the authenticated request.
export async function openRepaymentSchedulePdf(contractId, fetchPdf = fetchRepaymentSchedulePdf) {
  const tab = window.open('', '_blank');
  if (!tab) throw new Error('Allow pop-ups for this site to open the repayment schedule.');
  tab.opener = null;
  tab.document.title = 'Repayment Schedule';
  tab.document.body.textContent = 'Generating repayment schedule…';
  let url;
  try {
    const blob = await fetchPdf(contractId);
    if (tab.closed) return;
    url = URL.createObjectURL(blob);
    tab.location.replace(url);
    // Retain the blob for the lifetime of the viewer so printing/downloading works.
    const timer = window.setInterval(() => {
      if (tab.closed) {
        URL.revokeObjectURL(url);
        window.clearInterval(timer);
      }
    }, 1000);
  } catch (error) {
    if (url) URL.revokeObjectURL(url);
    if (!tab.closed) tab.close();
    throw error;
  }
}
