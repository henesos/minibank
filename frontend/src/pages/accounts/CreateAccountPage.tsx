import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Wallet } from 'lucide-react';
import { Card, Button, Input } from '../../components/common';
import { accountsApi } from '../../api';
import { useNotification } from '../../hooks';
import { queryClient } from '../../main';

const createAccountSchema = z.object({ accountType: z.enum(['CHECKING', 'SAVINGS', 'BUSINESS']), currency: z.string(), initialDeposit: z.number().min(0).optional() });
type CreateAccountFormData = z.input<typeof createAccountSchema>;

const CreateAccountPage: React.FC = () => {
  const navigate = useNavigate();
  const { showSuccess, showError } = useNotification();
  const { register, handleSubmit } = useForm<CreateAccountFormData>({ resolver: zodResolver(createAccountSchema), defaultValues: { accountType: 'CHECKING', currency: 'USD' } });

  const createMutation = useMutation({
    mutationFn: (data: CreateAccountFormData) => accountsApi.create({ accountType: data.accountType, currency: data.currency, initialDeposit: data.initialDeposit }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['accounts'] }); showSuccess('Account created successfully!'); navigate('/accounts'); },
    onError: (error: any) => showError(error.message || 'Failed to create account'),
  });

  const onSubmit = (data: CreateAccountFormData) => createMutation.mutate(data);

  return (
    <div className="max-w-xl mx-auto">
      <button onClick={() => navigate('/accounts')} className="flex items-center text-gray-600 hover:text-gray-900 mb-6"><ArrowLeft className="w-4 h-4 mr-1" />Back to Accounts</button>
      <Card>
        <div className="flex items-center gap-3 mb-6">
          <div className="w-12 h-12 bg-primary-100 rounded-xl flex items-center justify-center"><Wallet className="w-6 h-6 text-primary-600" /></div>
          <div><h1 className="text-xl font-bold text-gray-900">Create New Account</h1><p className="text-gray-500 text-sm">Open a new bank account</p></div>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
          <div><label className="label">Account Type</label><select {...register('accountType')} className="input"><option value="CHECKING">Checking Account</option><option value="SAVINGS">Savings Account</option><option value="BUSINESS">Business Account</option></select></div>
          <div><label className="label">Currency</label><select {...register('currency')} className="input"><option value="USD">USD - US Dollar</option><option value="EUR">EUR - Euro</option><option value="GBP">GBP - British Pound</option><option value="TRY">TRY - Turkish Lira</option></select></div>
          <Input label="Initial Deposit (Optional)" type="number" step="0.01" min="0" placeholder="0.00" {...register('initialDeposit', { valueAsNumber: true })} />
          <div className="flex gap-3 pt-4"><Button type="button" variant="secondary" onClick={() => navigate('/accounts')} className="flex-1">Cancel</Button><Button type="submit" className="flex-1" isLoading={createMutation.isPending}>Create Account</Button></div>
        </form>
      </Card>
    </div>
  );
};

export default CreateAccountPage;
