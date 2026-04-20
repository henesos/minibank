import React, { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../hooks'
import { Card, Button, Modal } from '../../components/common'
import { User, Mail, Phone, Shield, Calendar, CheckCircle, AlertTriangle } from 'lucide-react'
import { formatDateShort } from '../../utils'
import { authApi } from '../../api'
import { useNotification } from '../../hooks'
import { useNavigate } from 'react-router-dom'

const ProfilePage: React.FC = () => {
  const { user, logout } = useAuth()
  const queryClient = useQueryClient()
  const { showSuccess, showError } = useNotification()
  const navigate = useNavigate()
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false)

  const verifyEmailMutation = useMutation({
    mutationFn: () => authApi.verifyEmail(user!.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user'] })
      showSuccess('Email verified successfully!')
    },
    onError: (error: any) => {
      showError(error.message || 'Failed to verify email')
    },
  })

  const verifyPhoneMutation = useMutation({
    mutationFn: () => authApi.verifyPhone(user!.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user'] })
      showSuccess('Phone verified successfully!')
    },
    onError: (error: any) => {
      showError(error.message || 'Failed to verify phone')
    },
  })

  const deleteAccountMutation = useMutation({
    mutationFn: () => authApi.deleteAccount(user!.id),
    onSuccess: () => {
      showSuccess('Account deleted successfully')
      logout()
      navigate('/login')
    },
    onError: (error: any) => {
      showError(error.message || 'Failed to delete account')
    },
  })

  const handleDeleteAccount = () => {
    deleteAccountMutation.mutate()
    setIsDeleteModalOpen(false)
  }

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Profile</h1>
        <p className="text-gray-500 mt-1">Manage your account settings</p>
      </div>

      {/* Profile Card */}
      <Card>
        <div className="flex items-center gap-4 mb-6">
          <div className="w-20 h-20 rounded-full bg-primary-100 flex items-center justify-center">
            <span className="text-2xl font-bold text-primary-700">
              {user?.firstName?.charAt(0)}
              {user?.lastName?.charAt(0)}
            </span>
          </div>
          <div>
            <h2 className="text-xl font-semibold text-gray-900">
              {user?.firstName} {user?.lastName}
            </h2>
            <p className="text-gray-500">{user?.email}</p>
          </div>
        </div>

        <div className="space-y-4">
          <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
            <div className="flex items-center gap-3">
              <User className="w-5 h-5 text-gray-400" />
              <div>
                <p className="text-sm text-gray-500">Full Name</p>
                <p className="font-medium text-gray-900">
                  {user?.firstName} {user?.lastName}
                </p>
              </div>
            </div>
          </div>

          <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
            <div className="flex items-center gap-3">
              <Mail className="w-5 h-5 text-gray-400" />
              <div>
                <p className="text-sm text-gray-500">Email</p>
                <p className="font-medium text-gray-900">{user?.email}</p>
                {user?.emailVerified ? (
                  <span className="text-xs text-green-600 flex items-center gap-1">
                    <CheckCircle className="w-3 h-3" /> Verified
                  </span>
                ) : (
                  <span className="text-xs text-yellow-600">Not verified</span>
                )}
              </div>
            </div>
            {!user?.emailVerified && (
              <Button
                size="sm"
                variant="outline"
                onClick={() => verifyEmailMutation.mutate()}
                isLoading={verifyEmailMutation.isPending}
              >
                Verify
              </Button>
            )}
          </div>

          <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
            <div className="flex items-center gap-3">
              <Phone className="w-5 h-5 text-gray-400" />
              <div>
                <p className="text-sm text-gray-500">Phone</p>
                <p className="font-medium text-gray-900">
                  {user?.phone || 'Not set'}
                </p>
                {user?.phone && user.phoneVerified ? (
                  <span className="text-xs text-green-600 flex items-center gap-1">
                    <CheckCircle className="w-3 h-3" /> Verified
                  </span>
                ) : user?.phone ? (
                  <span className="text-xs text-yellow-600">Not verified</span>
                ) : null}
              </div>
            </div>
            {user?.phone && !user.phoneVerified && (
              <Button
                size="sm"
                variant="outline"
                onClick={() => verifyPhoneMutation.mutate()}
                isLoading={verifyPhoneMutation.isPending}
              >
                Verify
              </Button>
            )}
          </div>

          <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
            <Shield className="w-5 h-5 text-gray-400" />
            <div>
              <p className="text-sm text-gray-500">Account Status</p>
              <p className="font-medium text-gray-900">{user?.status}</p>
            </div>
          </div>

          <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
            <Calendar className="w-5 h-5 text-gray-400" />
            <div>
              <p className="text-sm text-gray-500">Member Since</p>
              <p className="font-medium text-gray-900">
                {user?.createdAt ? formatDateShort(user.createdAt) : 'N/A'}
              </p>
            </div>
          </div>
        </div>
      </Card>

      {/* Settings */}
      <Card>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Settings</h2>
        <div className="space-y-4">
          <Button variant="outline" fullWidth>
            Change Password
          </Button>
          <Button variant="outline" fullWidth>
            Notification Preferences
          </Button>
          <Button variant="outline" fullWidth>
            Security Settings
          </Button>
        </div>
      </Card>

      {/* Danger Zone */}
      <Card variant="bordered">
        <h2 className="text-lg font-semibold text-red-600 mb-4">Danger Zone</h2>
        <p className="text-sm text-gray-500 mb-4">
          Once you delete your account, there is no going back. Please be
          certain.
        </p>
        <Button variant="danger" onClick={() => setIsDeleteModalOpen(true)}>
          Delete Account
        </Button>
      </Card>

      {/* Delete Account Modal */}
      <Modal
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        title="Delete Account"
      >
        <div className="space-y-4">
          <div className="flex items-center gap-3 p-4 bg-red-50 rounded-lg">
            <AlertTriangle className="w-6 h-6 text-red-600" />
            <div>
              <p className="font-medium text-red-800">This action cannot be undone</p>
              <p className="text-sm text-red-600">
                All your data, accounts, and transactions will be permanently deleted.
              </p>
            </div>
          </div>

          <p className="text-gray-600">
            Are you sure you want to delete your account? Type your email <strong>{user?.email}</strong> to confirm.
          </p>

          <div className="flex gap-3 mt-6">
            <Button
              variant="outline"
              fullWidth
              onClick={() => setIsDeleteModalOpen(false)}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              fullWidth
              isLoading={deleteAccountMutation.isPending}
              onClick={handleDeleteAccount}
            >
              Delete My Account
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

export default ProfilePage
