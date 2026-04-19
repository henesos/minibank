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

  getCurrentUser: async (): Promise<User> => {
    // Backend returns UserResponse directly, not wrapped in ApiResponse
    const response = await apiClient.get<User>('/api/v1/users/me')
    return response.data
  },

  updateProfile: async (id: string, data: Partial<User>): Promise<User> => {
    // Backend returns UserResponse directly, not wrapped in ApiResponse
    const response = await apiClient.put<User>(`/api/v1/users/${id}`, data)
    return response.data
  },

  logout: (): void => {
    useAuthStore.getState().logout()
  },
}

// Import at the end to avoid circular dependency
import { useAuthStore } from '../store/authStore'
