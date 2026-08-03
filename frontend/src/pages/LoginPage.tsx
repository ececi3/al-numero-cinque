import { type FormEvent, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'

export function LoginPage() {
  const { auth, login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errore, setErrore] = useState<string | null>(null)
  const [inCorso, setInCorso] = useState(false)

  if (auth) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setErrore(null)
    setInCorso(true)
    try {
      await login(username, password)
    } catch (err) {
      setErrore(err instanceof ApiError ? err.message : 'Impossibile contattare il server')
    } finally {
      setInCorso(false)
    }
  }

  return (
    <div className="pagina-centrata">
      <form className="card" style={{ width: '100%', maxWidth: 360 }} onSubmit={handleSubmit}>
        <h1>al numero cinque</h1>
        {errore && <div className="messaggio-errore">{errore}</div>}
        <div className="campo">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            autoFocus
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
          />
        </div>
        <div className="campo">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </div>
        <button type="submit" className="pulsante" disabled={inCorso} style={{ width: '100%' }}>
          {inCorso ? 'Accesso in corso…' : 'Accedi'}
        </button>
      </form>
    </div>
  )
}
