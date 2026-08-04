import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../../api/client'
import { useApiCall } from '../../api/useApiCall'
import type {
  ApriSessioneNuovoTavoloRequest,
  ApriSessioneRequest,
  SessioneDettaglioResponse,
  SessioneResponse,
  TavoloResponse,
} from '../../api/types'
import { accoda } from '../../offline/queue'
import { idrataSessioneLocaleDaServer, salvaSessioneLocale } from '../../offline/sessioneLocale'
import { leggiSessioneDiTavolo, registraSessioneDiTavolo } from '../../offline/indiceTavoli'

export function CameriereTavoliPage() {
  const navigate = useNavigate()
  const { dati: tavoli, errore, inCorso } = useApiCall(() => api.get<TavoloResponse[]>('/api/tavoli'))

  const [tavoloSelezionato, setTavoloSelezionato] = useState<TavoloResponse | null>(null)
  const [numeroCoperti, setNumeroCoperti] = useState('2')
  const [erroreApertura, setErroreApertura] = useState<string | null>(null)
  const [aperturaInCorso, setAperturaInCorso] = useState(false)
  const [tavoloInRecupero, setTavoloInRecupero] = useState<number | null>(null)

  // Apertura di un tavolo nuovo (o riuso di uno libero per numero): a
  // differenza dell'apertura su un tavolo gia' scelto dalla griglia sotto,
  // e' un'operazione online-only (un tavolo non puo' nascere offline, vedi
  // Tavolo), quindi niente coda di sync in caso di errore di rete.
  const [numeroTavoloNuovo, setNumeroTavoloNuovo] = useState('')
  const [copertiTavoloNuovo, setCopertiTavoloNuovo] = useState('2')
  const [erroreTavoloNuovo, setErroreTavoloNuovo] = useState<string | null>(null)
  const [aperturaTavoloNuovoInCorso, setAperturaTavoloNuovoInCorso] = useState(false)

  async function clickTavolo(tavolo: TavoloResponse) {
    setErroreApertura(null)
    if (tavolo.stato === 'OCCUPATO') {
      const sessioneId = leggiSessioneDiTavolo(tavolo.id)
      if (sessioneId) {
        navigate(`/cameriere/sessioni/${sessioneId}`)
        return
      }
      // Sessione aperta da un altro dispositivo: non e' nell'indice locale
      // (vedi offline/indiceTavoli.ts), va recuperata dal server.
      setTavoloInRecupero(tavolo.id)
      try {
        const dettaglio = await api.get<SessioneDettaglioResponse>(`/api/sessioni/per-tavolo/${tavolo.id}`)
        idrataSessioneLocaleDaServer(dettaglio)
        registraSessioneDiTavolo(tavolo.id, dettaglio.id)
        navigate(`/cameriere/sessioni/${dettaglio.id}`)
      } catch (err) {
        setErroreApertura(
          err instanceof ApiError ? err.message : 'Errore di rete: il recupero della sessione richiede connessione',
        )
      } finally {
        setTavoloInRecupero(null)
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

  /**
   * Apre un tavolo dato solo il numero: lo crea al volo se non esiste
   * ancora (o riusa quello libero con lo stesso numero). Online-only, niente
   * fallback offline: un tavolo nuovo non puo' essere creato senza
   * connessione (vedi Tavolo), quindi qui un errore di rete si mostra e
   * basta, senza accodare nulla.
   */
  async function apriTavoloNuovo(e: FormEvent) {
    e.preventDefault()
    if (!numeroTavoloNuovo.trim()) return
    setErroreTavoloNuovo(null)
    setAperturaTavoloNuovoInCorso(true)

    const sessioneId = crypto.randomUUID()
    const request: ApriSessioneNuovoTavoloRequest = {
      id: sessioneId,
      numeroTavolo: numeroTavoloNuovo.trim(),
      numeroCoperti: Number(copertiTavoloNuovo),
    }

    try {
      const risposta = await api.post<SessioneResponse>('/api/sessioni/nuovo-tavolo', request)
      salvaSessioneLocale(sessioneId, {
        sessione: { id: sessioneId, tavoloId: risposta.tavoloId, numeroCoperti: risposta.numeroCoperti },
        pendente: false,
        tavoliAggregatiIds: [],
        comande: [],
      })
      registraSessioneDiTavolo(risposta.tavoloId, sessioneId)
      navigate(`/cameriere/sessioni/${sessioneId}`)
    } catch (err) {
      setErroreTavoloNuovo(
        err instanceof ApiError ? err.message : 'Errore di rete: la creazione del tavolo richiede connessione',
      )
    } finally {
      setAperturaTavoloNuovoInCorso(false)
    }
  }

  return (
    <>
      <h1>Tavoli</h1>

      <div className="card" style={{ maxWidth: 420 }}>
        <h2>Nuovo tavolo</h2>
        {erroreTavoloNuovo && <div className="messaggio-errore">{erroreTavoloNuovo}</div>}
        <form onSubmit={apriTavoloNuovo}>
          <div className="griglia">
            <div className="campo">
              <label htmlFor="numero-tavolo-nuovo">Numero tavolo</label>
              <input
                id="numero-tavolo-nuovo"
                value={numeroTavoloNuovo}
                onChange={(e) => setNumeroTavoloNuovo(e.target.value)}
                required
              />
            </div>
            <div className="campo">
              <label htmlFor="coperti-tavolo-nuovo">Coperti</label>
              <input
                id="coperti-tavolo-nuovo"
                type="number"
                min={1}
                value={copertiTavoloNuovo}
                onChange={(e) => setCopertiTavoloNuovo(e.target.value)}
                required
              />
            </div>
          </div>
          <button type="submit" className="pulsante" disabled={aperturaTavoloNuovoInCorso || !numeroTavoloNuovo.trim()}>
            {aperturaTavoloNuovoInCorso ? 'Apertura…' : 'Apri tavolo'}
          </button>
        </form>
      </div>

      <h2 style={{ marginTop: '1.5rem' }}>Tavoli esistenti</h2>
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
              disabled={tavoloInRecupero === tavolo.id}
              style={tavolo.stato === 'OCCUPATO' ? { borderColor: '#fecaca' } : undefined}
            >
              <span>{tavolo.numero}</span>
              <span className={`badge ${tavolo.stato.toLowerCase()}`}>
                {tavoloInRecupero === tavolo.id ? 'apertura…' : tavolo.stato}
              </span>
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
