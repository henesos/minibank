import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Mail, Lock, User, Phone, ArrowRight } from 'lucide-react';
import { Link, Navigate } from 'react-router-dom';
import { Button, Input, Card } from '../../components/common';
import { useAuth } from '../../hooks';

const registerSchema = z.object({
  firstName: z.string().min(2, 'First name must be at least 2 characters'),
  lastName: z.string().min(2, 'Last name must be at least 2 characters'),
  email: z.string().email('Invalid email address'),
  phone: z.string().optional(),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  confirmPassword: z.string(),
}).refine((data) => data.password === data.confirmPassword, { message: "Passwords don't match", path: ['confirmPassword'] });

type RegisterFormData = z.infer<typeof registerSchema>;

const RegisterPage: React.FC = () => {
  const { register: registerUser, isAuthenticated, isLoading } = useAuth();
  const { register, handleSubmit, formState: { errors } } = useForm<RegisterFormData>({ resolver: zodResolver(registerSchema) });

  const onSubmit = (data: RegisterFormData) => registerUser({ firstName: data.firstName, lastName: data.lastName, email: data.email, phoneNumber: data.phone, password: data.password });

  if (isAuthenticated) return <Navigate to="/dashboard" replace />;

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-600 to-primary-800 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-white rounded-2xl shadow-lg mb-4"><span className="text-2xl font-bold text-primary-600">MB</span></div>
          <h1 className="text-3xl font-bold text-white">Create Account</h1>
          <p className="text-primary-100 mt-2">Join MiniBank today</p>
        </div>
        <Card>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <Input label="First Name" placeholder="John" leftIcon={<User className="w-5 h-5" />} error={errors.firstName?.message} {...register('firstName')} />
              <Input label="Last Name" placeholder="Doe" error={errors.lastName?.message} {...register('lastName')} />
            </div>
            <Input label="Email" type="email" placeholder="john@example.com" leftIcon={<Mail className="w-5 h-5" />} error={errors.email?.message} {...register('email')} />
            <Input label="Phone (optional)" type="tel" placeholder="+1 234 567 8900" leftIcon={<Phone className="w-5 h-5" />} error={errors.phone?.message} {...register('phone')} />
            <Input label="Password" type="password" placeholder="Create a password" leftIcon={<Lock className="w-5 h-5" />} error={errors.password?.message} {...register('password')} />
            <Input label="Confirm Password" type="password" placeholder="Confirm your password" leftIcon={<Lock className="w-5 h-5" />} error={errors.confirmPassword?.message} {...register('confirmPassword')} />
            <Button type="submit" className="w-full" isLoading={isLoading} rightIcon={<ArrowRight className="w-4 h-4" />}>Create Account</Button>
          </form>
          <div className="mt-6 text-center">
            <p className="text-gray-600">Already have an account? <Link to="/login" className="text-primary-600 hover:text-primary-700 font-medium">Sign in</Link></p>
          </div>
        </Card>
      </div>
    </div>
  );
};

export default RegisterPage;
