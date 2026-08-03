// Coda di sync offline per le uniche due operazioni append-capable del
// backend (vedi docs/06-offline-sync.md): apertura sessione e
// sincronizzazione comanda. Entrambe hanno id generato dal client e sono
// idempotenti sul server, quindi possono essere accodate qui quando la rete
// manca e ritentate in ordine quando torna, senza rischio di duplicati.
import { ApiError, api } from '../api/client'
import type { ApriSessioneRequest, SincronizzaComandaRequest } from '../api/types'

export type OperazioneCoda =
  | { tipo: 'apri-sessione'; payload: ApriSessioneRequest }
  | { tipo: 'sincronizza-comanda'; payload: SincronizzaComandaRequest }

interface VoceCoda {
  id: string
  operazione: OperazioneCoda
}

const CHIAVE_STORAGE = 'al5.coda-offline'

function leggiStorage(): VoceCoda[] {
  try {
    const raw = localStorage.getItem(CHIAVE_STORAGE)
    return raw ? (JSON.parse(raw) as VoceCoda[]) : []
  } catch {
    return []
  }
}

let coda: VoceCoda[] = leggiStorage()
const ascoltatori = new Set<() => void>()

function scriviStorage() {
  localStorage.setItem(CHIAVE_STORAGE, JSON.stringify(coda))
  ascoltatori.forEach((cb) => cb())
}

export function accoda(operazione: OperazioneCoda) {
  coda = [...coda, { id: crypto.randomUUID(), operazione }]
  scriviStorage()
}

export function sottoscriviCoda(cb: () => void): () => void {
  ascoltatori.add(cb)
  return () => ascoltatori.delete(cb)
}

export function leggiCodaCorrente(): VoceCoda[] {
  return coda
}

function eseguiOperazione(operazione: OperazioneCoda): Promise<unknown> {
  return operazione.tipo === 'apri-sessione'
    ? api.post('/api/sessioni', operazione.payload)
    : api.post('/api/comande', operazione.payload)
}

let sincronizzazioneInCorso = false

/**
 * Ritenta le operazioni in coda, in ordine, fermandosi al primo errore di
 * rete (probabile assenza di connettivita': si ritenta al giro successivo).
 * Un errore con risposta dal server (ApiError) significa invece che il
 * server ha effettivamente processato la richiesta: la voce viene scartata
 * per non bloccare la coda all'infinito su un errore non recuperabile.
 */
export async function sincronizzaCoda(): Promise<void> {
  if (sincronizzazioneInCorso) return
  sincronizzazioneInCorso = true
  try {
    while (coda.length > 0) {
      const voce = coda[0]
      try {
        await eseguiOperazione(voce.operazione)
      } catch (errore) {
        if (errore instanceof ApiError) {
          console.warn('Operazione offline scartata dopo risposta di errore dal server', voce, errore)
        } else {
          break
        }
      }
      coda = coda.slice(1)
      scriviStorage()
    }
  } finally {
    sincronizzazioneInCorso = false
  }
}
