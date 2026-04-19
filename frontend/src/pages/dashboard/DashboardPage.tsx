import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Wallet, TrendingUp, TrendingDown, ArrowUpRight, ArrowDownLeft, CreditCard, DollarSign } from 'lucide-react';
import { Card, Badge, Loading } from '../../components/common';
import { accountsApi, transactionsApi } from '../../api';
import { formatCurrency, formatDateTime, getTransactionColor } from '../../utils';
import { Link } from 'react-router-dom';

const DashboardPage: React.FC = () => {
  const { data: accounts, isLoading: accountsLoading } = useQuery({ queryKey: ['accounts'], queryFn: accountsApi.getAll });
  const { data: transactions, isLoading: transactionsLoading } = useQuery({ queryKey: ['transactions', { size: 5 }], queryFn: () => transactionsApi.getAll({ size: 5 }) });

  if (accountsLoading || transactionsLoading) return <div className="flex items-center justify-center h-64"><Loading size="lg" text="Loading dashboard..." /></div>;

  const totalBalance = accounts?.reduce((sum, acc) => sum + acc.balance, 0) || 0;
  const accountCount = accounts?.length || 0;
  const monthlyIncome = transactions?.content?.filter((t) => t.type === 'DEPOSIT' && t.status === 'COMPLETED').reduce((sum, t) => sum + t.amount, 0) || 0;
  const monthlyExpenses = transactions?.content?.filter((t) => (t.type === 'WITHDRAWAL' || t.type === 'TRANSFER' || t.type === 'PAYMENT') && t.status === 'COMPLETED').reduce((sum, t) => sum + t.amount, 0) || 0;

  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold text-gray-900">Dashboard</h1><p className="text-gray-500 mt-1">Welcome back! Here's your financial overview.</p></div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="bg-gradient-to-br from-primary-500 to-primary-700 text-white border-0">
          <div className="flex items-center justify-between">
            <div><p className="text-primary-100 text-sm">Total Balance</p><p className="text-2xl font-bold mt-1">{formatCurrency(totalBalance)}</p></div>
            <div className="w-12 h-12 bg-white/20 rounded-lg flex items-center justify-center"><DollarSign className="w-6 h-6" /></div>
          </div>
        </Card>
        <Card>
          <div className="flex items-center justify-between">
            <div><p className="text-gray-500 text-sm">Accounts</p><p className="text-2xl font-bold text-gray-900 mt-1">{accountCount}</p></div>
            <div className="w-12 h-12 bg-blue-50 rounded-lg flex items-center justify-center"><CreditCard className="w-6 h-6 text-blue-600" /></div>
          </div>
        </Card>
        <Card>
          <div className="flex items-center justify-between">
            <div><p className="text-gray-500 text-sm">Income (30d)</p><p className="text-2xl font-bold text-green-600 mt-1">{formatCurrency(monthlyIncome)}</p></div>
            <div className="w-12 h-12 bg-green-50 rounded-lg flex items-center justify-center"><TrendingUp className="w-6 h-6 text-green-600" /></div>
          </div>
        </Card>
        <Card>
          <div className="flex items-center justify-between">
            <div><p className="text-gray-500 text-sm">Expenses (30d)</p><p className="text-2xl font-bold text-red-600 mt-1">{formatCurrency(monthlyExpenses)}</p></div>
            <div className="w-12 h-12 bg-red-50 rounded-lg flex items-center justify-center"><TrendingDown className="w-6 h-6 text-red-600" /></div>
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card title="My Accounts" headerAction={<Link to="/accounts" className="text-primary-600 hover:text-primary-700 text-sm font-medium">View all</Link>}>
          {accounts && accounts.length > 0 ? (
            <div className="space-y-3">
              {accounts.slice(0, 3).map((account) => (
                <div key={account.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-primary-100 rounded-lg flex items-center justify-center"><Wallet className="w-5 h-5 text-primary-600" /></div>
                    <div><p className="font-medium text-gray-900">{account.accountType}</p><p className="text-sm text-gray-500">{account.accountNumber}</p></div>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold text-gray-900">{formatCurrency(account.balance, account.currency)}</p>
                    <Badge variant={account.status === 'ACTIVE' ? 'success' : 'warning'} size="sm">{account.status}</Badge>
                  </div>
                </div>
              ))}
            </div>
          ) : <div className="text-center py-8"><p className="text-gray-500">No accounts yet</p></div>}
        </Card>

        <Card title="Recent Transactions" headerAction={<Link to="/transactions" className="text-primary-600 hover:text-primary-700 text-sm font-medium">View all</Link>}>
          {transactions && transactions.content.length > 0 ? (
            <div className="space-y-3">
              {transactions.content.map((transaction) => (
                <div key={transaction.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                  <div className="flex items-center gap-3">
                    <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${transaction.type === 'DEPOSIT' ? 'bg-green-100' : transaction.type === 'WITHDRAWAL' ? 'bg-red-100' : 'bg-blue-100'}`}>
                      {transaction.type === 'DEPOSIT' ? <ArrowDownLeft className="w-5 h-5 text-green-600" /> : <ArrowUpRight className="w-5 h-5 text-red-600" />}
                    </div>
                    <div><p className={`font-medium ${getTransactionColor(transaction.type)}`}>{transaction.type}</p><p className="text-sm text-gray-500">{formatDateTime(transaction.createdAt)}</p></div>
                  </div>
                  <div className="text-right">
                    <p className={`font-semibold ${transaction.type === 'DEPOSIT' ? 'text-green-600' : 'text-red-600'}`}>{transaction.type === 'DEPOSIT' ? '+' : '-'}{formatCurrency(transaction.amount, transaction.currency)}</p>
                    <Badge variant={transaction.status === 'COMPLETED' ? 'success' : transaction.status === 'PENDING' ? 'warning' : 'danger'} size="sm">{transaction.status}</Badge>
                  </div>
                </div>
              ))}
            </div>
          ) : <div className="text-center py-8"><p className="text-gray-500">No transactions yet</p></div>}
        </Card>
      </div>

      <Card title="Quick Actions">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Link to="/transactions/new?type=deposit" className="flex flex-col items-center p-4 bg-green-50 rounded-lg hover:bg-green-100 transition-colors"><ArrowDownLeft className="w-6 h-6 text-green-600 mb-2" /><span className="text-sm font-medium text-gray-700">Deposit</span></Link>
          <Link to="/transactions/new?type=withdraw" className="flex flex-col items-center p-4 bg-red-50 rounded-lg hover:bg-red-100 transition-colors"><ArrowUpRight className="w-6 h-6 text-red-600 mb-2" /><span className="text-sm font-medium text-gray-700">Withdraw</span></Link>
          <Link to="/transactions/new?type=transfer" className="flex flex-col items-center p-4 bg-blue-50 rounded-lg hover:bg-blue-100 transition-colors"><CreditCard className="w-6 h-6 text-blue-600 mb-2" /><span className="text-sm font-medium text-gray-700">Transfer</span></Link>
          <Link to="/accounts/new" className="flex flex-col items-center p-4 bg-purple-50 rounded-lg hover:bg-purple-100 transition-colors"><Wallet className="w-6 h-6 text-purple-600 mb-2" /><span className="text-sm font-medium text-gray-700">New Account</span></Link>
        </div>
      </Card>
    </div>
  );
};

export default DashboardPage;
