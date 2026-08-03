import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppLayout } from './layout/AppLayout'
import { LoginPage } from './pages/LoginPage'
import { CambiaPasswordPage } from './pages/CambiaPasswordPage'
import { AdminUtentiPage } from './pages/admin/AdminUtentiPage'
import { AdminTavoliPage } from './pages/admin/AdminTavoliPage'
import { AdminMenuPage } from './pages/admin/AdminMenuPage'
import { AdminAnalyticsPage } from './pages/admin/AdminAnalyticsPage'
import { CameriereTavoliPage } from './pages/cameriere/CameriereTavoliPage'
import { CameriereSessionePage } from './pages/cameriere/CameriereSessionePage'
import { KdsPage } from './pages/kds/KdsPage'

function HomeRedirect() {
  const { auth } = useAuth()
  if (!auth) return <Navigate to="/login" replace />
  if (auth.ruolo === 'ADMIN') return <Navigate to="/admin/analytics" replace />
  if (auth.ruolo === 'CUCINA') return <Navigate to="/kds" replace />
  return <Navigate to="/cameriere" replace />
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route index element={<HomeRedirect />} />
              <Route path="cambia-password" element={<CambiaPasswordPage />} />

              <Route element={<ProtectedRoute ruoli={['CAMERIERE']} />}>
                <Route path="cameriere" element={<CameriereTavoliPage />} />
                <Route path="cameriere/sessioni/:sessioneId" element={<CameriereSessionePage />} />
              </Route>

              <Route element={<ProtectedRoute ruoli={['CUCINA']} />}>
                <Route path="kds" element={<KdsPage />} />
              </Route>

              <Route element={<ProtectedRoute ruoli={['ADMIN']} />}>
                <Route path="admin/utenti" element={<AdminUtentiPage />} />
                <Route path="admin/tavoli" element={<AdminTavoliPage />} />
                <Route path="admin/menu" element={<AdminMenuPage />} />
                <Route path="admin/analytics" element={<AdminAnalyticsPage />} />
              </Route>
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
