import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useOnlineStatus } from '../offline/useOnlineStatus'
import { useCodaOffline } from '../offline/useCodaOffline'

const VOCI_NAV: Record<string, { to: string; label: string }[]> = {
  CAMERIERE: [{ to: '/cameriere', label: 'Tavoli' }],
  CUCINA: [{ to: '/kds', label: 'Coda cucina' }],
  ADMIN: [
    { to: '/admin/analytics', label: 'Analytics' },
    { to: '/admin/utenti', label: 'Utenti' },
    { to: '/admin/tavoli', label: 'Tavoli' },
    { to: '/admin/menu', label: 'Menu' },
  ],
}

export function AppLayout() {
  const { auth, logout } = useAuth()
  const online = useOnlineStatus()
  const codaOffline = useCodaOffline()

  if (!auth) return null

  return (
    <div className="app-shell">
      {!online && <div className="striscia-offline">Connessione assente — le operazioni verranno sincronizzate al ritorno online</div>}
      {online && codaOffline.length > 0 && (
        <div className="striscia-offline">
          Sincronizzazione in corso: {codaOffline.length} operazion{codaOffline.length === 1 ? 'e' : 'i'} in coda
        </div>
      )}
      <header className="barra-nav">
        <span className="titolo">al numero cinque</span>
        <nav>
          {VOCI_NAV[auth.ruolo].map((voce) => (
            <NavLink key={voce.to} to={voce.to} className={({ isActive }) => (isActive ? 'attivo' : '')}>
              {voce.label}
            </NavLink>
          ))}
        </nav>
        <div className="utente-corrente">
          <span>
            {auth.username} · {auth.ruolo}
          </span>
          <NavLink to="/cambia-password" className="pulsante secondario piccolo">
            Password
          </NavLink>
          <button className="pulsante secondario piccolo" onClick={logout}>
            Esci
          </button>
        </div>
      </header>
      <main className="contenuto">
        <Outlet />
      </main>
    </div>
  )
}
