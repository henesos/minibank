import React from 'react';

interface CardProps {
  children: React.ReactNode;
  title?: string;
  subtitle?: string;
  headerAction?: React.ReactNode;
  footer?: React.ReactNode;
  className?: string;
  noPadding?: boolean;
}

const Card: React.FC<CardProps> = ({ children, title, subtitle, headerAction, footer, className = '', noPadding = false }) => (
  <div className={`bg-white rounded-xl shadow-sm border border-gray-200 ${className}`}>
    {(title || headerAction) && (
      <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
        <div>{title && <h3 className="text-lg font-semibold text-gray-900">{title}</h3>}{subtitle && <p className="text-sm text-gray-500 mt-1">{subtitle}</p>}</div>
        {headerAction && <div>{headerAction}</div>}
      </div>
    )}
    <div className={noPadding ? '' : 'p-6'}>{children}</div>
    {footer && <div className="px-6 py-4 border-t border-gray-200 bg-gray-50 rounded-b-xl">{footer}</div>}
  </div>
);

export default Card;
