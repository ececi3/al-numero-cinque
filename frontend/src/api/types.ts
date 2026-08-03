// Tipi che rispecchiano i DTO del backend (src/main/java/.../web/dto/*).
// Tenuti manualmente in sync: nessun generatore OpenAPI nel progetto.

export type Ruolo = 'CAMERIERE' | 'CUCINA' | 'ADMIN'

export type StatoTavolo = 'LIBERO' | 'OCCUPATO'
export type StatoSessione = 'APERTA' | 'CHIUSA'
export type StatoGruppo = 'TRATTENUTO' | 'IN_CODA' | 'IN_PREP' | 'PRONTO' | 'SERVITO'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  ruolo: Ruolo
}

export interface CambiaPasswordRequest {
  vecchiaPassword: string
  nuovaPassword: string
}

export interface UtenteResponse {
  id: number
  username: string
  ruolo: Ruolo
  attivo: boolean
}

export interface CreaUtenteRequest {
  username: string
  password: string
  ruolo: Ruolo
}

export interface TavoloResponse {
  id: number
  numero: string
  stato: StatoTavolo
}

export interface CreaTavoloRequest {
  numero: string
}

export interface CategoriaResponse {
  id: number
  nome: string
}

export interface CreaCategoriaRequest {
  nome: string
}

export interface MenuItemResponse {
  id: number
  nome: string
  descrizione: string | null
  prezzo: number
  categoriaId: number | null
  categoria: string | null
  inviaInCucina: boolean
  disponibile: boolean
}

export interface CreaMenuItemRequest {
  nome: string
  descrizione?: string
  prezzo: number
  categoriaId?: number
  inviaInCucina: boolean
}

export interface ApriSessioneRequest {
  id: string
  tavoloId: number
  numeroCoperti: number
}

export interface SessioneResponse {
  id: string
  tavoloId: number
  cameriereId: number
  numeroCoperti: number
  stato: StatoSessione
  apertaAt: string
  tavoliAggregatiIds: number[]
}

export interface RigaRequest {
  menuItemId: number
  quantita: number
  note?: string
}

export interface GruppoRequest {
  numeroPortata: number
  righe: RigaRequest[]
}

export interface SincronizzaComandaRequest {
  id: string
  sessioneId: string
  gruppi: GruppoRequest[]
}

export interface RigaKdsResponse {
  menuItemId: number
  nome: string
  quantita: number
  note?: string
}

export interface GruppoInvioResponse {
  id: number
  comandaId: string
  numeroPortata: number
  stato: StatoGruppo
  seqCoda: number | null
  righe: RigaKdsResponse[]
}

export interface ComandaResponse {
  id: string
  sessioneId: string
  cameriereId: number
  seqServer: number | null
  creataAt: string
  gruppi: GruppoInvioResponse[]
}

export interface ComandaDettaglioResponse {
  id: string
  tavoloNumero: string
  cameriereId: number
  cameriereUsername: string | null
  creataAt: string
  gruppi: GruppoInvioResponse[]
}

export interface SessioneDettaglioResponse {
  id: string
  tavoloId: number
  cameriereId: number
  numeroCoperti: number
  stato: StatoSessione
  apertaAt: string
  tavoliAggregatiIds: number[]
  comande: ComandaResponse[]
}

export interface TempoPreparazionePortata {
  numeroPortata: number
  minutiMediPreparazione: number
  campioni: number
}

export interface AnalyticsResponse {
  numeroSessioni: number
  totaleCoperti: number
  numeroComande: number
  tempiMediPreparazionePerPortata: TempoPreparazionePortata[]
}
