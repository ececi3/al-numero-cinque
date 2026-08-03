import { type FormEvent, useState } from 'react'
import { api, ApiError } from '../../api/client'
import { useApiCall } from '../../api/useApiCall'
import type { CreaTavoloRequest, TavoloResponse } from '../../api/types'

export function AdminTavoliPage() {
  const { dati: tavoli, errore, inCorso, ricarica } = useApiCall(() => api.get<TavoloResponse[]>('/api/tavoli'))

  const [numero, setNumero] = useState('')
  const [erroreForm, setErroreForm] = useState<string | null>(null)
  const [invioInCorso, setInvioInCorso] = useState(false)

  async function creaTavolo(e: FormEvent) {
    e.preventDefault()
    setErroreForm(null)
    setInvioInCorso(true)
    try {
      const request: CreaTavoloRequest = { numero }
      await api.post('/api/admin/tavoli', request)
      setNumero('')
      ricarica()
    } catch (err) {
      setErroreForm(err instanceof ApiError ? err.message : 'Errore di rete')
    } finally {
      setInvioInCorso(false)
    }
  }

  return (
    <>
      <h1>Tavoli</h1>

      <div className="card">
        <h2>Nuovo tavolo</h2>
        {erroreForm && <div className="messaggio-errore">{erroreForm}</div>}
        <form onSubmit={creaTavolo} style={{ display: 'flex', gap: '0.75rem', alignItems: 'flex-end' }}>
          <div className="campo" style={{ marginBottom: 0 }}>
            <label htmlFor="numero-tavolo">Numero/nome tavolo</label>
            <input id="numero-tavolo" value={numero} onChange={(e) => setNumero(e.target.value)} required />
          </div>
          <button className="pulsante" disabled={invioInCorso} type="submit">
            {invioInCorso ? 'Creazione…' : 'Crea'}
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Elenco</h2>
        {inCorso && <p>Caricamento…</p>}
        {errore && <div className="messaggio-errore">{errore}</div>}
        {tavoli && (
          <table>
            <thead>
              <tr>
                <th>Tavolo</th>
                <th>Stato</th>
              </tr>
            </thead>
            <tbody>
              {tavoli.map((tavolo) => (
                <tr key={tavolo.id}>
                  <td>{tavolo.numero}</td>
                  <td>
                    <span className={`badge ${tavolo.stato.toLowerCase()}`}>{tavolo.stato}</span>
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
