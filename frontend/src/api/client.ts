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

// Track if we're refreshing the token
let isRefreshing = false
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (error: Error) => void
}> = []

const processQueue = (error: Error | null, token: string | null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token!)
    }
  })
  failedQueue = []
}

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

// Response interceptor - handle errors and token refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    // Handle 401 - unauthorized
    if (error.response?.status === 401 && !originalRequest._retry) {
      // If we have a refresh token, try to refresh
      const refreshToken = useAuthStore.getState().refreshToken

      if (refreshToken) {
        if (isRefreshing) {
          // Wait for the token refresh to complete
          return new Promise((resolve, reject) => {
            failedQueue.push({ resolve, reject })
          })
            .then((token) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              return apiClient(originalRequest)
            })
            .catch((err) => Promise.reject(err))
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          // Call refresh token endpoint
          const response = await axios.post(`${API_BASE_URL}/api/v1/users/refresh`, {
            refreshToken,
          })

          const { accessToken, refreshToken: newRefreshToken } = response.data

          // Update store with new tokens
          useAuthStore.getState().setToken(accessToken, newRefreshToken)

          processQueue(null, accessToken)

          // Retry original request with new token
          originalRequest.headers.Authorization = `Bearer ${accessToken}`
          return apiClient(originalRequest)
        } catch (refreshError) {
          processQueue(refreshError as Error, null)
          // Refresh failed, logout user
          useAuthStore.getState().logout()
          window.location.href = '/login'
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      } else {
        // No refresh token, logout
        useAuthStore.getState().logout()
        window.location.href = '/login'
      }
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
