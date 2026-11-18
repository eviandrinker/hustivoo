import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/layouts/AppLayout'
import { DashboardPage } from '@/pages/DashboardPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { AuthPage } from '@/pages/AuthPage'
import { AuthProvider } from '@/auth/AuthProvider'
import { AuthGate } from '@/auth/AuthRoutes'
import { navigation } from '@/lib/navigation'
import { BusinessBoundary } from '@/business/BusinessProvider'
import { OnboardingPage } from '@/pages/OnboardingPage'
import { CustomersPage } from '@/pages/CustomersPage'
import { CustomerDetailPage } from '@/pages/CustomerDetailPage'
import { ServicesPage } from '@/pages/ServicesPage'
import { QuotesPage } from '@/pages/QuotesPage'
import { QuoteDetailPage } from '@/pages/QuoteDetailPage'
import { QuoteEditorPage } from '@/pages/QuoteEditorPage'
import './App.css'

const modulePages: Record<string, ReactNode> = { '/customers': <CustomersPage />, '/services': <ServicesPage />, '/quotes': <QuotesPage /> }

function App() {
  return (
    <BrowserRouter><AuthProvider><Routes>
      <Route element={<AuthGate publicOnly />}>
        <Route path="/login" element={<AuthPage key="login" />} />
        <Route path="/register" element={<AuthPage key="register" register />} />
      </Route>
      <Route element={<AuthGate />}><Route element={<BusinessBoundary />}>
        <Route path="/onboarding" element={<OnboardingPage />} />
        <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="customers/:customerId" element={<CustomerDetailPage />} />
        <Route path="quotes/new" element={<QuoteEditorPage />} />
        <Route path="quotes/:quoteId" element={<QuoteDetailPage />} />
        <Route path="quotes/:quoteId/edit" element={<QuoteEditorPage />} />
        <Route path="dashboard" element={<DashboardPage />} />
        {navigation.slice(1).map(page => <Route key={page.path} path={page.path} element={modulePages[page.path] ?? <PlaceholderPage />} />)}
        <Route path="*" element={<PlaceholderPage />} />
      </Route></Route></Route>
    </Routes></AuthProvider></BrowserRouter>
  )
}
export default App
