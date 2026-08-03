import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../../api/client'
import { useApiCall } from '../../api/useApiCall'
import type {
  GruppoRequest,
  MenuItemResponse,
  SessioneDettaglioResponse,
  SincronizzaComandaRequest,
  TavoloResponse,
} from '../../api/types'
import { accoda } from '../../offline/queue'
import {
  aggiornaTavoliAggregati,
  aggiungiComandaLocale,
  idrataSessioneLocaleDaServer,
  leggiSessioneLocale,
  sincronizzaComandeDaServer,
  type ComandaVista,
  type RigaVista,
} from '../../offline/sessioneLocale'
import { registraSessioneDiTavolo, rimuoviSessioneDiTavolo } from '../../offline/indiceTavoli'

interface RigaInPreparazione extends RigaVista {
  numeroPortata: number
}

/** Raggruppa il menu per categoria cosi' il cameriere puo' scorrerlo per sezioni, non come un unico elenco piatto. */
function raggruppaPerCategoria(menu: MenuItemResponse[]): [string, MenuItemResponse[]][] {
  const gruppi = new Map<string, MenuItemResponse[]>()
  for (const voce of menu) {
    const categoria = voce.categoria ?? 'Senza categoria'
    const voci = gruppi.get(categoria) ?? []
    voci.push(voce)
    gruppi.set(categoria, voci)
  }
  return [...gruppi.entries()].sort(([a], [b]) => a.localeCompare(b))
}

export function CameriereSessionePage() {
  // Il parametro di rotta e' garantito dalla route "/cameriere/sessioni/:sessioneId";
  // fissato qui come string (invece di string | undefined) cosi' che la
  // narrowing del controllo qui sotto valga anche dentro alle funzioni
  // annidate (TS non propaga la narrowing di un guard nei closure).
  const sessioneId = useParams<{ sessioneId: string }>().sessioneId as string
  const navigate = useNavigate()

  const [sessioneLocale, setSessioneLocale] = useState(() => (sessioneId ? leggiSessioneLocale(sessioneId) : undefined))
  const [recuperoInCorso, setRecuperoInCorso] = useState(false)
  const [erroreRecupero, setErroreRecupero] = useState<string | null>(null)
  const { dati: menu } = useApiCall(() => api.get<MenuItemResponse[]>('/api/menu'))
  const { dati: tavoli } = useApiCall(() => api.get<TavoloResponse[]>('/api/tavoli'))

  // Sessione assente in locale: puo' essere stata aperta da un altro
  // dispositivo (vedi offline/indiceTavoli.ts, che indicizza solo sul
  // dispositivo di apertura). Si tenta il recupero dal server prima di
  // arrendersi: richiede connessione, coerente con le altre operazioni
  // online-only (aggregazione tavoli, transizioni cucina).
  useEffect(() => {
    if (sessioneLocale || !sessioneId) return
    setRecuperoInCorso(true)
    setErroreRecupero(null)
    api
      .get<SessioneDettaglioResponse>(`/api/sessioni/${sessioneId}`)
      .then((dettaglio) => {
        setSessioneLocale(idrataSessioneLocaleDaServer(dettaglio))
        registraSessioneDiTavolo(dettaglio.tavoloId, dettaglio.id)
      })
      .catch((e) =>
        setErroreRecupero(e instanceof ApiError ? e.message : 'Errore di rete: il recupero della sessione richiede connessione'),
      )
      .finally(() => setRecuperoInCorso(false))
  }, [sessioneId, sessioneLocale])

  // Lo stato di ogni portata (es. "pronta", "servita") arriva dalla cucina in
  // tempo reale via WebSocket, ma quel canale e' riservato al ruolo CUCINA
  // (vedi KdsHandshakeAuthInterceptor): il dispositivo cameriere non puo'
  // ascoltarlo. Un polling periodico e' il modo piu' semplice per non
  // lasciare il cameriere con badge di stato bloccati al momento dell'invio
  // (es. "IN_CODA" anche quando la cucina ha gia' servito la portata).
  // Nessuna richiesta finche' l'apertura sessione stessa e' pendente (offline):
  // la sessione non esiste ancora lato server.
  useEffect(() => {
    if (!sessioneId || !sessioneLocale || sessioneLocale.pendente) return
    const intervallo = setInterval(() => {
      api
        .get<SessioneDettaglioResponse>(`/api/sessioni/${sessioneId}`)
        .then((dettaglio) => {
          sincronizzaComandeDaServer(sessioneId, dettaglio.comande)
          setSessioneLocale(leggiSessioneLocale(sessioneId))
        })
        .catch(() => {
          // Rete assente o momentaneamente irraggiungibile: si ritenta al
          // prossimo giro, senza disturbare il cameriere con un errore.
        })
    }, 10000)
    return () => clearInterval(intervallo)
    // sessioneLocale intenzionalmente escluso: dipende solo da .pendente
    // (già in dep array) per avviare/fermare il polling, non deve riavviare
    // l'intervallo ad ogni comanda locale aggiunta o ad ogni giro di polling.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessioneId, sessioneLocale?.pendente])

  const [righeInPreparazione, setRigheInPreparazione] = useState<RigaInPreparazione[]>([])
  const [menuItemId, setMenuItemId] = useState<number | ''>('')
  const [quantita, setQuantita] = useState('1')
  const [numeroPortata, setNumeroPortata] = useState('1')
  const [note, setNote] = useState('')

  const [erroreInvio, setErroreInvio] = useState<string | null>(null)
  const [invioInCorso, setInvioInCorso] = useState(false)

  const [erroreChiusura, setErroreChiusura] = useState<string | null>(null)
  const [chiusuraInCorso, setChiusuraInCorso] = useState(false)

  const [tavoloDaAggregare, setTavoloDaAggregare] = useState<number | ''>('')
  const [erroreAggregazione, setErroreAggregazione] = useState<string | null>(null)
  const [aggregazioneInCorso, setAggregazioneInCorso] = useState(false)

  function ricaricaSessioneLocale() {
    if (sessioneId) setSessioneLocale(leggiSessioneLocale(sessioneId))
  }

  if (!sessioneId) {
    return (
      <div className="messaggio-errore">
        Sessione non trovata. <Link to="/cameriere">Torna ai tavoli</Link>
      </div>
    )
  }

  if (!sessioneLocale) {
    if (recuperoInCorso) {
      return <p>Caricamento sessione…</p>
    }
    return (
      <div className="messaggio-errore">
        {erroreRecupero ?? 'Sessione non trovata su questo dispositivo.'} <Link to="/cameriere">Torna ai tavoli</Link>
      </div>
    )
  }

  function aggiungiRiga() {
    if (menuItemId === '') return
    const voce = menu?.find((v) => v.id === menuItemId)
    if (!voce) return
    setRigheInPreparazione((righe) => [
      ...righe,
      { menuItemId: voce.id, nome: voce.nome, quantita: Number(quantita), numeroPortata: Number(numeroPortata), note: note || undefined },
    ])
    setQuantita('1')
    setNote('')
  }

  function rimuoviRiga(indice: number) {
    setRigheInPreparazione((righe) => righe.filter((_, i) => i !== indice))
  }

  async function inviaComanda() {
    if (righeInPreparazione.length === 0) return
    setErroreInvio(null)
    setInvioInCorso(true)

    const numeriPortata = [...new Set(righeInPreparazione.map((r) => r.numeroPortata))].sort((a, b) => a - b)
    const gruppi: GruppoRequest[] = numeriPortata.map((numPortata) => ({
      numeroPortata: numPortata,
      righe: righeInPreparazione
        .filter((r) => r.numeroPortata === numPortata)
        .map((r) => ({ menuItemId: r.menuItemId, quantita: r.quantita, note: r.note })),
    }))

    const comandaId = crypto.randomUUID()
    const request: SincronizzaComandaRequest = { id: comandaId, sessioneId, gruppi }

    const comandaVista: ComandaVista = {
      id: comandaId,
      pendente: false,
      gruppi: numeriPortata.map((numPortata) => ({
        numeroPortata: numPortata,
        righe: righeInPreparazione
          .filter((r) => r.numeroPortata === numPortata)
          .map(({ menuItemId, nome, quantita, note }) => ({ menuItemId, nome, quantita, note })),
      })),
    }

    try {
      const risposta = await api.post<{ gruppi: ComandaVista['statiGruppi'] }>('/api/comande', request)
      comandaVista.statiGruppi = risposta.gruppi
    } catch (err) {
      if (err instanceof ApiError) {
        setErroreInvio(err.message)
        setInvioInCorso(false)
        return
      }
      accoda({ tipo: 'sincronizza-comanda', payload: request })
      comandaVista.pendente = true
    }

    aggiungiComandaLocale(sessioneId, comandaVista)
    ricaricaSessioneLocale()
    setRigheInPreparazione([])
    setInvioInCorso(false)
  }

  async function chiudiSessione() {
    setErroreChiusura(null)
    setChiusuraInCorso(true)
    try {
      await api.post(`/api/sessioni/${sessioneId}/chiudi`)
      // Il guard sopra ha gia' verificato sessioneLocale all'inizio del render.
      rimuoviSessioneDiTavolo(sessioneLocale!.sessione.tavoloId)
      navigate('/cameriere')
    } catch (err) {
      setErroreChiusura(err instanceof ApiError ? err.message : 'Errore di rete: la chiusura richiede connessione')
    } finally {
      setChiusuraInCorso(false)
    }
  }

  async function aggregaTavolo() {
    if (tavoloDaAggregare === '') return
    setErroreAggregazione(null)
    setAggregazioneInCorso(true)
    try {
      const risposta = await api.post<{ tavoliAggregatiIds: number[] }>(
        `/api/sessioni/${sessioneId}/aggrega-tavolo/${tavoloDaAggregare}`,
      )
      aggiornaTavoliAggregati(sessioneId, risposta.tavoliAggregatiIds)
      ricaricaSessioneLocale()
      setTavoloDaAggregare('')
    } catch (err) {
      setErroreAggregazione(err instanceof ApiError ? err.message : 'Errore di rete: l\'aggregazione richiede connessione')
    } finally {
      setAggregazioneInCorso(false)
    }
  }

  const tavolo = tavoli?.find((t) => t.id === sessioneLocale.sessione.tavoloId)
  const tavoliLiberi = tavoli?.filter((t) => t.stato === 'LIBERO') ?? []
  const menuPerCategoria = raggruppaPerCategoria(menu ?? [])

  return (
    <>
      <p>
        <Link to="/cameriere">← Tavoli</Link>
      </p>
      <h1>
        Tavolo {tavolo?.numero ?? sessioneLocale.sessione.tavoloId} — {sessioneLocale.sessione.numeroCoperti} coperti
        {sessioneLocale.pendente && <span className="badge trattenuto" style={{ marginLeft: '0.6rem' }}>apertura in attesa di sync</span>}
      </h1>

      {sessioneLocale.tavoliAggregatiIds.length > 0 && (
        <div className="messaggio-info">
          Tavoli aggregati: {sessioneLocale.tavoliAggregatiIds.map((id) => tavoli?.find((t) => t.id === id)?.numero ?? id).join(', ')}
        </div>
      )}

      <div className="card">
        <h2>Nuova comanda</h2>
        {erroreInvio && <div className="messaggio-errore">{erroreInvio}</div>}
        <div className="griglia">
          <div className="campo">
            <label htmlFor="voce-menu">Voce di menu</label>
            <select id="voce-menu" value={menuItemId} onChange={(e) => setMenuItemId(e.target.value ? Number(e.target.value) : '')}>
              <option value="">Seleziona…</option>
              {menuPerCategoria.map(([categoria, voci]) => (
                <optgroup key={categoria} label={categoria}>
                  {voci.map((voce) => (
                    <option key={voce.id} value={voce.id}>
                      {voce.nome} (€ {voce.prezzo.toFixed(2)})
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </div>
          <div className="campo">
            <label htmlFor="quantita">Quantità</label>
            <input id="quantita" type="number" min={1} value={quantita} onChange={(e) => setQuantita(e.target.value)} />
          </div>
          <div className="campo">
            <label htmlFor="portata">Portata</label>
            <input id="portata" type="number" min={1} value={numeroPortata} onChange={(e) => setNumeroPortata(e.target.value)} />
          </div>
          <div className="campo">
            <label htmlFor="note">Note</label>
            <input id="note" value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
        </div>
        <button className="pulsante secondario" type="button" onClick={aggiungiRiga} disabled={menuItemId === ''}>
          Aggiungi alla comanda
        </button>

        {righeInPreparazione.length > 0 && (
          <>
            <table style={{ marginTop: '1rem' }}>
              <thead>
                <tr>
                  <th>Portata</th>
                  <th>Voce</th>
                  <th>Qtà</th>
                  <th>Note</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {righeInPreparazione.map((riga, indice) => (
                  <tr key={indice}>
                    <td>{riga.numeroPortata}</td>
                    <td>{riga.nome}</td>
                    <td>{riga.quantita}</td>
                    <td>{riga.note ?? '—'}</td>
                    <td>
                      <button className="pulsante secondario piccolo" onClick={() => rimuoviRiga(indice)}>
                        Rimuovi
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <button className="pulsante" style={{ marginTop: '0.75rem' }} onClick={inviaComanda} disabled={invioInCorso}>
              {invioInCorso ? 'Invio…' : 'Invia comanda in cucina'}
            </button>
          </>
        )}
      </div>

      <div className="card">
        <h2>Comande di questa sessione</h2>
        {sessioneLocale.comande.length === 0 && <p>Nessuna comanda inviata ancora.</p>}
        {sessioneLocale.comande.map((comanda) => (
          <div key={comanda.id} className="gruppo-kds">
            {comanda.pendente && <span className="badge trattenuto">in attesa di sync</span>}
            {comanda.gruppi.map((gruppo) => {
              const stato = comanda.statiGruppi?.find((g) => g.numeroPortata === gruppo.numeroPortata)?.stato
              return (
                <div key={gruppo.numeroPortata} style={{ marginTop: '0.5rem' }}>
                  <strong>Portata {gruppo.numeroPortata}</strong>{' '}
                  {stato && <span className={`badge ${stato.toLowerCase().replace('_', '-')}`}>{stato}</span>}
                  <ul style={{ margin: '0.3rem 0 0', paddingLeft: '1.2rem' }}>
                    {gruppo.righe.map((riga, i) => (
                      <li key={i}>
                        {riga.quantita}× {riga.nome} {riga.note && `(${riga.note})`}
                      </li>
                    ))}
                  </ul>
                </div>
              )
            })}
          </div>
        ))}
      </div>

      <div className="card">
        <h2>Aggrega tavolo</h2>
        {erroreAggregazione && <div className="messaggio-errore">{erroreAggregazione}</div>}
        <div className="elenco-azioni">
          <select value={tavoloDaAggregare} onChange={(e) => setTavoloDaAggregare(e.target.value ? Number(e.target.value) : '')}>
            <option value="">Seleziona un tavolo libero…</option>
            {tavoliLiberi.map((t) => (
              <option key={t.id} value={t.id}>
                {t.numero}
              </option>
            ))}
          </select>
          <button className="pulsante secondario" onClick={aggregaTavolo} disabled={tavoloDaAggregare === '' || aggregazioneInCorso}>
            {aggregazioneInCorso ? 'Aggregazione…' : 'Aggrega'}
          </button>
        </div>
      </div>

      <div className="card">
        <h2>Chiusura sessione</h2>
        {erroreChiusura && <div className="messaggio-errore">{erroreChiusura}</div>}
        <p style={{ color: 'var(--colore-testo-debole)', fontSize: '0.9rem' }}>
          Richiede che tutte le portate inviate siano state servite.
        </p>
        <button className="pulsante pericolo" onClick={chiudiSessione} disabled={chiusuraInCorso}>
          {chiusuraInCorso ? 'Chiusura…' : 'Chiudi sessione e libera il tavolo'}
        </button>
      </div>
    </>
  )
}
