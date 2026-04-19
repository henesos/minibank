import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Mail, Lock, ArrowRight } from 'lucide-react';
import { Link, Navigate } from 'react-router-dom';
import { Button, Input, Card } from '../../components/common';
import { useAuth } from '../../hooks';

const loginSchema = z.object({ email: z.string().email('Invalid email address'), password: z.string().min(6, 'Password must be at least 6 characters') });
type LoginFormData = z.infer<typeof loginSchema>;

const LoginPage: React.FC = () => {
  const { login, isAuthenticated, isLoading } = useAuth();
  const { register, handleSubmit, formState: { errors } } = useForm<LoginFormData>({ resolver: zodResolver(loginSchema) });

  const onSubmit = (data: LoginFormData) => {
    console.log('Login attempt:', data);
    login(data);
  };

  // Prevent form from refreshing page
  const onFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    handleSubmit(onSubmit)();
  };

  if (isAuthenticated) return <Navigate to="/dashboard" replace />;

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-600 to-primary-800 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-white rounded-2xl shadow-lg mb-4"><span className="text-2xl font-bold text-primary-600">MB</span></div>
          <h1 className="text-3xl font-bold text-white">Welcome Back</h1>
          <p className="text-primary-100 mt-2">Sign in to your MiniBank account</p>
        </div>
        <Card>
          <form onSubmit={onFormSubmit} className="space-y-5">
            <Input label="Email" type="email" placeholder="Enter your email" leftIcon={<Mail className="w-5 h-5" />} error={errors.email?.message} {...register('email')} />
            <Input label="Password" type="password" placeholder="Enter your password" leftIcon={<Lock className="w-5 h-5" />} error={errors.password?.message} {...register('password')} />
            <Button type="submit" className="w-full" isLoading={isLoading} rightIcon={<ArrowRight className="w-4 h-4" />}>Sign In</Button>
          </form>
          <div className="mt-6 text-center">
            <p className="text-gray-600">Don't have an account? <Link to="/register" className="text-primary-600 hover:text-primary-700 font-medium">Sign up</Link></p>
          </div>
        </Card>
      </div>
    </div>
  );
};

export default LoginPage;
