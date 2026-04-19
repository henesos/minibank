import apiClient from './client';
import type { AuthResponse, LoginRequest, RegisterRequest, User, ApiResponse } from '../types';

export const authApi = {
  login: async (credentials: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/api/v1/users/login', credentials);
    return response.data;
  },
  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/api/v1/users/register', data);
    return response.data;
  },
  getCurrentUser: async (): Promise<User> => {
    const response = await apiClient.get<ApiResponse<User>>('/api/v1/users/me');
    return response.data.data;
  },
  updateProfile: async (data: Partial<User>): Promise<User> => {
    const response = await apiClient.put<ApiResponse<User>>('/api/v1/users/me', data);
    return response.data.data;
  },
  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    await apiClient.put('/api/v1/users/me/password', { currentPassword, newPassword });
  },
  logout: (): void => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  },
};
