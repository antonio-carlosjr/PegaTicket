import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import {
  CalendarCheck,
  CalendarDays,
  ClipboardList,
  LogOut,
  Menu,
  PlusCircle,
  ScanLine,
  ShieldCheck,
  Ticket,
  User2,
  X,
  type LucideIcon,
} from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Logo } from '@/components/Logo'
import { cn } from '@/lib/utils'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

export function AppLayout() {
  const { user, signOut } = useAuth()
  const location = useLocation()
  const [menuAberto, setMenuAberto] = useState(false)

  const ehPromotor = user?.papel === 'PROMOTOR'
  const ehAdmin = user?.papel === 'ADMIN'

  // Fecha o menu mobile ao trocar de rota (belt-and-suspenders com o onClick dos links).
  useEffect(() => {
    setMenuAberto(false)
  }, [location.pathname])

  // Fonte única dos itens — garante ícone consistente em desktop e mobile.
  const itens: NavItem[] = [
    { to: '/eventos', label: 'Eventos', icon: CalendarDays },
    { to: '/meus-ingressos', label: 'Meus ingressos', icon: Ticket },
    { to: '/minhas-inscricoes', label: 'Minhas inscrições', icon: ClipboardList },
    ...(ehPromotor
      ? [
          { to: '/meus-eventos', label: 'Meus eventos', icon: CalendarCheck },
          { to: '/check-in', label: 'Check-in', icon: ScanLine },
        ]
      : []),
    ...(ehAdmin ? [{ to: '/admin/usuarios', label: 'Admin', icon: ShieldCheck }] : []),
  ]

  const linkBase =
    'inline-flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors'
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      linkBase,
      isActive
        ? 'bg-primary/10 text-primary'
        : 'text-foreground hover:bg-muted hover:text-primary'
    )

  return (
    <div className="flex min-h-screen flex-col bg-background">
      <header className="sticky top-0 z-40 border-b border-border bg-card/80 backdrop-blur-xl">
        <div className="container flex h-16 items-center justify-between gap-4">
          <Link to="/" className="inline-flex shrink-0" aria-label="Início">
            <Logo size="md" />
          </Link>

          {/* ── Navegação desktop (lg+) ─────────────────────────────────── */}
          <nav className="hidden items-center gap-1 lg:flex">
            {itens.map((it) => (
              <NavLink key={it.to} to={it.to} className={linkClass} end={it.to === '/eventos'}>
                <it.icon className="h-4 w-4 flex-shrink-0" aria-hidden="true" />
                {it.label}
              </NavLink>
            ))}
            {ehPromotor && (
              <NavLink
                to="/eventos/novo"
                className={({ isActive }) =>
                  cn(
                    'ml-1 inline-flex items-center gap-2 rounded-md px-3 py-2 text-sm font-semibold transition-colors',
                    isActive
                      ? 'bg-primary text-primary-foreground'
                      : 'text-primary hover:bg-primary/10'
                  )
                }
              >
                <PlusCircle className="h-4 w-4 flex-shrink-0" aria-hidden="true" />
                Criar evento
              </NavLink>
            )}
          </nav>

          {/* ── Cluster direito: perfil + sair (desktop) / hamburger (mobile) ── */}
          <div className="flex items-center gap-1">
            {user && (
              <Link
                to="/perfil"
                className="hidden max-w-[220px] items-center gap-2 rounded-md px-1.5 py-1 transition-colors hover:bg-muted lg:flex"
                aria-label="Meu perfil"
                title="Meu perfil"
              >
                <span className="inline-flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                  <User2 className="h-4 w-4" />
                </span>
                <div className="min-w-0 text-sm leading-tight">
                  <p className="truncate font-semibold text-foreground">
                    {user.nome || user.email}
                  </p>
                  <PapelBadge papel={user.papel} verificado={user.verificado} />
                </div>
              </Link>
            )}

            <Button
              variant="ghost"
              size="sm"
              onClick={signOut}
              aria-label="Sair"
              className="hidden lg:inline-flex"
            >
              <LogOut className="h-4 w-4" />
              Sair
            </Button>

            {/* Hamburger — só mobile/tablet */}
            <Button
              variant="ghost"
              size="icon"
              className="lg:hidden"
              onClick={() => setMenuAberto((v) => !v)}
              aria-label={menuAberto ? 'Fechar menu' : 'Abrir menu'}
              aria-expanded={menuAberto}
              aria-controls="menu-mobile"
            >
              {menuAberto ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </Button>
          </div>
        </div>

        {/* ── Drawer mobile ───────────────────────────────────────────── */}
        {menuAberto && (
          <div
            id="menu-mobile"
            className="border-t border-border bg-card shadow-lg lg:hidden"
          >
            <nav className="container flex flex-col gap-1 py-3">
              {user && (
                <Link
                  to="/perfil"
                  onClick={() => setMenuAberto(false)}
                  className="mb-1 flex items-center gap-3 rounded-md px-3 py-2.5 transition-colors hover:bg-muted"
                >
                  <span className="inline-flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                    <User2 className="h-5 w-5" />
                  </span>
                  <div className="min-w-0">
                    <p className="truncate font-semibold text-foreground">
                      {user.nome || user.email}
                    </p>
                    <PapelBadge papel={user.papel} verificado={user.verificado} />
                  </div>
                </Link>
              )}

              {itens.map((it) => (
                <NavLink
                  key={it.to}
                  to={it.to}
                  end={it.to === '/eventos'}
                  onClick={() => setMenuAberto(false)}
                  className={({ isActive }) =>
                    cn(
                      'inline-flex items-center gap-3 rounded-md px-3 py-3 text-base font-medium transition-colors',
                      isActive
                        ? 'bg-primary/10 text-primary'
                        : 'text-foreground hover:bg-muted'
                    )
                  }
                >
                  <it.icon className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
                  {it.label}
                </NavLink>
              ))}

              {ehPromotor && (
                <NavLink
                  to="/eventos/novo"
                  onClick={() => setMenuAberto(false)}
                  className={({ isActive }) =>
                    cn(
                      'mt-1 inline-flex items-center gap-3 rounded-md px-3 py-3 text-base font-semibold transition-colors',
                      isActive
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-primary/10 text-primary hover:bg-primary/20'
                    )
                  }
                >
                  <PlusCircle className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
                  Criar evento
                </NavLink>
              )}

              <button
                type="button"
                onClick={() => {
                  setMenuAberto(false)
                  signOut()
                }}
                className="mt-1 inline-flex w-full items-center gap-3 rounded-md px-3 py-3 text-base font-medium text-destructive transition-colors hover:bg-destructive/10"
              >
                <LogOut className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
                Sair
              </button>
            </nav>
          </div>
        )}
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
