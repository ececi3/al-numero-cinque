import { useCallback, useEffect, useState } from 'react'
import { ApiError } from './client'

/** Boilerplate comune di caricamento dati: stato di errore/caricamento + funzione di ricarica manuale. */
export function useApiCall<T>(caricatore: () => Promise<T>, deps: unknown[] = []) {
  const [dati, setDati] = useState<T | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const [inCorso, setInCorso] = useState(true)

  const ricarica = useCallback(() => {
    setInCorso(true)
    setErrore(null)
    caricatore()
      .then((risultato) => setDati(risultato))
      .catch((e: unknown) => setErrore(e instanceof ApiError ? e.message : 'Errore di rete'))
      .finally(() => setInCorso(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => {
    ricarica()
  }, [ricarica])

  return { dati, errore, inCorso, ricarica }
}
