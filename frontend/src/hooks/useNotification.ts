import { useState, useCallback } from 'react'

interface Notification {
  id: string
  type: 'success' | 'error' | 'warning' | 'info'
  message: string
}

const notifications: Notification[] = []
const listeners: Set<() => void> = new Set()

const notify = () => listeners.forEach((listener) => listener())

export const useNotification = () => {
  const [, forceUpdate] = useState({})

  const subscribe = useCallback(() => {
    const listener = () => forceUpdate({})
    listeners.add(listener)
    return () => listeners.delete(listener)
  }, [])

  // Subscribe on mount
  useState(() => {
    subscribe()
  })

  const show = useCallback((type: Notification['type'], message: string) => {
    const id = Date.now().toString()
    notifications.push({ id, type, message })
    notify()

    // Auto remove after 5 seconds
    setTimeout(() => {
      const index = notifications.findIndex((n) => n.id === id)
      if (index > -1) {
        notifications.splice(index, 1)
        notify()
      }
    }, 5000)
  }, [subscribe])

  const showSuccess = useCallback((message: string) => show('success', message), [show])
  const showError = useCallback((message: string) => show('error', message), [show])
  const showWarning = useCallback((message: string) => show('warning', message), [show])
  const showInfo = useCallback((message: string) => show('info', message), [show])

  const remove = useCallback((id: string) => {
    const index = notifications.findIndex((n) => n.id === id)
    if (index > -1) {
      notifications.splice(index, 1)
      notify()
    }
  }, [])

  return {
    notifications,
    showSuccess,
    showError,
    showWarning,
    showInfo,
    remove,
  }
}
