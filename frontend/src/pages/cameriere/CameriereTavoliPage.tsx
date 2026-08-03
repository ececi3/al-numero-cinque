import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../../api/client'
import { useApiCall } from '../../api/useApiCall'
import type { ApriSessioneRequest, TavoloResponse } from '../../api/types'
import { accoda } from '../../offline/queue'
import { salvaSessioneLocale } from '../../offline/sessioneLocale'
import { leggiSessioneDiTavolo, registraSessioneDiTavolo } from '../../offline/indiceTavoli'

export function CameriereTavoliPage() {
  const navigate = useNavigate()
  const { dati: tavoli, errore, inCorso } = useApiCall(() => api.get<TavoloResponse[]>('/api/tavoli'))

  const [tavoloSelezionato, setTavoloSelezionato] = useState<TavoloResponse | null>(null)
  const [numeroCoperti, setNumeroCoperti] = useState('2')
  const [erroreApertura, setErroreApertura] = useState<string | null>(null)
  const [aperturaInCorso, setAperturaInCorso] = useState(false)

  function clickTavolo(tavolo: TavoloResponse) {
    setErroreApertura(null)
    if (tavolo.stato === 'OCCUPATO') {
      const sessioneId = leggiSessioneDiTavolo(tavolo.id)
      if (sessioneId) {
        navigate(`/cameriere/sessioni/${sessioneId}`)
      } else {
        setErroreApertura('Tavolo occupato: la sessione non è nota su questo dispositivo (apri sessione da chi lo ha occupato).')
      }
      return
    }
    setTavoloSelezionato(tavolo)
  }

  async function apriSessione(e: FormEvent) {
    e.preventDefault()
    if (!tavoloSelezionato) return
    setErroreApertura(null)
    setAperturaInCorso(true)

    const sessioneId = crypto.randomUUID()
    const request: ApriSessioneRequest = {
      id: sessioneId,
      tavoloId: tavoloSelezionato.id,
      numeroCoperti: Number(numeroCoperti),
    }

    let pendente = false
    try {
      await api.post('/api/sessioni', request)
    } catch (err) {
      if (err instanceof ApiError) {
        setErroreApertura(err.message)
        setAperturaInCorso(false)
        return
      }
      // Nessuna risposta dal server: probabile assenza di rete. Si accoda
      // per il retry automatico e si procede comunque in locale, coerente
      // con l'apertura sessione append-capable (id generato dal client).
      accoda({ tipo: 'apri-sessione', payload: request })
      pendente = true
    }

    salvaSessioneLocale(sessioneId, { sessione: request, pendente, tavoliAggregatiIds: [], comande: [] })
    registraSessioneDiTavolo(tavoloSelezionato.id, sessioneId)
    setAperturaInCorso(false)
    navigate(`/cameriere/sessioni/${sessioneId}`)
  }

  return (
    <>
      <h1>Tavoli</h1>
      {inCorso && <p>Caricamento…</p>}
      {errore && <div className="messaggio-errore">{errore}</div>}
      {!tavoloSelezionato && erroreApertura && <div className="messaggio-errore">{erroreApertura}</div>}

      {tavoli && (
        <div className="griglia">
          {tavoli.map((tavolo) => (
            <button
              key={tavolo.id}
              className="tavolo-scelta"
              onClick={() => clickTavolo(tavolo)}
              style={tavolo.stato === 'OCCUPATO' ? { borderColor: '#fecaca' } : undefined}
            >
              <span>{tavolo.numero}</span>
              <span className={`badge ${tavolo.stato.toLowerCase()}`}>{tavolo.stato}</span>
            </button>
          ))}
        </div>
      )}

      {tavoloSelezionato && (
        <div className="card" style={{ marginTop: '1.25rem', maxWidth: 360 }}>
          <h2>Apri sessione — tavolo {tavoloSelezionato.numero}</h2>
          {erroreApertura && <div className="messaggio-errore">{erroreApertura}</div>}
          <form onSubmit={apriSessione}>
            <div className="campo">
              <label htmlFor="coperti">Numero coperti</label>
              <input
                id="coperti"
                type="number"
                min={1}
                value={numeroCoperti}
                onChange={(e) => setNumeroCoperti(e.target.value)}
                required
                autoFocus
              />
            </div>
            <div className="elenco-azioni">
              <button type="submit" className="pulsante" disabled={aperturaInCorso}>
                {aperturaInCorso ? 'Apertura…' : 'Apri sessione'}
              </button>
              <button type="button" className="pulsante secondario" onClick={() => setTavoloSelezionato(null)}>
                Annulla
              </button>
            </div>
          </form>
        </div>
      )}
    </>
  )
}
