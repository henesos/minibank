import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus,
  Wallet,
  MoreVertical,
  Eye,
  EyeOff,
  CheckCircle,
  Trash2,
  Ban,
} from 'lucide-react'
import { Card, Button, Modal, Select, Spinner } from '../../components/common'
import { accountsApi } from '../../api'
import { useNotification } from '../../hooks'
import { formatCurrency, formatDateShort } from '../../utils'
import type { CreateAccountRequest, Account } from '../../types'

const AccountsPage: React.FC = () => {
  const queryClient = useQueryClient()
  const { showSuccess, showError } = useNotification()
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [isCloseModalOpen, setIsCloseModalOpen] = useState(false)
  const [selectedAccount, setSelectedAccount] = useState<Account | null>(null)
  const [showBalances, setShowBalances] = useState(true)
  const [newAccountType, setNewAccountType] = useState<'CHECKING' | 'SAVINGS' | 'BUSINESS'>('CHECKING')

  const { data: accounts, isLoading } = useQuery({
    queryKey: ['accounts'],
    queryFn: accountsApi.getAll,
  })

  const createMutation = useMutation({
    mutationFn: (data: CreateAccountRequest) => accountsApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      showSuccess('Account created successfully!')
      setIsCreateModalOpen(false)
      setNewAccountType('CHECKING')
    },
    onError: (error: any) => {
      showError(error.message || 'Failed to create account')
    },
  })

  const activateMutation = useMutation({
    mutationFn: (id: string) => accountsApi.activate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      showSuccess('Account activated successfully!')
    },
    onError: (error: any) => {
      showError(error.message || 'Failed to activate account')
    },
  })

  const suspendMutation = useMutation({
    mutationFn: (id: string) => accountsApi.suspend(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      showSuccess('Account suspended successfully!')
    },
    onError: (error: any) => {
      showError(error.message || 'Failed to suspend account')
    },
  })

  const closeMutation = useMutation({
    mutationFn: (id: string) => accountsApi.close(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      showSuccess('Account closed successfully!')
      setIsCloseModalOpen(false)
      setSelectedAccount(null)
    },
    onError: (error: any) => {
      showError(error.message || 'Failed to close account')
    },
  })

  const handleCreateAccount = () => {
    createMutation.mutate({ accountType: newAccountType, currency: 'USD' })
  }

  const handleActivate = (accountId: string) => {
    activateMutation.mutate(accountId)
  }

  const handleSuspend = (accountId: string) => {
    suspendMutation.mutate(accountId)
  }

  const handleCloseAccount = () => {
    if (selectedAccount) {
      closeMutation.mutate(selectedAccount.id)
    }
  }

  const openCloseModal = (account: Account) => {
    setSelectedAccount(account)
    setIsCloseModalOpen(true)
  }

  const getAccountColor = (type: string, status: string) => {
    if (status === 'PENDING' || status === 'DORMANT' || status === 'SUSPENDED') {
      return 'from-gray-400 to-gray-600'
    }
    switch (type) {
      case 'CHECKING':
        return 'from-blue-500 to-blue-700'
      case 'SAVINGS':
        return 'from-green-500 to-green-700'
      case 'BUSINESS':
        return 'from-purple-500 to-purple-700'
      default:
        return 'from-gray-500 to-gray-700'
    }
  }

  const renderAccountCard = (account: Account) => (
    <Card
      key={account.id}
      className={`bg-gradient-to-br ${getAccountColor(account.accountType, account.status)} text-white overflow-hidden relative`}
      padding="none"
    >
      {/* Pending overlay */}
      {account.status === 'PENDING' && (
        <div className="absolute inset-0 bg-black/30 flex items-center justify-center z-10">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => handleActivate(account.id)}
            isLoading={activateMutation.isPending}
            className="bg-white text-gray-900 hover:bg-gray-100"
          >
            <CheckCircle className="w-4 h-4 mr-2" />
            Activate Account
          </Button>
        </div>
      )}
      <div className="p-6">
        <div className="flex items-start justify-between mb-8">
          <div>
            <p className="text-sm opacity-80">
              {account.accountType.charAt(0) +
                account.accountType.slice(1).toLowerCase()}{' '}
              Account
            </p>
            <p className="text-lg font-medium mt-1">
              ****{account.accountNumber.slice(-4)}
            </p>
          </div>
          {/* Account Actions Dropdown */}
          <div className="relative group">
            <button className="p-1 rounded hover:bg-white/10">
              <MoreVertical className="w-5 h-5" />
            </button>
            {/* Dropdown Menu */}
            <div className="absolute right-0 top-8 w-48 bg-white rounded-lg shadow-lg py-1 hidden group-hover:block z-20">
              {account.status === 'ACTIVE' && (
                <button
                  onClick={() => handleSuspend(account.id)}
                  className="w-full px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-100 flex items-center gap-2"
                >
                  <Ban className="w-4 h-4" />
                  Suspend Account
                </button>
              )}
              {account.status === 'SUSPENDED' && (
                <button
                  onClick={() => handleActivate(account.id)}
                  className="w-full px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-100 flex items-center gap-2"
                >
                  <CheckCircle className="w-4 h-4" />
                  Activate Account
                </button>
              )}
              <button
                onClick={() => openCloseModal(account)}
                className="w-full px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50 flex items-center gap-2"
              >
                <Trash2 className="w-4 h-4" />
                Close Account
              </button>
            </div>
          </div>
        </div>

        <div>
          <p className="text-sm opacity-80">Balance</p>
          <p className="text-3xl font-bold mt-1">
            {showBalances ? formatCurrency(account.balance) : '••••••'}
          </p>
        </div>

        <div className="flex items-center justify-between mt-6 pt-4 border-t border-white/20">
          <div>
            <p className="text-xs opacity-70">Status</p>
            <p className={`text-sm font-medium ${account.status === 'ACTIVE' ? 'text-green-300' : account.status === 'PENDING' ? 'text-yellow-300' : account.status === 'SUSPENDED' ? 'text-red-300' : ''}`}>
              {account.status.charAt(0) +
                account.status.slice(1).toLowerCase()}
            </p>
          </div>
          <div className="text-right">
            <p className="text-xs opacity-70">Created</p>
            <p className="text-sm font-medium">
              {formatDateShort(account.createdAt)}
            </p>
          </div>
        </div>
      </div>
    </Card>
  )

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Accounts</h1>
          <p className="text-gray-500 mt-1">Manage your bank accounts</p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            onClick={() => setShowBalances(!showBalances)}
          >
            {showBalances ? (
              <EyeOff className="w-5 h-5" />
            ) : (
              <Eye className="w-5 h-5" />
            )}
          </Button>
          <Button onClick={() => setIsCreateModalOpen(true)}>
            <Plus className="w-5 h-5 mr-2" />
            New Account
          </Button>
        </div>
      </div>

      {/* Accounts grid */}
      {isLoading ? (
        <div className="flex justify-center py-12">
          <Spinner size="lg" />
        </div>
      ) : accounts && accounts.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {accounts.map((account) => renderAccountCard(account))}
        </div>
      ) : (
        <Card>
          <div className="text-center py-12">
            <Wallet className="w-16 h-16 text-gray-300 mx-auto mb-4" />
            <h3 className="text-lg font-medium text-gray-900">
              No accounts yet
            </h3>
            <p className="text-gray-500 mt-1">
              Create your first account to get started
            </p>
            <Button
              className="mt-4"
              onClick={() => setIsCreateModalOpen(true)}
            >
              <Plus className="w-5 h-5 mr-2" />
              Create Account
            </Button>
          </div>
        </Card>
      )}

      {/* Create Account Modal */}
      <Modal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        title="Create New Account"
      >
        <div className="space-y-4">
          <Select
            label="Account Type"
            value={newAccountType}
            onChange={(e) =>
              setNewAccountType(e.target.value as 'CHECKING' | 'SAVINGS' | 'BUSINESS')
            }
            options={[
              { value: 'CHECKING', label: 'Checking Account' },
              { value: 'SAVINGS', label: 'Savings Account' },
              { value: 'BUSINESS', label: 'Business Account' },
            ]}
          />

          <div className="flex gap-3 mt-6">
            <Button
              variant="outline"
              fullWidth
              onClick={() => setIsCreateModalOpen(false)}
            >
              Cancel
            </Button>
            <Button
              fullWidth
              isLoading={createMutation.isPending}
              onClick={handleCreateAccount}
            >
              Create Account
            </Button>
          </div>
        </div>
      </Modal>

      {/* Close Account Modal */}
      <Modal
        isOpen={isCloseModalOpen}
        onClose={() => {
          setIsCloseModalOpen(false)
          setSelectedAccount(null)
        }}
        title="Close Account"
      >
        <div className="space-y-4">
          <div className="flex items-center gap-3 p-4 bg-red-50 rounded-lg">
            <Trash2 className="w-6 h-6 text-red-600" />
            <div>
              <p className="font-medium text-red-800">This action cannot be undone</p>
              <p className="text-sm text-red-600">
                Account will be permanently closed.
              </p>
            </div>
          </div>

          {selectedAccount && (
            <div className="p-4 bg-gray-50 rounded-lg">
              <p className="text-sm text-gray-600">Account to close:</p>
              <p className="font-medium text-gray-900">
                {selectedAccount.accountType} - ****{selectedAccount.accountNumber.slice(-4)}
              </p>
              <p className="text-sm text-gray-600 mt-1">
                Balance: {formatCurrency(selectedAccount.balance)}
              </p>
            </div>
          )}

          {selectedAccount && selectedAccount.balance !== 0 && (
            <div className="p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
              <p className="text-sm text-yellow-800">
                This account has a non-zero balance. Please withdraw or transfer all funds before closing.
              </p>
            </div>
          )}

          <div className="flex gap-3 mt-6">
            <Button
              variant="outline"
              fullWidth
              onClick={() => {
                setIsCloseModalOpen(false)
                setSelectedAccount(null)
              }}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              fullWidth
              isLoading={closeMutation.isPending}
              onClick={handleCloseAccount}
              disabled={selectedAccount?.balance !== 0}
            >
              Close Account
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

export default AccountsPage
