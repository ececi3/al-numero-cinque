// Cache locale (localStorage) delle sessioni aperte da questo dispositivo e
// delle comande inviate in ciascuna, cosi' che un refresh della pagina o un
// riavvio dell'app cameriere non perda il "carrello" del tavolo mentre si e'
// offline (le comande in coda non hanno ancora una risposta dal server).
// GruppoInvioResponse non porta le righe: il dettaglio articoli/quantita'
// visualizzato qui e' quello inserito dal cameriere, lo stato del gruppo
// (se la comanda e' stata sincronizzata) viene invece dalla risposta server.
import type { ApriSessioneRequest, GruppoInvioResponse } from '../api/types'

export interface RigaVista {
  menuItemId: number
  nome: string
  quantita: number
  note?: string
}

export interface GruppoVista {
  numeroPortata: number
  righe: RigaVista[]
}

export interface ComandaVista {
  id: string
  gruppi: GruppoVista[]
  pendente: boolean
  statiGruppi?: GruppoInvioResponse[]
}

export interface SessioneLocale {
  sessione: ApriSessioneRequest
  pendente: boolean
  tavoliAggregatiIds: number[]
  comande: ComandaVista[]
}

const CHIAVE_STORAGE = 'al5.sessioni-locali'

function leggiTutte(): Record<string, SessioneLocale> {
  try {
    const raw = localStorage.getItem(CHIAVE_STORAGE)
    return raw ? (JSON.parse(raw) as Record<string, SessioneLocale>) : {}
  } catch {
    return {}
  }
}

function scriviTutte(tutte: Record<string, SessioneLocale>) {
  localStorage.setItem(CHIAVE_STORAGE, JSON.stringify(tutte))
}

export function leggiSessioneLocale(sessioneId: string): SessioneLocale | undefined {
  return leggiTutte()[sessioneId]
}

export function salvaSessioneLocale(sessioneId: string, dati: SessioneLocale) {
  const tutte = leggiTutte()
  tutte[sessioneId] = dati
  scriviTutte(tutte)
}

export function aggiungiComandaLocale(sessioneId: string, comanda: ComandaVista) {
  const tutte = leggiTutte()
  const esistente = tutte[sessioneId]
  if (!esistente) return
  esistente.comande = [...esistente.comande, comanda]
  scriviTutte(tutte)
}

export function segnaSessioneNonPendente(sessioneId: string) {
  const tutte = leggiTutte()
  const esistente = tutte[sessioneId]
  if (!esistente) return
  esistente.pendente = false
  scriviTutte(tutte)
}

export function aggiornaTavoliAggregati(sessioneId: string, tavoliAggregatiIds: number[]) {
  const tutte = leggiTutte()
  const esistente = tutte[sessioneId]
  if (!esistente) return
  esistente.tavoliAggregatiIds = tavoliAggregatiIds
  scriviTutte(tutte)
}
