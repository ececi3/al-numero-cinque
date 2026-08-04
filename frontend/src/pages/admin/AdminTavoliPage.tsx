import { api } from '../../api/client'
import { useApiCall } from '../../api/useApiCall'
import type { TavoloResponse } from '../../api/types'

/** Sola lettura: i tavoli nascono solo aprendo una sessione dal dispositivo cameriere (vedi CameriereTavoliPage). */
export function AdminTavoliPage() {
  const { dati: tavoli, errore, inCorso } = useApiCall(() => api.get<TavoloResponse[]>('/api/tavoli'))

  return (
    <>
      <h1>Tavoli</h1>

      <div className="card">
        <h2>Elenco</h2>
        <p style={{ color: 'var(--colore-testo-debole)', fontSize: '0.9rem' }}>
          Un tavolo nasce quando un cameriere apre una sessione dando il suo numero: qui e' visibile in sola lettura.
        </p>
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
