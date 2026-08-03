import { type FormEvent, useState } from 'react'
import { api, ApiError } from '../api/client'
import type { CambiaPasswordRequest } from '../api/types'

export function CambiaPasswordPage() {
  const [vecchiaPassword, setVecchiaPassword] = useState('')
  const [nuovaPassword, setNuovaPassword] = useState('')
  const [errore, setErrore] = useState<string | null>(null)
  const [fatto, setFatto] = useState(false)
  const [inCorso, setInCorso] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setErrore(null)
    setFatto(false)
    setInCorso(true)
    try {
      const request: CambiaPasswordRequest = { vecchiaPassword, nuovaPassword }
      await api.post('/api/auth/cambia-password', request)
      setVecchiaPassword('')
      setNuovaPassword('')
      setFatto(true)
    } catch (err) {
      setErrore(err instanceof ApiError ? err.message : 'Errore di rete')
    } finally {
      setInCorso(false)
    }
  }

  return (
    <>
      <h1>Cambia password</h1>
      <div className="card" style={{ maxWidth: 420 }}>
        {errore && <div className="messaggio-errore">{errore}</div>}
        {fatto && <div className="messaggio-info">Password aggiornata.</div>}
        <form onSubmit={handleSubmit}>
          <div className="campo">
            <label htmlFor="vecchia-password">Password attuale</label>
            <input
              id="vecchia-password"
              type="password"
              value={vecchiaPassword}
              onChange={(e) => setVecchiaPassword(e.target.value)}
              required
            />
          </div>
          <div className="campo">
            <label htmlFor="nuova-password">Nuova password (min. 8 caratteri)</label>
            <input
              id="nuova-password"
              type="password"
              value={nuovaPassword}
              onChange={(e) => setNuovaPassword(e.target.value)}
              minLength={8}
              required
            />
          </div>
          <button className="pulsante" disabled={inCorso} type="submit">
            {inCorso ? 'Aggiornamento…' : 'Aggiorna password'}
          </button>
        </form>
      </div>
    </>
  )
}
