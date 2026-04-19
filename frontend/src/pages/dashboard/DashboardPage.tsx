import React from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Wallet,
  TrendingUp,
  TrendingDown,
  ArrowUpRight,
  ArrowDownLeft,
  CreditCard,
} from 'lucide-react'
import { Card, Button, Spinner } from '../../components/common'
import { accountsApi, transactionsApi } from '../../api'
import { useAuth } from '../../hooks'
import { formatCurrency, formatDateShort } from '../../utils'
import { useNavigate } from 'react-router-dom'

const DashboardPage: React.FC = () => {
  const navigate = useNavigate()
  const { user } = useAuth()

  const { data: accounts, isLoading: accountsLoading } = useQuery({
    queryKey: ['accounts'],
    queryFn: accountsApi.getAll,
  })

  const { data: transactions, isLoading: transactionsLoading } = useQuery({
    queryKey: ['transactions'],
    queryFn: () => transactionsApi.getAll({ size: 5 }),
  })

  const totalBalance = accounts?.reduce((sum, acc) => sum + acc.balance, 0) || 0
  const activeAccounts = accounts?.filter((a) => a.status === 'ACTIVE').length || 0

  return (
    <div className="space-y-6">
      {/* Welcome section */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">
          Welcome back, {user?.firstName}!
        </h1>
        <p className="text-gray-500 mt-1">
          Here's your financial overview
        </p>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card>
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-gray-500">Total Balance</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">
                {formatCurrency(totalBalance)}
              </p>
            </div>
            <div className="p-3 bg-primary-50 rounded-xl">
              <Wallet className="w-6 h-6 text-primary-600" />
            </div>
          </div>
        </Card>

        <Card>
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-gray-500">Active Accounts</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">
                {activeAccounts}
              </p>
            </div>
            <div className="p-3 bg-green-50 rounded-xl">
              <CreditCard className="w-6 h-6 text-green-600" />
            </div>
          </div>
        </Card>

        <Card>
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-gray-500">This Month</p>
              <div className="flex items-center gap-2 mt-1">
                <TrendingUp className="w-5 h-5 text-green-500" />
                <span className="text-2xl font-bold text-gray-900">+12%</span>
              </div>
            </div>
            <div className="p-3 bg-blue-50 rounded-xl">
              <TrendingDown className="w-6 h-6 text-blue-600" />
            </div>
          </div>
        </Card>
      </div>

      {/* Accounts and Transactions */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Accounts */}
        <Card>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-900">My Accounts</h2>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => navigate('/accounts')}
            >
              View all
            </Button>
          </div>

          {accountsLoading ? (
            <div className="flex justify-center py-8">
              <Spinner />
            </div>
          ) : accounts && accounts.length > 0 ? (
            <div className="space-y-3">
              {accounts.slice(0, 3).map((account) => (
                <div
                  key={account.id}
                  className="flex items-center justify-between p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors cursor-pointer"
                  onClick={() => navigate('/accounts')}
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-10 h-10 rounded-full flex items-center justify-center ${
                        account.accountType === 'CHECKING'
                          ? 'bg-blue-100 text-blue-600'
                          : account.accountType === 'SAVINGS'
                          ? 'bg-green-100 text-green-600'
                          : 'bg-purple-100 text-purple-600'
                      }`}
                    >
                      <Wallet className="w-5 h-5" />
                    </div>
                    <div>
                      <p className="font-medium text-gray-900">
                        {account.accountType.charAt(0) +
                          account.accountType.slice(1).toLowerCase()}{' '}
                        Account
                      </p>
                      <p className="text-sm text-gray-500">
                        ****{account.accountNumber.slice(-4)}
                      </p>
                    </div>
                  </div>
                  <p className="font-semibold text-gray-900">
                    {formatCurrency(account.balance)}
                  </p>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-8">
              <Wallet className="w-12 h-12 text-gray-300 mx-auto mb-3" />
              <p className="text-gray-500">No accounts yet</p>
              <Button
                className="mt-3"
                onClick={() => navigate('/accounts')}
              >
                Create Account
              </Button>
            </div>
          )}
        </Card>

        {/* Recent Transactions */}
        <Card>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              Recent Transactions
            </h2>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => navigate('/transactions')}
            >
              View all
            </Button>
          </div>

          {transactionsLoading ? (
            <div className="flex justify-center py-8">
              <Spinner />
            </div>
          ) : transactions && transactions.content.length > 0 ? (
            <div className="space-y-3">
              {transactions.content.slice(0, 5).map((transaction) => (
                <div
                  key={transaction.id}
                  className="flex items-center justify-between p-3 hover:bg-gray-50 rounded-lg transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-10 h-10 rounded-full flex items-center justify-center ${
                        transaction.type === 'DEPOSIT'
                          ? 'bg-green-100 text-green-600'
                          : transaction.type === 'WITHDRAWAL'
                          ? 'bg-red-100 text-red-600'
                          : 'bg-blue-100 text-blue-600'
                      }`}
                    >
                      {transaction.type === 'DEPOSIT' ? (
                        <ArrowDownLeft className="w-5 h-5" />
                      ) : (
                        <ArrowUpRight className="w-5 h-5" />
                      )}
                    </div>
                    <div>
                      <p className="font-medium text-gray-900">
                        {transaction.type.charAt(0) +
                          transaction.type.slice(1).toLowerCase()}
                      </p>
                      <p className="text-sm text-gray-500">
                        {formatDateShort(transaction.createdAt)}
                      </p>
                    </div>
                  </div>
                  <p
                    className={`font-semibold ${
                      transaction.type === 'DEPOSIT'
                        ? 'text-green-600'
                        : 'text-red-600'
                    }`}
                  >
                    {transaction.type === 'DEPOSIT' ? '+' : '-'}
                    {formatCurrency(transaction.amount)}
                  </p>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-8">
              <TrendingUp className="w-12 h-12 text-gray-300 mx-auto mb-3" />
              <p className="text-gray-500">No transactions yet</p>
            </div>
          )}
        </Card>
      </div>

      {/* Quick Actions */}
      <Card>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          Quick Actions
        </h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Button
            variant="outline"
            className="flex-col h-24 gap-2"
            onClick={() => navigate('/transactions')}
          >
            <ArrowUpRight className="w-6 h-6" />
            Transfer
          </Button>
          <Button
            variant="outline"
            className="flex-col h-24 gap-2"
            onClick={() => navigate('/transactions')}
          >
            <ArrowDownLeft className="w-6 h-6" />
            Deposit
          </Button>
          <Button
            variant="outline"
            className="flex-col h-24 gap-2"
            onClick={() => navigate('/accounts')}
          >
            <CreditCard className="w-6 h-6" />
            New Account
          </Button>
          <Button
            variant="outline"
            className="flex-col h-24 gap-2"
            onClick={() => navigate('/profile')}
          >
            <Wallet className="w-6 h-6" />
            Settings
          </Button>
        </div>
      </Card>
    </div>
  )
}

export default DashboardPage
