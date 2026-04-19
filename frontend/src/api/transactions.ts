import apiClient from './client'
import type {
  Transaction,
  TransferRequest,
  DepositRequest,
  WithdrawRequest,
  ApiResponse,
  PaginatedResponse,
} from '../types'

export interface TransactionFilter {
  accountId?: string
  type?: string
  status?: string
  page?: number
  size?: number
}

export const transactionsApi = {
  getAll: async (filter?: TransactionFilter): Promise<PaginatedResponse<Transaction>> => {
    const response = await apiClient.get<PaginatedResponse<Transaction>>('/api/v1/transactions', {
      params: filter,
    })
    return response.data
  },

  getById: async (id: string): Promise<Transaction> => {
    const response = await apiClient.get<ApiResponse<Transaction>>(`/api/v1/transactions/${id}`)
    return response.data.data
  },

  transfer: async (data: TransferRequest): Promise<Transaction> => {
    const response = await apiClient.post<ApiResponse<Transaction>>('/api/v1/transactions/transfer', data)
    return response.data.data
  },

  deposit: async (data: DepositRequest): Promise<Transaction> => {
    const response = await apiClient.post<ApiResponse<Transaction>>('/api/v1/transactions/deposit', data)
    return response.data.data
  },

  withdraw: async (data: WithdrawRequest): Promise<Transaction> => {
    const response = await apiClient.post<ApiResponse<Transaction>>('/api/v1/transactions/withdraw', data)
    return response.data.data
  },
}
