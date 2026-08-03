import { type FormEvent, useState } from 'react'
import { api, ApiError } from '../../api/client'
import { useApiCall } from '../../api/useApiCall'
import type { CreaUtenteRequest, Ruolo, UtenteResponse } from '../../api/types'

export function AdminUtentiPage() {
  const { dati: utenti, errore, inCorso, ricarica } = useApiCall(() => api.get<UtenteResponse[]>('/api/admin/utenti'))

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [ruolo, setRuolo] = useState<Ruolo>('CAMERIERE')
  const [erroreForm, setErroreForm] = useState<string | null>(null)
  const [invioInCorso, setInvioInCorso] = useState(false)

  async function creaUtente(e: FormEvent) {
    e.preventDefault()
    setErroreForm(null)
    setInvioInCorso(true)
    try {
      const request: CreaUtenteRequest = { username, password, ruolo }
      await api.post('/api/admin/utenti', request)
      setUsername('')
      setPassword('')
      ricarica()
    } catch (err) {
      setErroreForm(err instanceof ApiError ? err.message : 'Errore di rete')
    } finally {
      setInvioInCorso(false)
    }
  }

  async function disattiva(id: number) {
    setErroreForm(null)
    try {
      await api.post(`/api/admin/utenti/${id}/disattiva`)
      ricarica()
    } catch (err) {
      setErroreForm(err instanceof ApiError ? err.message : 'Errore di rete')
    }
  }

  return (
    <>
      <h1>Utenti</h1>
      {erroreForm && <div className="messaggio-errore">{erroreForm}</div>}

      <div className="card">
        <h2>Nuovo utente</h2>
        <form onSubmit={creaUtente}>
          <div className="griglia">
            <div className="campo">
              <label htmlFor="nuovo-username">Username</label>
              <input id="nuovo-username" value={username} onChange={(e) => setUsername(e.target.value)} required />
            </div>
            <div className="campo">
              <label htmlFor="nuova-password">Password (min. 8 caratteri)</label>
              <input
                id="nuova-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                minLength={8}
                required
              />
            </div>
            <div className="campo">
              <label htmlFor="nuovo-ruolo">Ruolo</label>
              <select id="nuovo-ruolo" value={ruolo} onChange={(e) => setRuolo(e.target.value as Ruolo)}>
                <option value="CAMERIERE">Cameriere</option>
                <option value="CUCINA">Cucina</option>
                <option value="ADMIN">Admin</option>
              </select>
            </div>
          </div>
          <button className="pulsante" disabled={invioInCorso} type="submit">
            {invioInCorso ? 'Creazione…' : 'Crea utente'}
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Elenco</h2>
        {inCorso && <p>Caricamento…</p>}
        {errore && <div className="messaggio-errore">{errore}</div>}
        {utenti && (
          <table>
            <thead>
              <tr>
                <th>Username</th>
                <th>Ruolo</th>
                <th>Stato</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {utenti.map((utente) => (
                <tr key={utente.id}>
                  <td>{utente.username}</td>
                  <td>{utente.ruolo}</td>
                  <td>
                    <span className={`badge ${utente.attivo ? 'attivo' : 'inattivo'}`}>
                      {utente.attivo ? 'attivo' : 'disattivato'}
                    </span>
                  </td>
                  <td>
                    {utente.attivo && (
                      <button className="pulsante secondario piccolo" onClick={() => disattiva(utente.id)}>
                        Disattiva
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
