export const formatCurrency = (amount: number, currency = 'USD'): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
  }).format(amount)
}

export const formatDate = (date: string | Date): string => {
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(date))
}

export const formatDateShort = (date: string | Date): string => {
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(new Date(date))
}

export const maskAccountNumber = (accountNumber: string): string => {
  if (accountNumber.length <= 4) return accountNumber
  return '****' + accountNumber.slice(-4)
}

export const getInitials = (firstName: string, lastName: string): string => {
  return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase()
}

export const getTransactionIcon = (type: string): string => {
  switch (type) {
    case 'DEPOSIT':
      return '↓'
    case 'WITHDRAWAL':
      return '↑'
    case 'TRANSFER':
      return '→'
    case 'PAYMENT':
      return '↗'
    default:
      return '•'
  }
}

export const getTransactionColor = (type: string): string => {
  switch (type) {
    case 'DEPOSIT':
      return 'text-green-600'
    case 'WITHDRAWAL':
      return 'text-red-600'
    case 'TRANSFER':
      return 'text-blue-600'
    case 'PAYMENT':
      return 'text-purple-600'
    default:
      return 'text-gray-600'
  }
}
