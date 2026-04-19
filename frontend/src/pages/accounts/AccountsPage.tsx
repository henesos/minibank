import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus,
  Wallet,
  MoreVertical,
  Eye,
  EyeOff,
} from 'lucide-react'
import { Card, Button, Modal, Select, Spinner } from '../../components/common'
import { accountsApi } from '../../api'
import { useNotification } from '../../hooks'
import { formatCurrency, formatDateShort } from '../../utils'
import type { CreateAccountRequest } from '../../types'

const AccountsPage: React.FC = () => {
  const queryClient = useQueryClient()
  const { showSuccess, showError } = useNotification()
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
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

  const handleCreateAccount = () => {
    createMutation.mutate({ accountType: newAccountType, currency: 'USD' })
  }

  const getAccountColor = (type: string) => {
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
          {accounts.map((account) => (
            <Card
              key={account.id}
              className={`bg-gradient-to-br ${getAccountColor(account.accountType)} text-white overflow-hidden`}
              padding="none"
            >
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
                  <button className="p-1 rounded hover:bg-white/10">
                    <MoreVertical className="w-5 h-5" />
                  </button>
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
                    <p className="text-sm font-medium">
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
          ))}
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
    </div>
  )
}

export default AccountsPage
