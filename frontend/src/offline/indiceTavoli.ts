// Indice locale tavolo -> sessione aperta da questo dispositivo. Serve solo
// a ritrovare la sessione dopo aver lasciato la pagina del tavolo: il
// backend non espone una query "sessione aperta per tavolo" (solo apertura,
// chiusura e sync comanda), quindi la lente e' necessariamente per-dispositivo.
const CHIAVE_STORAGE = 'al5.tavolo-sessione'

function leggiMappa(): Record<number, string> {
  try {
    const raw = localStorage.getItem(CHIAVE_STORAGE)
    return raw ? (JSON.parse(raw) as Record<number, string>) : {}
  } catch {
    return {}
  }
}

function scriviMappa(mappa: Record<number, string>) {
  localStorage.setItem(CHIAVE_STORAGE, JSON.stringify(mappa))
}

export function leggiSessioneDiTavolo(tavoloId: number): string | undefined {
  return leggiMappa()[tavoloId]
}

export function registraSessioneDiTavolo(tavoloId: number, sessioneId: string) {
  const mappa = leggiMappa()
  mappa[tavoloId] = sessioneId
  scriviMappa(mappa)
}

export function rimuoviSessioneDiTavolo(tavoloId: number) {
  const mappa = leggiMappa()
  delete mappa[tavoloId]
  scriviMappa(mappa)
}
