import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowDownLeft, ArrowUpRight, CreditCard, ArrowLeft } from 'lucide-react';
import { Card, Button, Input } from '../../components/common';
import { transactionsApi, accountsApi } from '../../api';
import { useNotification } from '../../hooks';
import { queryClient } from '../../main';
import { formatCurrency } from '../../utils';

const transactionSchema = z.object({ accountId: z.string().min(1), type: z.enum(['DEPOSIT', 'WITHDRAWAL', 'TRANSFER']), amount: z.number().positive(), description: z.string().optional(), toAccountNumber: z.string().optional() });
type TransactionFormData = z.infer<typeof transactionSchema>;

const CreateTransactionPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { showSuccess, showError } = useNotification();
  const typeParam = searchParams.get('type') as 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER' | null;

  const { data: accounts } = useQuery({ queryKey: ['accounts'], queryFn: accountsApi.getAll });
  const { register, handleSubmit, watch, formState: { errors } } = useForm<TransactionFormData>({ resolver: zodResolver(transactionSchema), defaultValues: { type: typeParam || 'DEPOSIT', accountId: '', amount: 0 } });

  const selectedType = watch('type');
  const selectedAccountId = watch('accountId');
  const selectedAccount = accounts?.find((a) => a.id === selectedAccountId);

  const createMutation = useMutation({
    mutationFn: (data: TransactionFormData) => {
      if (data.type === 'DEPOSIT') return transactionsApi.deposit(data.accountId, data.amount, data.description);
      if (data.type === 'WITHDRAWAL') return transactionsApi.withdraw(data.accountId, data.amount, data.description);
      return transactionsApi.transfer({ fromAccountId: data.accountId, toAccountNumber: data.toAccountNumber!, amount: data.amount, description: data.description });
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['transactions'] }); queryClient.invalidateQueries({ queryKey: ['accounts'] }); showSuccess('Transaction completed!'); navigate('/transactions'); },
    onError: (error: any) => showError(error.message || 'Transaction failed'),
  });

  const onSubmit = (data: TransactionFormData) => createMutation.mutate(data);

  return (
    <div className="max-w-xl mx-auto">
      <button onClick={() => navigate('/transactions')} className="flex items-center text-gray-600 hover:text-gray-900 mb-6"><ArrowLeft className="w-4 h-4 mr-1" />Back</button>
      <Card>
        <h1 className="text-xl font-bold text-gray-900 mb-6">New Transaction</h1>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
          <div><label className="label">Transaction Type</label>
            <div className="grid grid-cols-3 gap-3">
              {['DEPOSIT', 'WITHDRAWAL', 'TRANSFER'].map((type) => (
                <label key={type} className={`flex items-center justify-center gap-2 p-3 rounded-lg border-2 cursor-pointer transition-all ${selectedType === type ? type === 'DEPOSIT' ? 'border-green-500 bg-green-50 text-green-700' : type === 'WITHDRAWAL' ? 'border-red-500 bg-red-50 text-red-700' : 'border-blue-500 bg-blue-50 text-blue-700' : 'border-gray-200 hover:border-gray-300'}`}>
                  <input type="radio" value={type} {...register('type')} className="sr-only" />
                  {type === 'DEPOSIT' ? <ArrowDownLeft className="w-5 h-5" /> : type === 'WITHDRAWAL' ? <ArrowUpRight className="w-5 h-5" /> : <CreditCard className="w-5 h-5" />}
                  <span className="font-medium">{type.charAt(0) + type.slice(1).toLowerCase()}</span>
                </label>
              ))}
            </div>
          </div>
          <div><label className="label">{selectedType === 'TRANSFER' ? 'From Account' : 'Account'}</label><select {...register('accountId')} className="input"><option value="">Select account</option>{accounts?.map((a) => (<option key={a.id} value={a.id}>{a.accountType} - {a.accountNumber} ({formatCurrency(a.balance, a.currency)})</option>))}</select></div>
          {selectedType === 'TRANSFER' && <Input label="To Account Number" placeholder="Recipient account number" {...register('toAccountNumber')} />}
          <Input label="Amount" type="number" step="0.01" min="0.01" placeholder="0.00" error={errors.amount?.message} {...register('amount', { valueAsNumber: true })} />
          {selectedAccount && (selectedType === 'WITHDRAWAL' || selectedType === 'TRANSFER') && (<div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3"><p className="text-sm text-yellow-800">Available: <span className="font-semibold">{formatCurrency(selectedAccount.availableBalance, selectedAccount.currency)}</span></p></div>)}
          <Input label="Description (Optional)" placeholder="Add a note..." {...register('description')} />
          <div className="flex gap-3 pt-4"><Button type="button" variant="secondary" onClick={() => navigate('/transactions')} className="flex-1">Cancel</Button><Button type="submit" variant={selectedType === 'DEPOSIT' ? 'success' : selectedType === 'WITHDRAWAL' ? 'danger' : 'primary'} className="flex-1" isLoading={createMutation.isPending}>{selectedType === 'DEPOSIT' ? 'Deposit' : selectedType === 'WITHDRAWAL' ? 'Withdraw' : 'Transfer'}</Button></div>
        </form>
      </Card>
    </div>
  );
};

export default CreateTransactionPage;
