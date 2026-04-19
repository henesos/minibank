export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
  createdAt: string;
  updatedAt: string;
}

export interface LoginRequest { email: string; password: string; }
export interface RegisterRequest { email: string; password: string; firstName: string; lastName: string; phoneNumber?: string; }
export interface AuthResponse { token: string; user: User; }

export interface Account {
  id: string;
  userId: string;
  accountNumber: string;
  accountType: 'CHECKING' | 'SAVINGS' | 'BUSINESS';
  currency: string;
  balance: number;
  availableBalance: number;
  status: 'ACTIVE' | 'INACTIVE' | 'FROZEN' | 'CLOSED';
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  accountType: 'CHECKING' | 'SAVINGS' | 'BUSINESS';
  currency?: string;
  initialDeposit?: number;
}

export interface Transaction {
  id: string;
  accountId: string;
  type: 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER' | 'PAYMENT';
  amount: number;
  currency: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  description?: string;
  referenceNumber: string;
  toAccountNumber?: string;
  fromAccountNumber?: string;
  createdAt: string;
  completedAt?: string;
}

export interface CreateTransactionRequest {
  accountId: string;
  type: 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER' | 'PAYMENT';
  amount: number;
  description?: string;
  toAccountNumber?: string;
}

export interface TransactionFilter {
  accountId?: string;
  type?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}

export interface ApiResponse<T> { data: T; message?: string; }
export interface PaginatedResponse<T> { content: T[]; totalElements: number; totalPages: number; size: number; number: number; first: boolean; last: boolean; }
export interface ApiError { status: number; message: string; errors?: Record<string, string[]>; }
