import { type FormEvent, useState } from 'react'
import { api, ApiError } from '../../api/client'
import { useApiCall } from '../../api/useApiCall'
import type { CreaMenuItemRequest, MenuItemResponse } from '../../api/types'

export function AdminMenuPage() {
  const { dati: voci, errore, inCorso, ricarica } = useApiCall(() =>
    api.get<MenuItemResponse[]>('/api/menu?tutti=true'),
  )

  const [nome, setNome] = useState('')
  const [descrizione, setDescrizione] = useState('')
  const [prezzo, setPrezzo] = useState('')
  const [categoria, setCategoria] = useState('')
  const [inviaInCucina, setInviaInCucina] = useState(true)
  const [erroreForm, setErroreForm] = useState<string | null>(null)
  const [invioInCorso, setInvioInCorso] = useState(false)

  async function creaVoce(e: FormEvent) {
    e.preventDefault()
    setErroreForm(null)
    setInvioInCorso(true)
    try {
      const request: CreaMenuItemRequest = {
        nome,
        descrizione: descrizione || undefined,
        prezzo: Number(prezzo),
        categoria: categoria || undefined,
        inviaInCucina,
      }
      await api.post('/api/admin/menu', request)
      setNome('')
      setDescrizione('')
      setPrezzo('')
      setCategoria('')
      setInviaInCucina(true)
      ricarica()
    } catch (err) {
      setErroreForm(err instanceof ApiError ? err.message : 'Errore di rete')
    } finally {
      setInvioInCorso(false)
    }
  }

  async function cambiaDisponibilita(voce: MenuItemResponse) {
    setErroreForm(null)
    try {
      const azione = voce.disponibile ? 'disattiva' : 'attiva'
      await api.post(`/api/admin/menu/${voce.id}/${azione}`)
      ricarica()
    } catch (err) {
      setErroreForm(err instanceof ApiError ? err.message : 'Errore di rete')
    }
  }

  return (
    <>
      <h1>Menu</h1>
      {erroreForm && <div className="messaggio-errore">{erroreForm}</div>}

      <div className="card">
        <h2>Nuova voce</h2>
        <form onSubmit={creaVoce}>
          <div className="griglia">
            <div className="campo">
              <label htmlFor="menu-nome">Nome</label>
              <input id="menu-nome" value={nome} onChange={(e) => setNome(e.target.value)} required />
            </div>
            <div className="campo">
              <label htmlFor="menu-categoria">Categoria</label>
              <input id="menu-categoria" value={categoria} onChange={(e) => setCategoria(e.target.value)} />
            </div>
            <div className="campo">
              <label htmlFor="menu-prezzo">Prezzo (€)</label>
              <input
                id="menu-prezzo"
                type="number"
                step="0.01"
                min="0"
                value={prezzo}
                onChange={(e) => setPrezzo(e.target.value)}
                required
              />
            </div>
            <div className="campo">
              <label htmlFor="menu-descrizione">Descrizione</label>
              <input id="menu-descrizione" value={descrizione} onChange={(e) => setDescrizione(e.target.value)} />
            </div>
            <div className="campo">
              <label>
                <input
                  type="checkbox"
                  checked={inviaInCucina}
                  onChange={(e) => setInviaInCucina(e.target.checked)}
                  style={{ marginRight: '0.4rem' }}
                />
                Richiede lavorazione in cucina
              </label>
            </div>
          </div>
          <button className="pulsante" disabled={invioInCorso} type="submit">
            {invioInCorso ? 'Creazione…' : 'Crea voce'}
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Elenco</h2>
        {inCorso && <p>Caricamento…</p>}
        {errore && <div className="messaggio-errore">{errore}</div>}
        {voci && (
          <table>
            <thead>
              <tr>
                <th>Nome</th>
                <th>Categoria</th>
                <th>Prezzo</th>
                <th>Cucina</th>
                <th>Stato</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {voci.map((voce) => (
                <tr key={voce.id}>
                  <td>{voce.nome}</td>
                  <td>{voce.categoria ?? '—'}</td>
                  <td>€ {voce.prezzo.toFixed(2)}</td>
                  <td>{voce.inviaInCucina ? 'sì' : 'no'}</td>
                  <td>
                    <span className={`badge ${voce.disponibile ? 'disponibile' : 'non-disponibile'}`}>
                      {voce.disponibile ? 'disponibile' : 'disattivata'}
                    </span>
                  </td>
                  <td>
                    <button className="pulsante secondario piccolo" onClick={() => cambiaDisponibilita(voce)}>
                      {voce.disponibile ? 'Disattiva' : 'Attiva'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
