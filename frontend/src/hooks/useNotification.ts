import { useState, useCallback } from 'react';

interface Notification { id: string; type: 'success' | 'error' | 'warning' | 'info'; message: string; }

export const useNotification = () => {
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const addNotification = useCallback((type: Notification['type'], message: string) => {
    const id = Date.now().toString();
    setNotifications((prev) => [...prev, { id, type, message }]);
    setTimeout(() => setNotifications((prev) => prev.filter((n) => n.id !== id)), 5000);
  }, []);

  return {
    notifications,
    removeNotification: useCallback((id: string) => setNotifications((prev) => prev.filter((n) => n.id !== id)), []),
    showSuccess: useCallback((message: string) => addNotification('success', message), [addNotification]),
    showError: useCallback((message: string) => addNotification('error', message), [addNotification]),
    showWarning: useCallback((message: string) => addNotification('warning', message), [addNotification]),
    showInfo: useCallback((message: string) => addNotification('info', message), [addNotification]),
  };
};
