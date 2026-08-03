// Client HTTP minimale: aggiunge il Bearer token, normalizza gli errori
// (ApiError con lo stesso "messaggio" restituito da ApiExceptionHandler) e
// notifica un handler globale sui 401 (token scaduto/revocato), cosi' che
// AuthContext possa forzare il logout senza che ogni pagina debba gestirlo.

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

type GetToken = () => string | null
type OnUnauthorized = () => void

let getToken: GetToken = () => null
let onUnauthorized: OnUnauthorized = () => {}

export function configureApiClient(opts: { getToken: GetToken; onUnauthorized: OnUnauthorized }) {
  getToken = opts.getToken
  onUnauthorized = opts.onUnauthorized
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (response.status === 401) {
    onUnauthorized()
  }

  if (!response.ok) {
    let messaggio = `Errore ${response.status}`
    try {
      const data = await response.json()
      if (data && typeof data.messaggio === 'string') {
        messaggio = data.messaggio
      }
    } catch {
      // corpo non JSON: si usa il messaggio generico sopra
    }
    throw new ApiError(response.status, messaggio)
  }

  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return undefined as T
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
}
