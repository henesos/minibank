import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api'
import { useAuthStore } from '../store/authStore'
import type { LoginRequest, RegisterRequest } from '../types'
import { useNotification } from './useNotification'

export const useAuth = () => {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { user, token, isAuthenticated, setAuth, logout: logoutStore, setLoading } = useAuthStore()
  const { showSuccess, showError } = useNotification()

  const loginMutation = useMutation({
    mutationFn: (credentials: LoginRequest) => authApi.login(credentials),
    onMutate: () => setLoading(true),
    onSuccess: (data) => {
      setAuth(data.user, data.accessToken, data.refreshToken)
      showSuccess('Welcome back, ' + data.user.firstName + '!')
      navigate('/dashboard')
    },
    onError: (error: any) => {
      showError(error.message || 'Login failed. Please check your credentials.')
    },
    onSettled: () => setLoading(false),
  })

  const registerMutation = useMutation({
    mutationFn: (data: RegisterRequest) => authApi.register(data),
    onMutate: () => setLoading(true),
    onSuccess: (data) => {
      setAuth(data.user, data.accessToken, data.refreshToken)
      showSuccess('Account created successfully!')
      navigate('/dashboard')
    },
    onError: (error: any) => {
      showError(error.message || 'Registration failed. Please try again.')
    },
    onSettled: () => setLoading(false),
  })

  const logoutMutation = useMutation({
    mutationFn: () => {
      authApi.logout()
      return Promise.resolve()
    },
    onSuccess: () => {
      logoutStore()
      queryClient.clear()
      navigate('/login')
      showSuccess('Logged out successfully')
    },
  })

  const { data: currentUser, isLoading } = useQuery({
    queryKey: ['currentUser'],
    queryFn: authApi.getCurrentUser,
    enabled: !!token && !user,
    retry: false,
  })

  // Auto-set user when fetched
  if (currentUser && !user) {
    useAuthStore.getState().setUser(currentUser)
  }

  return {
    user,
    token,
    isAuthenticated,
    isLoading: isLoading || loginMutation.isPending || registerMutation.isPending,
    login: loginMutation.mutate,
    register: registerMutation.mutate,
    logout: logoutMutation.mutate,
  }
}
