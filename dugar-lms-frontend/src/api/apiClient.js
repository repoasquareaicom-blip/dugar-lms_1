import axios from 'axios';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

export function getApiBaseUrl() {
  return API_BASE_URL;
}

export function clearAuthSession() {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
}

function normalizeToken(value) {
  const token = String(value || '').trim();
  if (!token || token === 'undefined' || token === 'null') return '';
  return token.replace(/^Bearer\s+/i, '').trim();
}

export function saveCleanAuthToken(token) {
  const cleanToken = normalizeToken(token);
  if (cleanToken) {
    localStorage.setItem('token', cleanToken);
  } else {
    localStorage.removeItem('token');
  }
  return cleanToken;
}

export function getStoredAuthToken() {
  const token = saveCleanAuthToken(localStorage.getItem('token'));
  if (token) return token;

  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}');
    return saveCleanAuthToken(user?.token);
  } catch {
    return '';
  }
}

apiClient.interceptors.request.use((config) => {
  const token = getStoredAuthToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      clearAuthSession();
    }
    return Promise.reject(error);
  },
);

export default apiClient;
