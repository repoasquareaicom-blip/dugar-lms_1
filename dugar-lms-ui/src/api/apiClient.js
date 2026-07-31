import axios from 'axios'
import { getAccessToken } from '../utils/authStorage.js'

const API_BASE_URL = import.meta.env.VITE_API_URL

if (import.meta.env.DEV) {
  console.log('API Base URL:', API_BASE_URL)
}

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

export default apiClient
