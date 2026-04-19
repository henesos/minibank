import React from 'react'
import { cn } from '../../utils/cn'

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  helperText?: string
  leftIcon?: React.ReactNode
  rightIcon?: React.ReactNode
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  (
    { className, label, error, helperText, leftIcon, rightIcon, id, ...props },
    ref
  ) => {
    const inputId = id || label?.toLowerCase().replace(/\s+/g, '-')

    return (
      <div className="w-full">
        {label && (
          <label
            htmlFor={inputId}
            className="block text-sm font-medium text-gray-700 mb-1.5"
          >
            {label}
          </label>
        )}
        <div className="relative">
          {leftIcon && (
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
              {leftIcon}
            </div>
          )}
          <input
            ref={ref}
            id={inputId}
            className={cn(
              'w-full px-4 py-2.5 text-sm border rounded-lg transition-colors duration-200',
              'bg-white text-gray-900 placeholder-gray-400',
              'focus:ring-2 focus:ring-primary-500 focus:border-primary-500',
              'disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed',
              leftIcon && 'pl-10',
              rightIcon && 'pr-10',
              error
                ? 'border-red-500 focus:ring-red-500 focus:border-red-500'
                : 'border-gray-300',
              className
            )}
            {...props}
          />
          {rightIcon && (
            <div className="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-gray-400">
              {rightIcon}
            </div>
          )}
        </div>
        {error && <p className="mt-1.5 text-sm text-red-600">{error}</p>}
        {helperText && !error && (
          <p className="mt-1.5 text-sm text-gray-500">{helperText}</p>
        )}
      </div>
    )
  }
)

Input.displayName = 'Input'

export default Input
