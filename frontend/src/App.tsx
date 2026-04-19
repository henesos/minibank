import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import MainLayout from './components/layout/MainLayout'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import DashboardPage from './pages/dashboard/DashboardPage'
import AccountsPage from './pages/accounts/AccountsPage'
import TransactionsPage from './pages/transactions/TransactionsPage'
import ProfilePage from './pages/profile/ProfilePage'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { token } = useAuthStore()
  return token ? <>{children}</> : <Navigate to="/login" replace />
}

function PublicRoute({ children }: { children: React.ReactNode }) {
  const { token } = useAuthStore()
  return !token ? <>{children}</> : <Navigate to="/dashboard" replace />
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public Routes */}
        <Route path="/login" element={<PublicRoute><LoginPage /></PublicRoute>} />
        <Route path="/register" element={<PublicRoute><RegisterPage /></PublicRoute>} />

        {/* Protected Routes */}
        <Route path="/" element={<PrivateRoute><MainLayout /></PrivateRoute>}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="accounts" element={<AccountsPage />} />
          <Route path="transactions" element={<TransactionsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>

        {/* Catch all */}
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
