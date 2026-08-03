// Cache locale (localStorage) delle sessioni aperte da questo dispositivo e
// delle comande inviate in ciascuna, cosi' che un refresh della pagina o un
// riavvio dell'app cameriere non perda il "carrello" del tavolo mentre si e'
// offline (le comande in coda non hanno ancora una risposta dal server).
// GruppoInvioResponse non porta le righe: il dettaglio articoli/quantita'
// visualizzato qui e' quello inserito dal cameriere, lo stato del gruppo
// (se la comanda e' stata sincronizzata) viene invece dalla risposta server.
import type { ApriSessioneRequest, ComandaResponse, GruppoInvioResponse, SessioneDettaglioResponse } from '../api/types'

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

/**
 * Riallinea le comande gia' sincronizzate con lo stato del server (stato di
 * ogni gruppo incluso): il cameriere non ha altrimenti alcun modo di vedere
 * che la cucina ha lavorato una portata, dato che comanda.statiGruppi era
 * finora uno snapshot preso una tantum all'invio/idratazione e mai piu'
 * aggiornato (vedi CameriereSessionePage, polling periodico). Le comande
 * ancora pendenti (in coda offline, non ancora sul server) restano intatte.
 */
export function sincronizzaComandeDaServer(sessioneId: string, comandeServer: ComandaResponse[]) {
  const tutte = leggiTutte()
  const esistente = tutte[sessioneId]
  if (!esistente) return

  const comandePendenti = esistente.comande.filter((c) => c.pendente)
  const comandeSincronizzate: ComandaVista[] = comandeServer.map((comanda) => ({
    id: comanda.id,
    pendente: false,
    statiGruppi: comanda.gruppi,
    gruppi: comanda.gruppi.map((gruppo) => ({
      numeroPortata: gruppo.numeroPortata,
      righe: gruppo.righe.map((riga) => ({
        menuItemId: riga.menuItemId,
        nome: riga.nome,
        quantita: riga.quantita,
        note: riga.note,
      })),
    })),
  }))

  esistente.comande = [...comandeSincronizzate, ...comandePendenti]
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

/**
 * Converte lo stato completo di una sessione ricevuto dal server (vedi
 * GET /api/sessioni/{id} e GET /api/sessioni/per-tavolo/{tavoloId}) nel
 * formato usato dalla cache locale, e lo salva: serve a un dispositivo che
 * non ha aperto la sessione (quindi non ha nulla in localStorage) per poterla
 * comunque visualizzare e continuare a operarci.
 */
export function idrataSessioneLocaleDaServer(dettaglio: SessioneDettaglioResponse): SessioneLocale {
  const sessioneLocale: SessioneLocale = {
    sessione: { id: dettaglio.id, tavoloId: dettaglio.tavoloId, numeroCoperti: dettaglio.numeroCoperti },
    pendente: false,
    tavoliAggregatiIds: dettaglio.tavoliAggregatiIds,
    comande: dettaglio.comande.map((comanda) => ({
      id: comanda.id,
      pendente: false,
      statiGruppi: comanda.gruppi,
      gruppi: comanda.gruppi.map((gruppo) => ({
        numeroPortata: gruppo.numeroPortata,
        righe: gruppo.righe.map((riga) => ({
          menuItemId: riga.menuItemId,
          nome: riga.nome,
          quantita: riga.quantita,
          note: riga.note,
        })),
      })),
    })),
  }
  salvaSessioneLocale(dettaglio.id, sessioneLocale)
  return sessioneLocale
}
