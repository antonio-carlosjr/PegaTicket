import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { PageLoader } from '@/components/ui/spinner'

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { token, loading } = useAuth()
  const location = useLocation()

  if (loading) return <PageLoader />
  if (!token) {
    return (
      <Navigate
        to={`/login?redirect=${encodeURIComponent(location.pathname)}`}
        replace
      />
    )
  }
  return <>{children}</>
}
