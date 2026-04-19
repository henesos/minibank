import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { User, Mail, Phone, Lock } from 'lucide-react';
import { Card, Button, Input } from '../../components/common';
import { authApi } from '../../api';
import { useAuth, useNotification } from '../../hooks';
import { queryClient } from '../../main';

const profileSchema = z.object({ firstName: z.string().min(2), lastName: z.string().min(2), phoneNumber: z.string().optional() });
const passwordSchema = z.object({ currentPassword: z.string().min(6), newPassword: z.string().min(6), confirmPassword: z.string() }).refine((d) => d.newPassword === d.confirmPassword, { message: "Passwords don't match", path: ['confirmPassword'] });

type ProfileFormData = z.infer<typeof profileSchema>;
type PasswordFormData = z.infer<typeof passwordSchema>;

const ProfilePage: React.FC = () => {
  const { user, setUser } = useAuth();
  const { showSuccess, showError } = useNotification();

  const { register: regProfile, handleSubmit: handleProfileSubmit, formState: { errors: profileErrors } } = useForm<ProfileFormData>({ resolver: zodResolver(profileSchema), defaultValues: { firstName: user?.firstName || '', lastName: user?.lastName || '', phoneNumber: user?.phoneNumber || '' } });
  const { register: regPassword, handleSubmit: handlePasswordSubmit, reset: resetPassword, formState: { errors: passwordErrors } } = useForm<PasswordFormData>({ resolver: zodResolver(passwordSchema) });

  const updateProfileMutation = useMutation({
    mutationFn: (data: ProfileFormData) => authApi.updateProfile(data),
    onSuccess: (updatedUser) => { setUser(updatedUser); queryClient.invalidateQueries({ queryKey: ['currentUser'] }); showSuccess('Profile updated!'); },
    onError: (error: any) => showError(error.message || 'Failed to update'),
  });

  const changePasswordMutation = useMutation({
    mutationFn: (data: PasswordFormData) => authApi.changePassword(data.currentPassword, data.newPassword),
    onSuccess: () => { resetPassword(); showSuccess('Password changed!'); },
    onError: (error: any) => showError(error.message || 'Failed to change password'),
  });

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div><h1 className="text-2xl font-bold text-gray-900">Profile Settings</h1><p className="text-gray-500 mt-1">Manage your account settings</p></div>

      <Card title="Personal Information">
        <form onSubmit={handleProfileSubmit((d) => updateProfileMutation.mutate(d))} className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Input label="First Name" leftIcon={<User className="w-5 h-5" />} error={profileErrors.firstName?.message} {...regProfile('firstName')} />
            <Input label="Last Name" error={profileErrors.lastName?.message} {...regProfile('lastName')} />
          </div>
          <Input label="Email" type="email" leftIcon={<Mail className="w-5 h-5" />} disabled {...regProfile('email' as any)} />
          <Input label="Phone" type="tel" leftIcon={<Phone className="w-5 h-5" />} {...regProfile('phoneNumber')} />
          <Button type="submit" isLoading={updateProfileMutation.isPending}>Save Changes</Button>
        </form>
      </Card>

      <Card title="Change Password">
        <form onSubmit={handlePasswordSubmit((d) => changePasswordMutation.mutate(d))} className="space-y-4">
          <Input label="Current Password" type="password" leftIcon={<Lock className="w-5 h-5" />} error={passwordErrors.currentPassword?.message} {...regPassword('currentPassword')} />
          <Input label="New Password" type="password" leftIcon={<Lock className="w-5 h-5" />} error={passwordErrors.newPassword?.message} {...regPassword('newPassword')} />
          <Input label="Confirm Password" type="password" leftIcon={<Lock className="w-5 h-5" />} error={passwordErrors.confirmPassword?.message} {...regPassword('confirmPassword')} />
          <Button type="submit" isLoading={changePasswordMutation.isPending}>Change Password</Button>
        </form>
      </Card>

      <Card title="Account Information">
        <div className="space-y-3">
          <div className="flex justify-between py-2 border-b border-gray-100"><span className="text-gray-500">Status</span><span className={`px-2 py-1 rounded-full text-xs font-medium ${user?.status === 'ACTIVE' ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'}`}>{user?.status}</span></div>
          <div className="flex justify-between py-2 border-b border-gray-100"><span className="text-gray-500">Member Since</span><span className="text-gray-900">{user?.createdAt ? new Date(user.createdAt).toLocaleDateString() : '-'}</span></div>
          <div className="flex justify-between py-2"><span className="text-gray-500">User ID</span><span className="text-gray-900 font-mono text-sm">{user?.id}</span></div>
        </div>
      </Card>
    </div>
  );
};

export default ProfilePage;
