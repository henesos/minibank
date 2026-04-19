import apiClient from './client'
import type {
  Transaction,
  TransferRequest,
  DepositRequest,
  WithdrawRequest,
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
    const response = await apiClient.get<Transaction>(`/api/v1/transactions/${id}`)
    return response.data
  },

  transfer: async (data: TransferRequest): Promise<Transaction> => {
    // Transfer is initiated via POST /api/v1/transactions
    const response = await apiClient.post<Transaction>('/api/v1/transactions', {
      fromAccountId: data.fromAccountId,
      toAccountId: data.toAccountNumber, // Note: toAccountNumber is actually accountId
      amount: data.amount,
      description: data.description,
      currency: 'TRY',
    })
    return response.data
  },

  deposit: async (data: DepositRequest): Promise<Transaction> => {
    // Deposit via account service
    const response = await apiClient.post<Transaction>(
      `/api/v1/accounts/${data.accountId}/deposit`,
      { amount: data.amount, description: data.description }
    )
    return response.data
  },

  withdraw: async (data: WithdrawRequest): Promise<Transaction> => {
    // Withdraw via account service
    const response = await apiClient.post<Transaction>(
      `/api/v1/accounts/${data.accountId}/withdraw`,
      { amount: data.amount, description: data.description }
    )
    return response.data
  },
}
