import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../store/authStore'
import type { ApiError } from '../types'

// In Docker, nginx proxies /api to gateway
// In development, use vite proxy or direct connection
const API_BASE_URL = import.meta.env.VITE_API_URL || ''

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000,
})

// Request interceptor - add auth token
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().token
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor - handle errors
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    // Handle 401 - unauthorized
    if (error.response?.status === 401) {
      useAuthStore.getState().logout()
      window.location.href = '/login'
    }

    // Transform error
    const apiError: ApiError = {
      status: error.response?.status || 500,
      message: error.response?.data?.message || error.message || 'An unexpected error occurred',
      errors: error.response?.data?.errors,
    }

    return Promise.reject(apiError)
  }
)

export default apiClient
