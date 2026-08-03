import { useEffect, useState } from 'react'
import { Client } from '@stomp/stompjs'
import { api, ApiError } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import type { GruppoInvioResponse, StatoGruppo } from '../../api/types'

interface EventoKds {
  gruppoInvioId: number
  comandaId: string
  numeroPortata: number
  seqCoda: number | null
  stato: StatoGruppo
}

const COLONNE: { stato: StatoGruppo; titolo: string; azione?: { label: string; path: string } }[] = [
  { stato: 'IN_CODA', titolo: 'In coda', azione: { label: 'Inizia preparazione', path: 'inizia-preparazione' } },
  { stato: 'IN_PREP', titolo: 'In preparazione', azione: { label: 'Pronto', path: 'pronto' } },
  { stato: 'PRONTO', titolo: 'Pronto', azione: { label: 'Servito', path: 'servito' } },
]

export function KdsPage() {
  const { auth } = useAuth()
  const [coda, setCoda] = useState<GruppoInvioResponse[]>([])
  const [erroreCaricamento, setErroreCaricamento] = useState<string | null>(null)
  const [connesso, setConnesso] = useState(false)
  const [azioneInCorsoId, setAzioneInCorsoId] = useState<number | null>(null)
  const [erroreAzione, setErroreAzione] = useState<string | null>(null)

  useEffect(() => {
    api
      .get<GruppoInvioResponse[]>('/api/kds/coda')
      .then(setCoda)
      .catch((e) => setErroreCaricamento(e instanceof ApiError ? e.message : 'Errore di rete'))
  }, [])

  useEffect(() => {
    if (!auth) return

    function applicaEvento(evento: EventoKds) {
      setCoda((coda) => {
        const senzaQuesto = coda.filter((g) => g.id !== evento.gruppoInvioId)
        if (evento.stato === 'SERVITO') {
          return senzaQuesto
        }
        const aggiornato: GruppoInvioResponse = {
          id: evento.gruppoInvioId,
          comandaId: evento.comandaId,
          numeroPortata: evento.numeroPortata,
          stato: evento.stato,
          seqCoda: evento.seqCoda,
        }
        return [...senzaQuesto, aggiornato].sort((a, b) => (a.seqCoda ?? 0) - (b.seqCoda ?? 0))
      })
    }

    const protocollo = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${protocollo}://${window.location.host}/ws-kds?access_token=${encodeURIComponent(auth.token)}`,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnesso(true)
        client.subscribe('/topic/kds', (messaggio) => {
          try {
            applicaEvento(JSON.parse(messaggio.body) as EventoKds)
          } catch {
            // payload non deserializzabile: ignorato
          }
        })
      },
      onWebSocketClose: () => setConnesso(false),
      onStompError: () => setConnesso(false),
    })
    client.activate()
    return () => {
      client.deactivate()
    }
  }, [auth])

  async function eseguiTransizione(gruppo: GruppoInvioResponse, path: string) {
    setAzioneInCorsoId(gruppo.id)
    setErroreAzione(null)
    try {
      const aggiornato = await api.post<GruppoInvioResponse>(`/api/kds/gruppi/${gruppo.id}/${path}`)
      setCoda((coda) =>
        aggiornato.stato === 'SERVITO' ? coda.filter((g) => g.id !== gruppo.id) : coda.map((g) => (g.id === gruppo.id ? aggiornato : g)),
      )
    } catch (err) {
      setErroreAzione(err instanceof ApiError ? err.message : 'Errore di rete')
    } finally {
      setAzioneInCorsoId(null)
    }
  }

  return (
    <>
      <h1>
        Coda cucina
        <span className={`badge ${connesso ? 'attivo' : 'inattivo'}`} style={{ marginLeft: '0.6rem' }}>
          {connesso ? 'live' : 'riconnessione…'}
        </span>
      </h1>
      {erroreCaricamento && <div className="messaggio-errore">{erroreCaricamento}</div>}
      {erroreAzione && <div className="messaggio-errore">{erroreAzione}</div>}

      <div style={{ display: 'flex', gap: '1rem', overflowX: 'auto', alignItems: 'flex-start' }}>
        {COLONNE.map((colonna) => {
          const gruppi = coda.filter((g) => g.stato === colonna.stato).sort((a, b) => (a.seqCoda ?? 0) - (b.seqCoda ?? 0))
          return (
            <div key={colonna.stato} className="colonna-kds">
              <h2>
                {colonna.titolo} ({gruppi.length})
              </h2>
              {gruppi.length === 0 && <p style={{ color: 'var(--colore-testo-debole)' }}>—</p>}
              {gruppi.map((gruppo) => (
                <div key={gruppo.id} className="gruppo-kds">
                  <strong>Comanda {gruppo.comandaId.slice(0, 8)}</strong>
                  <p style={{ margin: '0.35rem 0' }}>Portata {gruppo.numeroPortata}</p>
                  {colonna.azione && (
                    <button
                      className="pulsante piccolo"
                      disabled={azioneInCorsoId === gruppo.id}
                      onClick={() => eseguiTransizione(gruppo, colonna.azione!.path)}
                    >
                      {colonna.azione.label}
                    </button>
                  )}
                </div>
              ))}
            </div>
          )
        })}
      </div>
    </>
  )
}
