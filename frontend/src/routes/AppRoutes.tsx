import { Navigate, Route, Routes, useParams } from 'react-router-dom'
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
import { MeusIngressos } from '@/pages/MeusIngressos'
import { MinhasInscricoes } from '@/pages/MinhasInscricoes'
import { CheckoutPage } from '@/pages/CheckoutPage'
import { Perfil } from '@/pages/Perfil'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { AppLayout } from '@/components/AppLayout'
import { AdminRoute } from '@/components/AdminRoute'
import { PromotorRoute } from '@/components/PromotorRoute'
import { AdminUsuarios } from '@/pages/AdminUsuarios'
import { CheckinScanner } from '@/pages/CheckinScanner'
import { AvaliacaoEvento } from '@/pages/AvaliacaoEvento'

function GuestOnly({ children }: { children: React.ReactNode }) {
  const { token, loading } = useAuth()
  if (loading) return null
  if (token) return <Navigate to="/" replace />
  return <>{children}</>
}

/** Le o :id da rota e injeta como prop no componente de avaliacao (US-024/025). */
function AvaliarEventoRoute() {
  const { id } = useParams<{ id: string }>()
  return <AvaliacaoEvento eventoId={Number(id)} />
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
        {/* Avaliar evento + reputação (US-024/025) — qualquer autenticado (elegibilidade no back) */}
        <Route path="/eventos/:id/avaliar" element={<AvaliarEventoRoute />} />

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

        {/* Check-in por QR (US-034) — apenas PROMOTOR */}
        <Route
          path="/check-in"
          element={<PromotorRoute><CheckinScanner /></PromotorRoute>}
        />

        {/* Ingressos e inscrições — qualquer autenticado */}
        <Route path="/meus-ingressos" element={<MeusIngressos />} />
        <Route path="/minhas-inscricoes" element={<MinhasInscricoes />} />
        <Route path="/checkout/:inscricaoId" element={<CheckoutPage />} />

        {/* Meu perfil — qualquer autenticado */}
        <Route path="/perfil" element={<Perfil />} />

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
