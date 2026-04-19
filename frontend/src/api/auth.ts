import apiClient from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, User, ApiResponse } from '../types'

export const authApi = {
  login: async (credentials: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/api/v1/users/login', credentials)
    return response.data
  },

  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/api/v1/users/register', data)
    return response.data
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await apiClient.get<ApiResponse<User>>('/api/v1/users/me')
    return response.data.data
  },

  updateProfile: async (id: string, data: Partial<User>): Promise<User> => {
    const response = await apiClient.put<ApiResponse<User>>(`/api/v1/users/${id}`, data)
    return response.data.data
  },

  logout: (): void => {
    useAuthStore.getState().logout()
  },
}

// Import at the end to avoid circular dependency
import { useAuthStore } from '../store/authStore'
