import { useState, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { register } from '../api/auth'

export function Register() {
  const navigate = useNavigate()
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [ok, setOk] = useState(false)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setErr(null)
    setLoading(true)
    try {
      await register(nome, email, senha)
      setOk(true)
      setTimeout(() => navigate('/login'), 1500)
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      setErr(msg ?? 'Falha no cadastro')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container">
      <h2>Cadastro</h2>
      {ok && <div className="success">Cadastro feito! Redirecionando para login...</div>}
      <form onSubmit={handleSubmit}>
        <input
          type="text"
          placeholder="nome completo"
          value={nome}
          onChange={(e) => setNome(e.target.value)}
          required
          minLength={2}
        />
        <input
          type="email"
          placeholder="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <input
          type="password"
          placeholder="senha (min 6 chars)"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          required
          minLength={6}
        />
        {err && <div className="error">{err}</div>}
        <button type="submit" disabled={loading || ok}>
          {loading ? 'Cadastrando...' : 'Cadastrar'}
        </button>
      </form>
    </div>
  )
}
