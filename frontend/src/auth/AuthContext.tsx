import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, configureApiClient } from '../api/client'
import type { LoginResponse, Ruolo } from '../api/types'

const STORAGE_KEY = 'al5.auth'

interface AuthState {
  token: string
  username: string
  ruolo: Ruolo
}

interface AuthContextValue {
  auth: AuthState | null
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

function leggiStorage(): AuthState | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthState
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState | null>(() => leggiStorage())

  // Il client API e' un modulo condiviso al di fuori di React: sincrono nel
  // corpo del render, NON in un useEffect. React chiama sempre la funzione
  // componente di un genitore prima di montare i figli, ma esegue gli
  // effect in ordine inverso (figli prima degli antenati): se questa
  // configurazione fosse in un useEffect, un componente figlio che monta
  // insieme all'AuthProvider (tipicamente subito dopo il login, quando
  // cambia rotta) vedrebbe il proprio effect di fetch iniziale partire
  // PRIMA che il token fosse stato agganciato al client — richiesta senza
  // Authorization, 401/403 anche con login corretto. L'assegnazione qui è
  // pura e idempotente, quindi è sicura anche se il render viene ripetuto.
  configureApiClient({
    getToken: () => auth?.token ?? null,
    onUnauthorized: () => setAuth(null),
  })

  useEffect(() => {
    if (auth) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(auth))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [auth])

  const value = useMemo<AuthContextValue>(
    () => ({
      auth,
      async login(username: string, password: string) {
        const risposta = await api.post<LoginResponse>('/api/auth/login', { username, password })
        setAuth({ token: risposta.token, username: risposta.username, ruolo: risposta.ruolo })
      },
      logout() {
        // Best-effort: revoca il token lato server, ma non blocca il
        // logout locale se il dispositivo e' offline.
        api.post('/api/auth/logout').catch(() => {})
        setAuth(null)
      },
    }),
    [auth],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth deve essere usato dentro AuthProvider')
  }
  return ctx
}
