// User types
export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  fullName?: string
  phone?: string
  status: 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'LOCKED'
  emailVerified: boolean
  phoneVerified: boolean
  lastLoginAt?: string
  createdAt: string
}

// Auth types
export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  phone?: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: User
}

// Account types
export interface Account {
  id: string
  accountNumber: string
  userId: string
  accountType: 'CHECKING' | 'SAVINGS' | 'BUSINESS'
  balance: number
  currency: string
  status: 'PENDING' | 'ACTIVE' | 'DORMANT' | 'SUSPENDED' | 'CLOSED'
  createdAt: string
  updatedAt?: string
}

export interface CreateAccountRequest {
  accountType: 'CHECKING' | 'SAVINGS' | 'BUSINESS'
  currency?: string
}

// Transaction types
export interface Transaction {
  id: string
  accountId?: string  // For DEPOSIT/WITHDRAWAL (synthetic transactions)
  type: 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER' | 'PAYMENT'
  amount: number
  currency: string
  status: 'PENDING' | 'PROCESSING' | 'DEBITED' | 'COMPLETED' | 'FAILED' | 'COMPENSATING' | 'COMPENSATED' | 'CANCELLED'
  description?: string
  fromAccountId?: string
  toAccountId?: string
  fromUserId?: string
  toUserId?: string
  referenceNumber?: string
  createdAt: string
  completedAt?: string
  failureReason?: string
}

export interface TransferRequest {
  fromAccountId: string
  toAccountNumber: string
  amount: number
  description?: string
}

export interface DepositRequest {
  accountId: string
  amount: number
  description?: string
}

export interface WithdrawRequest {
  accountId: string
  amount: number
  description?: string
}

// API Response types
export interface ApiResponse<T> {
  data: T
  message?: string
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface ApiError {
  status: number
  message: string
  errors?: Record<string, string[]>
}
