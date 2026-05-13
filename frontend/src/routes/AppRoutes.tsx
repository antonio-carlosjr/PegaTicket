import { Routes, Route, Link, Navigate } from 'react-router-dom'
import { Home } from '../pages/Home'
import { Login } from '../pages/Login'
import { Register } from '../pages/Register'
import { useAuth } from '../hooks/useAuth'

export function AppRoutes() {
  const { token, logout } = useAuth()

  return (
    <>
      <nav>
        <Link to="/">Home</Link>
        {!token && <Link to="/login">Login</Link>}
        {!token && <Link to="/register">Cadastro</Link>}
        <span className="spacer" />
        {token && (
          <button className="secondary" onClick={logout}>Sair</button>
        )}
      </nav>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={token ? <Navigate to="/" replace /> : <Login />} />
        <Route path="/register" element={token ? <Navigate to="/" replace /> : <Register />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  )
}
