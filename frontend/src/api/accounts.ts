import apiClient from './client'
import type { Account, CreateAccountRequest, PaginatedResponse, Transaction } from '../types'

export const accountsApi = {
  getAll: async (): Promise<Account[]> => {
    const response = await apiClient.get<Account[]>('/api/v1/accounts')
    return response.data
  },

  getById: async (id: string): Promise<Account> => {
    const response = await apiClient.get<Account>(`/api/v1/accounts/${id}`)
    return response.data
  },

  create: async (data: CreateAccountRequest): Promise<Account> => {
    const response = await apiClient.post<Account>('/api/v1/accounts', data)
    return response.data
  },

  getTransactions: async (accountId: string, page = 0, size = 10): Promise<PaginatedResponse<Transaction>> => {
    const response = await apiClient.get<PaginatedResponse<Transaction>>(
      `/api/v1/accounts/${accountId}/transactions`,
      { params: { page, size } }
    )
    return response.data
  },
}
