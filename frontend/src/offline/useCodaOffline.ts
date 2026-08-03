import { useEffect, useSyncExternalStore } from 'react'
import { leggiCodaCorrente, sincronizzaCoda, sottoscriviCoda } from './queue'

const INTERVALLO_RETRY_MS = 8000

/** Espone la coda offline corrente e ne pilota il retry automatico (mount, evento online, polling). */
export function useCodaOffline() {
  const coda = useSyncExternalStore(sottoscriviCoda, leggiCodaCorrente)

  useEffect(() => {
    void sincronizzaCoda()
    const suOnline = () => void sincronizzaCoda()
    window.addEventListener('online', suOnline)
    const intervallo = window.setInterval(() => void sincronizzaCoda(), INTERVALLO_RETRY_MS)
    return () => {
      window.removeEventListener('online', suOnline)
      window.clearInterval(intervallo)
    }
  }, [])

  return coda
}
