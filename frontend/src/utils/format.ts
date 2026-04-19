export const formatCurrency = (amount: number, currency: string = 'USD', locale: string = 'en-US'): string =>
  new Intl.NumberFormat(locale, { style: 'currency', currency }).format(amount);

export const formatDate = (date: string | Date, options?: Intl.DateTimeFormatOptions): string => {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric', ...options });
};

export const formatDateTime = (date: string | Date, options?: Intl.DateTimeFormatOptions): string => {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', ...options });
};

export const formatAccountNumber = (accountNumber: string): string => {
  if (!accountNumber || accountNumber.length < 8) return accountNumber;
  return `${accountNumber.slice(0, 4)}${'*'.repeat(accountNumber.length - 8)}${accountNumber.slice(-4)}`;
};

export const capitalize = (text: string): string => text.charAt(0).toUpperCase() + text.slice(1).toLowerCase();

export const getTransactionColor = (type: string): string => {
  const colors: Record<string, string> = { DEPOSIT: 'text-green-600', WITHDRAWAL: 'text-red-600', TRANSFER: 'text-blue-600', PAYMENT: 'text-purple-600' };
  return colors[type] || 'text-gray-600';
};

export const getStatusColor = (status: string): string => {
  const colors: Record<string, string> = { ACTIVE: 'bg-green-100 text-green-800', INACTIVE: 'bg-gray-100 text-gray-800', SUSPENDED: 'bg-yellow-100 text-yellow-800', FROZEN: 'bg-blue-100 text-blue-800', CLOSED: 'bg-red-100 text-red-800', PENDING: 'bg-yellow-100 text-yellow-800', COMPLETED: 'bg-green-100 text-green-800', FAILED: 'bg-red-100 text-red-800', CANCELLED: 'bg-gray-100 text-gray-800' };
  return colors[status] || 'bg-gray-100 text-gray-800';
};
