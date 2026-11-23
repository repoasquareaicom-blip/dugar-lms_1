import apiClient from '../api/apiClient';

export async function fetchWordPlaceholders(params = {}) {
  const response = await apiClient.get('/word-documents/placeholders', { params });
  return response.data;
}

export async function validateWordTemplate(id, params = {}) {
  const response = await apiClient.get(`/word-documents/templates/${encodeURIComponent(id)}/validation`, { params });
  return response.data;
}

export async function searchWordContracts(params = {}) {
  const response = await apiClient.get('/word-documents/contracts', { params });
  return response.data;
}

export async function generateWordDocument(payload) {
  const response = await apiClient.post('/word-documents/generate', payload, {
    responseType: 'blob',
  });
  const disposition = response.headers?.['content-disposition'] || '';
  const match = disposition.match(/filename="?([^";]+)"?/i);
  const filename = match?.[1] || 'generated-document.docx';
  const url = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export async function fetchWordTemplates(params = {}) {
  const response = await apiClient.get('/word-templates', { params });
  return response.data;
}

export const DOCUMENT_TEMPLATES_CHANGED = 'document-templates-changed';
function templatesChanged() {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new Event(DOCUMENT_TEMPLATES_CHANGED));
  try { window.localStorage.setItem(DOCUMENT_TEMPLATES_CHANGED, `${Date.now()}-${Math.random()}`); } catch { /* Storage may be disabled. */ }
}
export async function setWordTemplateActive(id, isActive) {
  const response = await apiClient.put(`/word-templates/${encodeURIComponent(id)}/active`, { isActive });
  templatesChanged();
  return response.data;
}

export async function replaceWordTemplateFile(id, file, onUploadProgress) {
  const formData = new FormData();
  formData.append('file', file);
  const response = await apiClient.post(`/word-templates/${encodeURIComponent(id)}/file`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
  });
  templatesChanged();
  return response.data;
}

export async function downloadWordTemplate(id, filename) {
  const response = await apiClient.get(`/word-templates/${encodeURIComponent(id)}/download`, {
    responseType: 'blob',
  });
  const url = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename || 'document-template.docx';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
