import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ArrowDownLeft, ArrowUpRight, Search, ChevronLeft, ChevronRight } from 'lucide-react';
import { Card, Badge, Button, Input, Loading } from '../../components/common';
import { transactionsApi } from '../../api';
import { formatCurrency, formatDateTime, capitalize } from '../../utils';
import type { TransactionFilter } from '../../types';

const TransactionsPage: React.FC = () => {
  const [filter, setFilter] = useState<TransactionFilter>({ size: 10, page: 0 });
  const [searchTerm, setSearchTerm] = useState('');

  const { data: transactions, isLoading: transactionsLoading } = useQuery({ queryKey: ['transactions', filter], queryFn: () => transactionsApi.getAll(filter) });

  if (transactionsLoading) return <div className="flex items-center justify-center h-64"><Loading size="lg" text="Loading transactions..." /></div>;

  const filteredTransactions = transactions?.content.filter((t) => !searchTerm || t.referenceNumber.toLowerCase().includes(searchTerm.toLowerCase()) || t.description?.toLowerCase().includes(searchTerm.toLowerCase()) || t.type.toLowerCase().includes(searchTerm.toLowerCase()));

  const handleFilterChange = (key: string, value: string) => setFilter((prev) => ({ ...prev, [key]: value, page: 0 }));
  const handlePageChange = (newPage: number) => setFilter((prev) => ({ ...prev, page: newPage }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-gray-900">Transactions</h1><p className="text-gray-500 mt-1">View and manage your transactions</p></div>
        <Link to="/transactions/new"><Button>New Transaction</Button></Link>
      </div>

      <Card>
        <div className="flex flex-col md:flex-row gap-4">
          <div className="flex-1"><Input placeholder="Search transactions..." leftIcon={<Search className="w-5 h-5" />} value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} /></div>
          <div className="flex gap-3">
            <select className="input w-auto" value={filter.type || ''} onChange={(e) => handleFilterChange('type', e.target.value)}><option value="">All Types</option><option value="DEPOSIT">Deposit</option><option value="WITHDRAWAL">Withdrawal</option><option value="TRANSFER">Transfer</option></select>
            <select className="input w-auto" value={filter.status || ''} onChange={(e) => handleFilterChange('status', e.target.value)}><option value="">All Status</option><option value="COMPLETED">Completed</option><option value="PENDING">Pending</option><option value="FAILED">Failed</option></select>
          </div>
        </div>
      </Card>

      <Card noPadding>
        {filteredTransactions && filteredTransactions.length > 0 ? (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Transaction</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Reference</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">Amount</th>
                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">Status</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {filteredTransactions.map((t) => (
                    <tr key={t.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-3">
                          <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${t.type === 'DEPOSIT' ? 'bg-green-100' : t.type === 'WITHDRAWAL' ? 'bg-red-100' : 'bg-blue-100'}`}>
                            {t.type === 'DEPOSIT' ? <ArrowDownLeft className="w-5 h-5 text-green-600" /> : <ArrowUpRight className="w-5 h-5 text-red-600" />}
                          </div>
                          <div><p className="font-medium text-gray-900">{capitalize(t.type)}</p><p className="text-sm text-gray-500 truncate max-w-xs">{t.description || '-'}</p></div>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap"><p className="text-sm text-gray-600 font-mono">{t.referenceNumber}</p></td>
                      <td className="px-6 py-4 whitespace-nowrap"><p className="text-sm text-gray-600">{formatDateTime(t.createdAt)}</p></td>
                      <td className="px-6 py-4 whitespace-nowrap text-right"><p className={`font-semibold ${t.type === 'DEPOSIT' ? 'text-green-600' : 'text-red-600'}`}>{t.type === 'DEPOSIT' ? '+' : '-'}{formatCurrency(t.amount, t.currency)}</p></td>
                      <td className="px-6 py-4 whitespace-nowrap text-right"><Badge variant={t.status === 'COMPLETED' ? 'success' : t.status === 'PENDING' ? 'warning' : 'danger'}>{capitalize(t.status)}</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {transactions && transactions.totalPages > 1 && (
              <div className="px-6 py-4 border-t border-gray-200 flex items-center justify-between">
                <p className="text-sm text-gray-500">Showing {transactions.number * transactions.size + 1} to {Math.min((transactions.number + 1) * transactions.size, transactions.totalElements)} of {transactions.totalElements}</p>
                <div className="flex gap-2">
                  <Button variant="secondary" size="sm" disabled={transactions.first} onClick={() => handlePageChange(transactions.number - 1)}><ChevronLeft className="w-4 h-4" /></Button>
                  <Button variant="secondary" size="sm" disabled={transactions.last} onClick={() => handlePageChange(transactions.number + 1)}><ChevronRight className="w-4 h-4" /></Button>
                </div>
              </div>
            )}
          </>
        ) : <div className="text-center py-12"><p className="text-gray-500">No transactions found</p></div>}
      </Card>
    </div>
  );
};

export default TransactionsPage;
