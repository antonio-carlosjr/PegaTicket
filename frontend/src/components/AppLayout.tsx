import { Link, Outlet } from 'react-router-dom'
import { LogOut, PlusCircle, Ticket, User2 } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Logo } from '@/components/Logo'

export function AppLayout() {
  const { user, signOut } = useAuth()

  const ehPromotor = user?.papel === 'PROMOTOR'

  return (
    <div className="flex min-h-screen flex-col bg-background">
      <header className="sticky top-0 z-40 border-b border-border bg-card/80 backdrop-blur-xl">
        <div className="container flex h-16 items-center justify-between">
          <Link to="/" className="inline-flex">
            <Logo size="md" />
          </Link>

          <nav className="flex items-center gap-3">
            {/* ── Eventos (todos os autenticados) ── */}
            <Link
              to="/eventos"
              className="text-sm font-medium text-foreground hover:text-primary"
            >
              Eventos
            </Link>

            {/* ── Meus ingressos (todos os autenticados) ── */}
            <Link
              to="/meus-ingressos"
              className="inline-flex items-center gap-1.5 text-sm font-medium text-foreground hover:text-primary"
            >
              <Ticket className="h-4 w-4" />
              <span className="hidden sm:inline">Meus ingressos</span>
            </Link>

            {/* ── Itens exclusivos do PROMOTOR ── */}
            {ehPromotor && (
              <>
                <Link
                  to="/meus-eventos"
                  className="text-sm font-medium text-foreground hover:text-primary"
                >
                  Meus eventos
                </Link>
                <Link
                  to="/eventos/novo"
                  className="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
                  aria-label="Criar evento"
                >
                  <PlusCircle className="h-4 w-4" />
                  <span className="hidden sm:inline">Criar evento</span>
                </Link>
              </>
            )}

            {/* ── Admin ── */}
            {user?.papel === 'ADMIN' && (
              <Link
                to="/admin/usuarios"
                className="text-sm font-medium text-primary hover:underline"
              >
                Admin
              </Link>
            )}

            {/* ── Meu perfil (clicavel) ── */}
            {user && (
              <Link
                to="/perfil"
                className="flex items-center gap-2 rounded-md px-1.5 py-1 transition-colors hover:bg-muted"
                aria-label="Meu perfil"
                title="Meu perfil"
              >
                <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary">
                  <User2 className="h-4 w-4" />
                </span>
                <div className="hidden text-sm leading-tight sm:block">
                  <p className="font-semibold text-foreground">{user.nome || user.email}</p>
                  <PapelBadge papel={user.papel} verificado={user.verificado} />
                </div>
              </Link>
            )}

            <Button variant="ghost" size="sm" onClick={signOut} aria-label="Sair">
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Sair</span>
            </Button>
          </nav>
        </div>
      </header>

      <main className="flex-1">
        <div className="container py-8">
          <Outlet />
        </div>
      </main>

      <footer className="border-t border-border bg-card/30 py-4 text-center text-xs text-muted-foreground">
        PegaTicket - Projeto academico ESOF II
      </footer>
    </div>
  )
}

function PapelBadge({ papel, verificado }: { papel: string; verificado: boolean }) {
  if (papel === 'ADMIN') {
    return <Badge variant="destructive" className="text-[10px]">Admin</Badge>
  }
  if (papel === 'PROMOTOR') {
    return verificado ? (
      <Badge variant="success" className="text-[10px]">Promotor verificado</Badge>
    ) : (
      <Badge variant="warning" className="text-[10px]">Promotor (pendente)</Badge>
    )
  }
  return <Badge variant="secondary" className="text-[10px]">Participante</Badge>
}
