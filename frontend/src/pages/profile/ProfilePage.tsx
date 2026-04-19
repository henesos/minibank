import React from 'react'
import { useAuth } from '../../hooks'
import { Card, Button } from '../../components/common'
import { User, Mail, Phone, Shield, Calendar } from 'lucide-react'
import { formatDateShort } from '../../utils'

const ProfilePage: React.FC = () => {
  const { user, logout } = useAuth()

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
          <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
            <User className="w-5 h-5 text-gray-400" />
            <div>
              <p className="text-sm text-gray-500">Full Name</p>
              <p className="font-medium text-gray-900">
                {user?.firstName} {user?.lastName}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
            <Mail className="w-5 h-5 text-gray-400" />
            <div>
              <p className="text-sm text-gray-500">Email</p>
              <p className="font-medium text-gray-900">{user?.email}</p>
              {user?.emailVerified ? (
                <span className="text-xs text-green-600">Verified</span>
              ) : (
                <span className="text-xs text-yellow-600">Not verified</span>
              )}
            </div>
          </div>

          <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
            <Phone className="w-5 h-5 text-gray-400" />
            <div>
              <p className="text-sm text-gray-500">Phone</p>
              <p className="font-medium text-gray-900">
                {user?.phone || 'Not set'}
              </p>
              {user?.phone && user.phoneVerified ? (
                <span className="text-xs text-green-600">Verified</span>
              ) : user?.phone ? (
                <span className="text-xs text-yellow-600">Not verified</span>
              ) : null}
            </div>
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
        <Button variant="danger" onClick={() => logout()}>
          Delete Account
        </Button>
      </Card>
    </div>
  )
}

export default ProfilePage
