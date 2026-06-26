import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { Login } from '@/pages/Login'
import { Register } from '@/pages/Register'
import { ForgotPassword } from '@/pages/ForgotPassword'
import { ResetPassword } from '@/pages/ResetPassword'
import { Home } from '@/pages/Home'
import { Eventos } from '@/pages/Eventos'
import { EventoDetalhe } from '@/pages/EventoDetalhe'
import { MeusEventos } from '@/pages/MeusEventos'
import { CriarEditarEvento } from '@/pages/CriarEditarEvento'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { AppLayout } from '@/components/AppLayout'
import { AdminRoute } from '@/components/AdminRoute'
import { PromotorRoute } from '@/components/PromotorRoute'
import { AdminUsuarios } from '@/pages/AdminUsuarios'

function GuestOnly({ children }: { children: React.ReactNode }) {
  const { token, loading } = useAuth()
  if (loading) return null
  if (token) return <Navigate to="/" replace />
  return <>{children}</>
}

export function AppRoutes() {
  return (
    <Routes>
      {/* ── Rotas públicas (guest) ──────────────────────────────────────── */}
      <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><Register /></GuestOnly>} />
      <Route path="/forgot-password" element={<GuestOnly><ForgotPassword /></GuestOnly>} />
      <Route path="/reset-password" element={<ResetPassword />} />

      {/* ── Rotas protegidas (autenticado) ──────────────────────────────── */}
      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        {/* Home */}
        <Route path="/" element={<Home />} />

        {/* Eventos — qualquer autenticado */}
        <Route path="/eventos" element={<Eventos />} />
        <Route path="/eventos/:id" element={<EventoDetalhe />} />

        {/* Meus eventos + Criar/Editar — apenas PROMOTOR */}
        <Route
          path="/meus-eventos"
          element={<PromotorRoute><MeusEventos /></PromotorRoute>}
        />
        <Route
          path="/eventos/novo"
          element={<PromotorRoute><CriarEditarEvento /></PromotorRoute>}
        />
        <Route
          path="/eventos/:id/editar"
          element={<PromotorRoute><CriarEditarEvento /></PromotorRoute>}
        />

        {/* Admin */}
        <Route
          path="/admin/usuarios"
          element={<AdminRoute><AdminUsuarios /></AdminRoute>}
        />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
