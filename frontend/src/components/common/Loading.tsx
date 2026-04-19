import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingProps { size?: 'sm' | 'md' | 'lg'; text?: string; fullScreen?: boolean; }

const Loading: React.FC<LoadingProps> = ({ size = 'md', text, fullScreen = false }) => {
  const sizes = { sm: 'w-4 h-4', md: 'w-8 h-8', lg: 'w-12 h-12' };
  const content = (
    <div className="flex flex-col items-center justify-center">
      <Loader2 className={`${sizes[size]} animate-spin text-primary-600`} />
      {text && <p className="mt-2 text-sm text-gray-500">{text}</p>}
    </div>
  );
  if (fullScreen) return <div className="fixed inset-0 bg-white bg-opacity-75 flex items-center justify-center z-50">{content}</div>;
  return content;
};

export default Loading;
