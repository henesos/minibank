import React from 'react';
import { Loader2 } from 'lucide-react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'success' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

const Button: React.FC<ButtonProps> = ({ children, variant = 'primary', size = 'md', isLoading = false, leftIcon, rightIcon, disabled, className = '', ...props }) => {
  const variants = { primary: 'bg-primary-600 text-white hover:bg-primary-700 focus:ring-primary-500', secondary: 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50 focus:ring-gray-500', danger: 'bg-red-500 text-white hover:bg-red-600 focus:ring-red-500', success: 'bg-green-500 text-white hover:bg-green-600 focus:ring-green-500', ghost: 'text-gray-700 hover:bg-gray-100 focus:ring-gray-500' };
  const sizes = { sm: 'px-3 py-1.5 text-sm', md: 'px-4 py-2 text-sm', lg: 'px-6 py-3 text-base' };
  return (
    <button className={`inline-flex items-center justify-center font-medium rounded-lg transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed ${variants[variant]} ${sizes[size]} ${className}`} disabled={disabled || isLoading} {...props}>
      {isLoading ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : leftIcon ? <span className="mr-2">{leftIcon}</span> : null}
      {children}
      {rightIcon && <span className="ml-2">{rightIcon}</span>}
    </button>
  );
};

export default Button;
