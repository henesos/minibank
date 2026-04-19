import apiClient from './client';
import type { Transaction, CreateTransactionRequest, TransactionFilter, ApiResponse, PaginatedResponse } from '../types';

export const transactionsApi = {
  getAll: async (filter?: TransactionFilter): Promise<PaginatedResponse<Transaction>> => {
    const response = await apiClient.get<PaginatedResponse<Transaction>>('/api/v1/transactions', { params: filter });
    return response.data;
  },
  getById: async (id: string): Promise<Transaction> => {
    const response = await apiClient.get<ApiResponse<Transaction>>(`/api/v1/transactions/${id}`);
    return response.data.data;
  },
  create: async (data: CreateTransactionRequest): Promise<Transaction> => {
    const response = await apiClient.post<ApiResponse<Transaction>>('/api/v1/transactions', data);
    return response.data.data;
  },
  transfer: async (data: { fromAccountId: string; toAccountNumber: string; amount: number; description?: string; }): Promise<Transaction> => {
    const response = await apiClient.post<ApiResponse<Transaction>>('/api/v1/transactions/transfer', data);
    return response.data.data;
  },
  deposit: async (accountId: string, amount: number, description?: string): Promise<Transaction> => {
    const response = await apiClient.post<ApiResponse<Transaction>>('/api/v1/transactions/deposit', { accountId, amount, description });
    return response.data.data;
  },
  withdraw: async (accountId: string, amount: number, description?: string): Promise<Transaction> => {
    const response = await apiClient.post<ApiResponse<Transaction>>('/api/v1/transactions/withdraw', { accountId, amount, description });
    return response.data.data;
  },
};
