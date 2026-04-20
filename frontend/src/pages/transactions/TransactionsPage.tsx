import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ArrowUpRight,
  ArrowDownLeft,
  ArrowLeftRight,
  Clock,
  CheckCircle,
  XCircle,
  Loader2,
  AlertTriangle,
} from 'lucide-react'
import { Card, Button, Input, Select, Spinner } from '../../components/common'
import { accountsApi, transactionsApi } from '../../api'
import { useNotification } from '../../hooks'
import { formatCurrency, formatDate } from '../../utils'
import type { TransferRequest, DepositRequest, WithdrawRequest, Transaction } from '../../types'

type TransactionTab = 'history' | 'transfer' | 'deposit' | 'withdraw'

const TransactionsPage: React.FC = () => {
  const queryClient = useQueryClient()
  const { showSuccess, showError } = useNotification()
  const [activeTab, setActiveTab] = useState<TransactionTab>('history')

  // Transfer form state
  const [transferFrom, setTransferFrom] = useState('')
  const [transferTo, setTransferTo] = useState('')
  const [transferAmount, setTransferAmount] = useState('')
  const [transferDesc, setTransferDesc] = useState('')

  // Deposit/Withdraw form state
  const [selectedAccount, setSelectedAccount] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')

  const { data: accounts } = useQuery({
    queryKey: ['accounts'],
    queryFn: accountsApi.getAll,
  })

  const { data: transactions, isLoading: transactionsLoading } = useQuery({
    queryKey: ['transactions'],
    queryFn: () => transactionsApi.getAll({ size: 20 }),
  })

  const transferMutation = useMutation({
    mutationFn: (data: TransferRequest) => transactionsApi.transfer(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      showSuccess('Transfer completed successfully!')
      resetForms()
    },
    onError: (error: any) => {
      showError(error.message || 'Transfer failed')
    },
  })

  const depositMutation = useMutation({
    mutationFn: (data: DepositRequest) => transactionsApi.deposit(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      showSuccess('Deposit completed successfully!')
      resetForms()
    },
    onError: (error: any) => {
      showError(error.message || 'Deposit failed')
    },
  })

  const withdrawMutation = useMutation({
    mutationFn: (data: WithdrawRequest) => transactionsApi.withdraw(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      showSuccess('Withdrawal completed successfully!')
      resetForms()
    },
    onError: (error: any) => {
      showError(error.message || 'Withdrawal failed')
    },
  })

  const resetForms = () => {
    setTransferFrom('')
    setTransferTo('')
    setTransferAmount('')
    setTransferDesc('')
    setSelectedAccount('')
    setAmount('')
    setDescription('')
  }

  const handleTransfer = () => {
    if (!transferFrom || !transferTo || !transferAmount) {
      showError('Please fill in all required fields')
      return
    }
    transferMutation.mutate({
      fromAccountId: transferFrom,
      toAccountNumber: transferTo,
      amount: parseFloat(transferAmount),
      description: transferDesc,
    })
  }

  const handleDeposit = () => {
    if (!selectedAccount || !amount) {
      showError('Please fill in all required fields')
      return
    }
    depositMutation.mutate({
      accountId: selectedAccount,
      amount: parseFloat(amount),
      description,
    })
  }

  const handleWithdraw = () => {
    if (!selectedAccount || !amount) {
      showError('Please fill in all required fields')
      return
    }
    withdrawMutation.mutate({
      accountId: selectedAccount,
      amount: parseFloat(amount),
      description,
    })
  }

  const getTransactionIcon = (type: string) => {
    switch (type) {
      case 'DEPOSIT':
        return <ArrowDownLeft className="w-5 h-5 text-green-600" />
      case 'WITHDRAWAL':
        return <ArrowUpRight className="w-5 h-5 text-red-600" />
      case 'TRANSFER':
        return <ArrowLeftRight className="w-5 h-5 text-blue-600" />
      default:
        return <ArrowLeftRight className="w-5 h-5 text-gray-600" />
    }
  }

  const getStatusBadge = (status: Transaction['status']) => {
    const statusConfig: Record<Transaction['status'], { color: string; icon: React.ReactNode; label: string }> = {
      PENDING: { color: 'bg-yellow-100 text-yellow-700', icon: <Clock className="w-3 h-3" />, label: 'Pending' },
      PROCESSING: { color: 'bg-blue-100 text-blue-700', icon: <Loader2 className="w-3 h-3 animate-spin" />, label: 'Processing' },
      DEBITED: { color: 'bg-purple-100 text-purple-700', icon: <ArrowUpRight className="w-3 h-3" />, label: 'Debited' },
      COMPLETED: { color: 'bg-green-100 text-green-700', icon: <CheckCircle className="w-3 h-3" />, label: 'Completed' },
      FAILED: { color: 'bg-red-100 text-red-700', icon: <XCircle className="w-3 h-3" />, label: 'Failed' },
      COMPENSATING: { color: 'bg-orange-100 text-orange-700', icon: <Loader2 className="w-3 h-3 animate-spin" />, label: 'Reverting' },
      COMPENSATED: { color: 'bg-gray-100 text-gray-700', icon: <AlertTriangle className="w-3 h-3" />, label: 'Reverted' },
      CANCELLED: { color: 'bg-gray-100 text-gray-500', icon: <XCircle className="w-3 h-3" />, label: 'Cancelled' },
    }

    const config = statusConfig[status] || statusConfig.PENDING

    return (
      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${config.color}`}>
        {config.icon}
        {config.label}
      </span>
    )
  }

  const tabs: { id: TransactionTab; label: string }[] = [
    { id: 'history', label: 'History' },
    { id: 'transfer', label: 'Transfer' },
    { id: 'deposit', label: 'Deposit' },
    { id: 'withdraw', label: 'Withdraw' },
  ]

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Transactions</h1>
        <p className="text-gray-500 mt-1">Manage your transactions</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-gray-200">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
              activeTab === tab.id
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab Content */}
      {activeTab === 'history' && (
        <Card>
          {transactionsLoading ? (
            <div className="flex justify-center py-12">
              <Spinner size="lg" />
            </div>
          ) : transactions && transactions.content.length > 0 ? (
            <div className="divide-y divide-gray-100">
              {transactions.content.map((transaction) => (
                <div
                  key={transaction.id}
                  className="flex items-center justify-between py-4 hover:bg-gray-50 px-2 -mx-2 rounded-lg transition-colors"
                >
                  <div className="flex items-center gap-4">
                    <div className="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center">
                      {getTransactionIcon(transaction.type)}
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="font-medium text-gray-900">
                          {transaction.type.charAt(0) +
                            transaction.type.slice(1).toLowerCase()}
                        </p>
                        {getStatusBadge(transaction.status)}
                      </div>
                      <p className="text-sm text-gray-500">
                        {transaction.description || 'No description'}
                      </p>
                      {transaction.failureReason && (
                        <p className="text-xs text-red-500 mt-1">
                          {transaction.failureReason}
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="text-right">
                    <p
                      className={`font-semibold ${
                        transaction.type === 'DEPOSIT'
                          ? 'text-green-600'
                          : transaction.type === 'WITHDRAWAL'
                          ? 'text-red-600'
                          : 'text-blue-600'
                      }`}
                    >
                      {transaction.type === 'DEPOSIT' ? '+' : transaction.type === 'WITHDRAWAL' ? '-' : ''}
                      {formatCurrency(transaction.amount)}
                    </p>
                    <p className="text-sm text-gray-500">
                      {formatDate(transaction.createdAt)}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-12">
              <ArrowLeftRight className="w-16 h-16 text-gray-300 mx-auto mb-4" />
              <h3 className="text-lg font-medium text-gray-900">
                No transactions yet
              </h3>
              <p className="text-gray-500 mt-1">
                Make your first transaction to get started
              </p>
            </div>
          )}
        </Card>
      )}

      {activeTab === 'transfer' && (
        <Card>
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            Transfer Money
          </h2>
          <div className="space-y-4 max-w-md">
            <Select
              label="From Account"
              value={transferFrom}
              onChange={(e) => setTransferFrom(e.target.value)}
              options={
                accounts?.map((a) => ({
                  value: a.id,
                  label: `${a.accountType} - ****${a.accountNumber.slice(-4)} (${formatCurrency(a.balance)})`,
                })) || []
              }
              placeholder="Select account"
            />
            <Input
              label="To Account Number"
              value={transferTo}
              onChange={(e) => setTransferTo(e.target.value)}
              placeholder="Enter destination account number"
            />
            <Input
              label="Amount"
              type="number"
              value={transferAmount}
              onChange={(e) => setTransferAmount(e.target.value)}
              placeholder="0.00"
              leftIcon={<span className="text-gray-400">$</span>}
            />
            <Input
              label="Description (optional)"
              value={transferDesc}
              onChange={(e) => setTransferDesc(e.target.value)}
              placeholder="What's this for?"
            />
            <Button
              fullWidth
              isLoading={transferMutation.isPending}
              onClick={handleTransfer}
            >
              Transfer
            </Button>
          </div>
        </Card>
      )}

      {activeTab === 'deposit' && (
        <Card>
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            Deposit Money
          </h2>
          <div className="space-y-4 max-w-md">
            <Select
              label="To Account"
              value={selectedAccount}
              onChange={(e) => setSelectedAccount(e.target.value)}
              options={
                accounts?.map((a) => ({
                  value: a.id,
                  label: `${a.accountType} - ****${a.accountNumber.slice(-4)} (${formatCurrency(a.balance)})`,
                })) || []
              }
              placeholder="Select account"
            />
            <Input
              label="Amount"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              leftIcon={<span className="text-gray-400">$</span>}
            />
            <Input
              label="Description (optional)"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What's this for?"
            />
            <Button
              fullWidth
              isLoading={depositMutation.isPending}
              onClick={handleDeposit}
            >
              Deposit
            </Button>
          </div>
        </Card>
      )}

      {activeTab === 'withdraw' && (
        <Card>
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            Withdraw Money
          </h2>
          <div className="space-y-4 max-w-md">
            <Select
              label="From Account"
              value={selectedAccount}
              onChange={(e) => setSelectedAccount(e.target.value)}
              options={
                accounts?.map((a) => ({
                  value: a.id,
                  label: `${a.accountType} - ****${a.accountNumber.slice(-4)} (${formatCurrency(a.balance)})`,
                })) || []
              }
              placeholder="Select account"
            />
            <Input
              label="Amount"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              leftIcon={<span className="text-gray-400">$</span>}
            />
            <Input
              label="Description (optional)"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What's this for?"
            />
            <Button
              fullWidth
              variant="danger"
              isLoading={withdrawMutation.isPending}
              onClick={handleWithdraw}
            >
              Withdraw
            </Button>
          </div>
        </Card>
      )}
    </div>
  )
}

export default TransactionsPage
