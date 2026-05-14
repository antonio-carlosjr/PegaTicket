import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { loadToken, storeToken } from '@/api/client'
import { me as fetchMe, type LoginResponse, type Usuario } from '@/api/auth'

type AuthContextValue = {
  token: string | null
  user: Usuario | null
  loading: boolean
  signIn: (login: LoginResponse) => void
  signOut: () => void
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => loadToken())
  const [user, setUser] = useState<Usuario | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    if (!token) {
      setUser(null)
      setLoading(false)
      return
    }
    try {
      const u = await fetchMe()
      setUser(u)
    } catch {
      // Token invalido/expirado: limpa tudo
      storeToken(null)
      setTokenState(null)
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => {
    refresh()
  }, [refresh])

  const signIn = useCallback((login: LoginResponse) => {
    storeToken(login.token)
    setTokenState(login.token)
    setUser({
      id: login.userId,
      nome: '',
      email: login.email,
      papel: login.papel,
      verificado: login.verificado,
      criadoEm: '',
    })
    // Forca buscar o nome completo do perfil
    void refresh()
  }, [refresh])

  const signOut = useCallback(() => {
    storeToken(null)
    setTokenState(null)
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ token, user, loading, signIn, signOut, refresh }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth deve ser usado dentro de AuthProvider')
  return ctx
}
