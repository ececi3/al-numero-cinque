import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'
import type { Ruolo } from '../api/types'

export function ProtectedRoute({ ruoli }: { ruoli?: Ruolo[] }) {
  const { auth } = useAuth()

  if (!auth) {
    return <Navigate to="/login" replace />
  }

  if (ruoli && !ruoli.includes(auth.ruolo)) {
    return (
      <div className="pagina-centrata">
        <p>Non hai i permessi per accedere a questa sezione.</p>
      </div>
    )
  }

  return <Outlet />
}
