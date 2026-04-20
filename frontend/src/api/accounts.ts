import apiClient from './client'
import type { Account, CreateAccountRequest, PaginatedResponse, Transaction } from '../types'

export interface BalanceResponse {
  accountId: string
  balance: number
  availableBalance: number
}

export const accountsApi = {
  getAll: async (): Promise<Account[]> => {
    const response = await apiClient.get<Account[]>('/api/v1/accounts')
    return response.data
  },

  getById: async (id: string): Promise<Account> => {
    const response = await apiClient.get<Account>(`/api/v1/accounts/${id}`)
    return response.data
  },

  getByNumber: async (accountNumber: string): Promise<Account> => {
    const response = await apiClient.get<Account>(`/api/v1/accounts/number/${accountNumber}`)
    return response.data
  },

  getByUserId: async (userId: string): Promise<Account[]> => {
    const response = await apiClient.get<Account[]>(`/api/v1/accounts/user/${userId}`)
    return response.data
  },

  create: async (data: CreateAccountRequest): Promise<Account> => {
    const response = await apiClient.post<Account>('/api/v1/accounts', data)
    return response.data
  },

  getBalance: async (id: string): Promise<BalanceResponse> => {
    const response = await apiClient.get<BalanceResponse>(`/api/v1/accounts/${id}/balance`)
    return response.data
  },

  deposit: async (id: string, amount: number, description?: string): Promise<Account> => {
    const response = await apiClient.post<Account>(`/api/v1/accounts/${id}/deposit`, {
      amount,
      description,
    })
    return response.data
  },

  withdraw: async (id: string, amount: number, description?: string): Promise<Account> => {
    const response = await apiClient.post<Account>(`/api/v1/accounts/${id}/withdraw`, {
      amount,
      description,
    })
    return response.data
  },

  activate: async (id: string): Promise<Account> => {
    const response = await apiClient.post<Account>(`/api/v1/accounts/${id}/activate`)
    return response.data
  },

  suspend: async (id: string): Promise<Account> => {
    const response = await apiClient.post<Account>(`/api/v1/accounts/${id}/suspend`)
    return response.data
  },

  close: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/v1/accounts/${id}`)
  },

  getTransactions: async (accountId: string, page = 0, size = 10): Promise<PaginatedResponse<Transaction>> => {
    const response = await apiClient.get<PaginatedResponse<Transaction>>(
      `/api/v1/accounts/${accountId}/transactions`,
      { params: { page, size } }
    )
    return response.data
  },
}
