import { api } from '../../api/client'
import { useApiCall } from '../../api/useApiCall'
import type { AnalyticsResponse } from '../../api/types'

export function AdminAnalyticsPage() {
  const { dati, errore, inCorso } = useApiCall(() => api.get<AnalyticsResponse>('/api/admin/analytics'))

  return (
    <>
      <h1>Analytics</h1>
      {inCorso && <p>Caricamento…</p>}
      {errore && <div className="messaggio-errore">{errore}</div>}
      {dati && (
        <>
          <div className="griglia">
            <div className="card">
              <h2>Sessioni totali</h2>
              <p style={{ fontSize: '2rem', fontWeight: 700, margin: 0 }}>{dati.numeroSessioni}</p>
            </div>
            <div className="card">
              <h2>Coperti totali</h2>
              <p style={{ fontSize: '2rem', fontWeight: 700, margin: 0 }}>{dati.totaleCoperti}</p>
            </div>
            <div className="card">
              <h2>Comande totali</h2>
              <p style={{ fontSize: '2rem', fontWeight: 700, margin: 0 }}>{dati.numeroComande}</p>
            </div>
          </div>

          <div className="card">
            <h2>Tempo medio di preparazione per portata</h2>
            {dati.tempiMediPreparazionePerPortata.length === 0 ? (
              <p>Nessun dato ancora disponibile.</p>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Portata</th>
                    <th>Minuti medi</th>
                    <th>Campioni</th>
                  </tr>
                </thead>
                <tbody>
                  {dati.tempiMediPreparazionePerPortata.map((riga) => (
                    <tr key={riga.numeroPortata}>
                      <td>{riga.numeroPortata}</td>
                      <td>{riga.minutiMediPreparazione.toFixed(1)}</td>
                      <td>{riga.campioni}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </>
  )
}
