import apiClient from './client'
import type {
  Transaction,
  TransferRequest,
  DepositRequest,
  WithdrawRequest,
  PaginatedResponse,
  Account,
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

  getBySagaId: async (sagaId: string): Promise<Transaction> => {
    const response = await apiClient.get<Transaction>(`/api/v1/transactions/saga/${sagaId}`)
    return response.data
  },

  getByUserId: async (userId: string): Promise<Transaction[]> => {
    const response = await apiClient.get<Transaction[]>(`/api/v1/transactions/user/${userId}`)
    return response.data
  },

  getByAccountId: async (accountId: string): Promise<Transaction[]> => {
    const response = await apiClient.get<Transaction[]>(`/api/v1/transactions/account/${accountId}`)
    return response.data
  },

  transfer: async (data: TransferRequest): Promise<Transaction> => {
    // First, get the destination account ID from account number
    const accountResponse = await apiClient.get<Account>(`/api/v1/accounts/number/${data.toAccountNumber}`)
    const toAccountId = accountResponse.data.id

    // Generate idempotency key for transfer
    const idempotencyKey = crypto.randomUUID()

    // Transfer is initiated via POST /api/v1/transactions
    const response = await apiClient.post<Transaction>('/api/v1/transactions', {
      fromAccountId: data.fromAccountId,
      toAccountId: toAccountId,
      amount: data.amount,
      description: data.description,
      currency: 'TRY',
      idempotencyKey: idempotencyKey,
    })
    return response.data
  },

  deposit: async (data: DepositRequest): Promise<Transaction> => {
    // Deposit via account service - returns AccountResponse, we create a synthetic Transaction
    const response = await apiClient.post<Account>(
      `/api/v1/accounts/${data.accountId}/deposit`,
      { amount: data.amount, description: data.description }
    )
    // Return a synthetic transaction object for UI consistency
    return {
      id: crypto.randomUUID(),
      accountId: data.accountId,
      type: 'DEPOSIT',
      amount: data.amount,
      currency: response.data.currency || 'TRY',
      status: 'COMPLETED',
      description: data.description,
      createdAt: new Date().toISOString(),
    } as Transaction
  },

  withdraw: async (data: WithdrawRequest): Promise<Transaction> => {
    // Withdraw via account service - returns AccountResponse, we create a synthetic Transaction
    const response = await apiClient.post<Account>(
      `/api/v1/accounts/${data.accountId}/withdraw`,
      { amount: data.amount, description: data.description }
    )
    // Return a synthetic transaction object for UI consistency
    return {
      id: crypto.randomUUID(),
      accountId: data.accountId,
      type: 'WITHDRAWAL',
      amount: data.amount,
      currency: response.data.currency || 'TRY',
      status: 'COMPLETED',
      description: data.description,
      createdAt: new Date().toISOString(),
    } as Transaction
  },
}
