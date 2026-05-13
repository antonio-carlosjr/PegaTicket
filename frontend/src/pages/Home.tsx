import { useEffect, useState } from 'react'
import { gatewayHealth, me, Usuario } from '../api/auth'
import { useAuth } from '../hooks/useAuth'

type HealthState = 'verificando' | 'UP' | 'DOWN'

export function Home() {
  const { token } = useAuth()
  const [health, setHealth] = useState<HealthState>('verificando')
  const [user, setUser] = useState<Usuario | null>(null)
  const [err, setErr] = useState<string | null>(null)

  useEffect(() => {
    gatewayHealth()
      .then((d) => setHealth(d.status === 'UP' ? 'UP' : 'DOWN'))
      .catch(() => setHealth('DOWN'))
  }, [])

  useEffect(() => {
    if (!token) {
      setUser(null)
      return
    }
    me()
      .then(setUser)
      .catch((e) => setErr(e?.response?.data?.message ?? 'Erro ao carregar perfil'))
  }, [token])

  return (
    <div className="container">
      <h1>Ticketeira</h1>
      <p>
        Backend health:{' '}
        <span className={`badge ${health === 'UP' ? 'up' : 'down'}`}>{health}</span>
      </p>

      {token ? (
        <>
          <h2>Meu perfil</h2>
          {err && <div className="error">{err}</div>}
          {user && (
            <pre>{JSON.stringify(user, null, 2)}</pre>
          )}
        </>
      ) : (
        <p>
          Faca <a href="/login">login</a> ou <a href="/register">cadastre-se</a>.
        </p>
      )}
    </div>
  )
}
