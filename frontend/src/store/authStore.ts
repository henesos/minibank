import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User } from '../types'

interface AuthState {
  user: User | null
  token: string | null
  refreshToken: string | null
  isAuthenticated: boolean
  isLoading: boolean

  // Actions
  setAuth: (user: User, token: string, refreshToken: string) => void
  setUser: (user: User) => void
  setToken: (token: string, refreshToken?: string) => void
  setLoading: (loading: boolean) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      refreshToken: null,
      isAuthenticated: false,
      isLoading: false,

      setAuth: (user, token, refreshToken) =>
        set({
          user,
          token,
          refreshToken,
          isAuthenticated: true,
          isLoading: false,
        }),

      setUser: (user) =>
        set({ user }),

      setToken: (token, refreshToken) =>
        set({ token, ...(refreshToken && { refreshToken }) }),

      setLoading: (isLoading) =>
        set({ isLoading }),

      logout: () =>
        set({
          user: null,
          token: null,
          refreshToken: null,
          isAuthenticated: false,
          isLoading: false,
        }),
    }),
    {
      name: 'minibank-auth',
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        refreshToken: state.refreshToken,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
)
