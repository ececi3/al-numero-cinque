import { useEffect, useState } from 'react'

/** Stato online/offline del browser, per mostrare la striscia di avviso e decidere se tentare la coda. */
export function useOnlineStatus(): boolean {
  const [online, setOnline] = useState(navigator.onLine)

  useEffect(() => {
    const aggiornaOnline = () => setOnline(true)
    const aggiornaOffline = () => setOnline(false)
    window.addEventListener('online', aggiornaOnline)
    window.addEventListener('offline', aggiornaOffline)
    return () => {
      window.removeEventListener('online', aggiornaOnline)
      window.removeEventListener('offline', aggiornaOffline)
    }
  }, [])

  return online
}
