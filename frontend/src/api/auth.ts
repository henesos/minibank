import apiClient from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '../types'

export const authApi = {
  login: async (credentials: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/api/v1/users/login', credentials)
    return response.data
  },

  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/api/v1/users/register', data)
    return response.data
  },

  refreshToken: async (refreshToken: string): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/api/v1/users/refresh', {
      refreshToken,
    })
    return response.data
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await apiClient.get<User>('/api/v1/users/me')
    return response.data
  },

  getUserById: async (id: string): Promise<User> => {
    const response = await apiClient.get<User>(`/api/v1/users/${id}`)
    return response.data
  },

  updateProfile: async (id: string, data: Partial<User>): Promise<User> => {
    const response = await apiClient.put<User>(`/api/v1/users/${id}`, data)
    return response.data
  },

  deleteAccount: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/v1/users/${id}`)
  },

  verifyEmail: async (id: string): Promise<User> => {
    const response = await apiClient.post<User>(`/api/v1/users/${id}/verify-email`)
    return response.data
  },

  verifyPhone: async (id: string): Promise<User> => {
    const response = await apiClient.post<User>(`/api/v1/users/${id}/verify-phone`)
    return response.data
  },

  logout: (): void => {
    useAuthStore.getState().logout()
  },
}

// Import at the end to avoid circular dependency
import { useAuthStore } from '../store/authStore'
