import apiClient from '../api/apiClient';

export async function fetchWordTemplateGroups(params = {}) {
  const response = await apiClient.get('/word-templates/groups', { params });
  return response.data;
}

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
  const filename = match?.[1] || 'generated-word-document.docx';
  const url = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export async function createWordTemplateGroup(payload) {
  const response = await apiClient.post('/word-templates/groups', payload);
  return response.data;
}

export async function updateWordTemplateGroup(id, payload) {
  const response = await apiClient.put(`/word-templates/groups/${encodeURIComponent(id)}`, payload);
  return response.data;
}

export async function deleteWordTemplateGroup(id) {
  await apiClient.delete(`/word-templates/groups/${encodeURIComponent(id)}`);
}

export async function fetchWordTemplates(params = {}) {
  const response = await apiClient.get('/word-templates', { params });
  return response.data;
}

export async function createWordTemplate(payload, onUploadProgress) {
  const formData = new FormData();
  formData.append('groupId', payload.groupId);
  formData.append('templateName', payload.templateName);
  formData.append('displayOrder', payload.displayOrder ?? 0);
  formData.append('isActive', payload.isActive !== false);
  formData.append('file', payload.file);
  const response = await apiClient.post('/word-templates', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
  });
  return response.data;
}

export async function updateWordTemplate(id, payload) {
  const response = await apiClient.put(`/word-templates/${encodeURIComponent(id)}`, payload);
  return response.data;
}

export async function replaceWordTemplateFile(id, file, onUploadProgress) {
  const formData = new FormData();
  formData.append('file', file);
  const response = await apiClient.post(`/word-templates/${encodeURIComponent(id)}/file`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
  });
  return response.data;
}

export async function deleteWordTemplate(id) {
  await apiClient.delete(`/word-templates/${encodeURIComponent(id)}`);
}

export async function downloadWordTemplate(id, filename) {
  const response = await apiClient.get(`/word-templates/${encodeURIComponent(id)}/download`, {
    responseType: 'blob',
  });
  const url = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename || 'word-template.docx';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
