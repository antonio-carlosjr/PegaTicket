import { Navigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { PageLoader } from '@/components/ui/spinner'

/**
 * Rota exclusiva para usuários com papel PROMOTOR.
 * Usuários sem autenticação → /login
 * Usuários autenticados com outro papel → /
 */
export function PromotorRoute({ children }: { children: React.ReactNode }) {
  const { user, token, loading } = useAuth()

  if (loading) return <PageLoader />

  if (!token) {
    return <Navigate to="/login" replace />
  }

  if (user?.papel !== 'PROMOTOR') {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
