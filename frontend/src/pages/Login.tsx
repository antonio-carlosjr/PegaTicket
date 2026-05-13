import { useState, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import { useAuth } from '../hooks/useAuth'

export function Login() {
  const { setToken } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setErr(null)
    setLoading(true)
    try {
      const r = await login(email, senha)
      setToken(r.token)
      navigate('/')
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      setErr(msg ?? 'Falha no login')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container">
      <h2>Login</h2>
      <form onSubmit={handleSubmit}>
        <input
          type="email"
          placeholder="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <input
          type="password"
          placeholder="senha"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          required
        />
        {err && <div className="error">{err}</div>}
        <button type="submit" disabled={loading}>
          {loading ? 'Entrando...' : 'Entrar'}
        </button>
      </form>
    </div>
  )
}
