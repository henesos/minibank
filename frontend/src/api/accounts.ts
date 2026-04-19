import apiClient from './client'
import type { Account, CreateAccountRequest, ApiResponse, PaginatedResponse, Transaction } from '../types'

export const accountsApi = {
  getAll: async (): Promise<Account[]> => {
    const response = await apiClient.get<ApiResponse<Account[]>>('/api/v1/accounts')
    return response.data.data
  },

  getById: async (id: string): Promise<Account> => {
    const response = await apiClient.get<ApiResponse<Account>>(`/api/v1/accounts/${id}`)
    return response.data.data
  },

  create: async (data: CreateAccountRequest): Promise<Account> => {
    const response = await apiClient.post<ApiResponse<Account>>('/api/v1/accounts', data)
    return response.data.data
  },

  getTransactions: async (accountId: string, page = 0, size = 10): Promise<PaginatedResponse<Transaction>> => {
    const response = await apiClient.get<PaginatedResponse<Transaction>>(
      `/api/v1/accounts/${accountId}/transactions`,
      { params: { page, size } }
    )
    return response.data
  },
}
