import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Wallet, Plus, ArrowRight } from 'lucide-react';
import { Card, Badge, Button, Loading } from '../../components/common';
import { accountsApi } from '../../api';
import { formatCurrency, capitalize } from '../../utils';

const AccountsPage: React.FC = () => {
  const { data: accounts, isLoading } = useQuery({ queryKey: ['accounts'], queryFn: accountsApi.getAll });

  if (isLoading) return <div className="flex items-center justify-center h-64"><Loading size="lg" text="Loading accounts..." /></div>;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-gray-900">Accounts</h1><p className="text-gray-500 mt-1">Manage your bank accounts</p></div>
        <Link to="/accounts/new"><Button leftIcon={<Plus className="w-4 h-4" />}>New Account</Button></Link>
      </div>

      {accounts && accounts.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {accounts.map((account) => (
            <Link key={account.id} to={`/accounts/${account.id}`}>
              <Card className="hover:shadow-md transition-shadow cursor-pointer group">
                <div className="flex items-start justify-between">
                  <div className="w-12 h-12 bg-primary-100 rounded-xl flex items-center justify-center"><Wallet className="w-6 h-6 text-primary-600" /></div>
                  <Badge variant={account.status === 'ACTIVE' ? 'success' : 'warning'}>{capitalize(account.status)}</Badge>
                </div>
                <div className="mt-4">
                  <p className="text-sm text-gray-500">{account.accountType}</p>
                  <p className="text-lg font-bold text-gray-900 mt-1">{formatCurrency(account.balance, account.currency)}</p>
                  <p className="text-sm text-gray-400 mt-1">{account.accountNumber}</p>
                </div>
                <div className="mt-4 pt-4 border-t border-gray-100 flex items-center justify-between">
                  <span className="text-sm text-gray-500">Available</span>
                  <span className="font-medium text-gray-900">{formatCurrency(account.availableBalance, account.currency)}</span>
                </div>
                <div className="mt-4 flex items-center text-primary-600 text-sm font-medium group-hover:text-primary-700">View Details<ArrowRight className="w-4 h-4 ml-1 group-hover:translate-x-1 transition-transform" /></div>
              </Card>
            </Link>
          ))}
        </div>
      ) : (
        <Card>
          <div className="text-center py-12">
            <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4"><Wallet className="w-8 h-8 text-gray-400" /></div>
            <h3 className="text-lg font-medium text-gray-900 mb-2">No accounts yet</h3>
            <p className="text-gray-500 mb-6">Create your first account to start managing your finances.</p>
            <Link to="/accounts/new"><Button leftIcon={<Plus className="w-4 h-4" />}>Create Account</Button></Link>
          </div>
        </Card>
      )}
    </div>
  );
};

export default AccountsPage;
