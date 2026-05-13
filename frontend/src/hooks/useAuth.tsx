import { createContext, useContext, useEffect, useState, ReactNode } from 'react'
import { loadToken, storeToken } from '../api/client'

type AuthContextValue = {
  token: string | null
  setToken: (t: string | null) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => loadToken())

  useEffect(() => {
    storeToken(token)
  }, [token])

  return (
    <AuthContext.Provider
      value={{
        token,
        setToken: setTokenState,
        logout: () => setTokenState(null),
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth deve ser usado dentro de AuthProvider')
  return ctx
}
