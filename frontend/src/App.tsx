import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/layouts/AppLayout'
import { DashboardPage } from '@/pages/DashboardPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { AuthPage } from '@/pages/AuthPage'
import { AuthProvider } from '@/auth/AuthProvider'
import { AuthGate } from '@/auth/AuthRoutes'
import { navigation } from '@/lib/navigation'
import './App.css'

function App() {
  return (
    <BrowserRouter><AuthProvider><Routes>
      <Route element={<AuthGate publicOnly />}>
        <Route path="/login" element={<AuthPage key="login" />} />
        <Route path="/register" element={<AuthPage key="register" register />} />
      </Route>
      <Route element={<AuthGate />}><Route element={<AppLayout />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        {navigation.slice(1).map(page => <Route key={page.path} path={page.path} element={<PlaceholderPage />} />)}
        <Route path="*" element={<PlaceholderPage />} />
      </Route></Route>
    </Routes></AuthProvider></BrowserRouter>
  )
}
export default App
