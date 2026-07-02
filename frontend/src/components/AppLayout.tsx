import { useEffect, useRef, useState } from 'react'
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
import type { Usuario } from '@/api/auth'
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
  const hamburgerRef = useRef<HTMLButtonElement>(null)
  const drawerRef = useRef<HTMLElement>(null)

  const ehPromotor = user?.papel === 'PROMOTOR'
  const ehAdmin = user?.papel === 'ADMIN'

  // Fecha o drawer ao trocar de rota (rede de segurança; onNavigate nos links é o caminho primário).
  useEffect(() => {
    setMenuAberto(false)
  }, [location.pathname])

  // Enquanto o drawer mobile está aberto: trava o scroll do body, fecha no Escape,
  // move o foco para dentro do painel (a11y do aria-modal) e restaura o foco no
  // hamburger ao fechar.
  useEffect(() => {
    if (!menuAberto) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMenuAberto(false)
    window.addEventListener('keydown', onKey)
    drawerRef.current?.querySelector<HTMLElement>('a, button')?.focus()
    return () => {
      document.body.style.overflow = prev
      window.removeEventListener('keydown', onKey)
      hamburgerRef.current?.focus()
    }
  }, [menuAberto])

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

  return (
    <div className="flex min-h-screen bg-background">
      {/* ── Sidebar fixa (desktop, lg+) ──────────────────────────────── */}
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 border-r border-border bg-card lg:flex">
        <SidebarContent user={user} itens={itens} ehPromotor={!!ehPromotor} onSignOut={signOut} />
      </aside>

      {/* ── Drawer mobile (<lg) ──────────────────────────────────────── */}
      {menuAberto && (
        <div className="lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          {/* Backdrop escuro clicável */}
          <button
            type="button"
            aria-label="Fechar menu"
            onClick={() => setMenuAberto(false)}
            className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
          />
          {/* Painel deslizante */}
          <aside
            ref={drawerRef}
            id="sidebar-mobile"
            className="fixed inset-y-0 left-0 z-50 flex w-72 max-w-[85vw] border-r border-border bg-card shadow-xl"
          >
            <SidebarContent
              user={user}
              itens={itens}
              ehPromotor={!!ehPromotor}
              onSignOut={signOut}
              onNavigate={() => setMenuAberto(false)}
            />
          </aside>
        </div>
      )}

      {/* ── Coluna principal ─────────────────────────────────────────── */}
      <div className="flex min-w-0 flex-1 flex-col">
        {/* Topbar mobile: logo + hamburger */}
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border bg-card/80 px-4 backdrop-blur-xl lg:hidden">
          <Link to="/" className="inline-flex shrink-0" aria-label="Início">
            <Logo size="md" />
          </Link>
          <Button
            ref={hamburgerRef}
            variant="ghost"
            size="icon"
            onClick={() => setMenuAberto((v) => !v)}
            aria-label={menuAberto ? 'Fechar menu' : 'Abrir menu'}
            aria-expanded={menuAberto}
            aria-controls="sidebar-mobile"
          >
            {menuAberto ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </Button>
        </header>

        <main className="flex-1">
          {/* Wrapper próprio no lugar de `container`: centraliza DENTRO da coluna,
              não da viewport — evita descentralização causada pela sidebar. */}
          <div className="mx-auto w-full max-w-6xl px-6 py-8">
            <Outlet />
          </div>
        </main>

        <footer className="border-t border-border bg-card/30 py-4 text-center text-xs text-muted-foreground">
          PegaTicket - Projeto academico ESOF II
        </footer>
      </div>
    </div>
  )
}

/* ──────────────────────────────────────────────────────────────────────
 * SidebarContent — reutilizado no <aside> desktop e no drawer mobile.
 * onNavigate: chamado ao clicar qualquer link/ação (fecha o drawer no mobile).
 * ────────────────────────────────────────────────────────────────────── */
function SidebarContent({
  user,
  itens,
  ehPromotor,
  onSignOut,
  onNavigate,
}: {
  user: Usuario | null
  itens: NavItem[]
  ehPromotor: boolean
  onSignOut: () => void
  onNavigate?: () => void
}) {
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      'flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium transition-colors',
      isActive
        ? 'bg-primary/10 text-primary'
        : 'text-foreground hover:bg-muted hover:text-primary'
    )

  return (
    <div className="flex h-full w-full flex-col">
      <div className="flex h-16 shrink-0 items-center border-b border-border px-4">
        <Link to="/" className="inline-flex" aria-label="Início" onClick={onNavigate}>
          <Logo size="md" />
        </Link>
      </div>

      {/* Navegação vertical */}
      <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4" aria-label="Navegação principal">
        {itens.map((it) => (
          <NavLink
            key={it.to}
            to={it.to}
            end={it.to === '/eventos'}
            onClick={onNavigate}
            className={linkClass}
          >
            <it.icon className="h-5 w-5 shrink-0" aria-hidden="true" />
            {it.label}
          </NavLink>
        ))}

        {ehPromotor && (
          <NavLink
            to="/eventos/novo"
            onClick={onNavigate}
            className={({ isActive }) =>
              cn(
                'mt-2 flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-semibold transition-colors',
                isActive
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-primary/10 text-primary hover:bg-primary/20'
              )
            }
          >
            <PlusCircle className="h-5 w-5 shrink-0" aria-hidden="true" />
            Criar evento
          </NavLink>
        )}
      </nav>

      {/* Rodapé: perfil (clicável → /perfil) + Sair */}
      {user && (
        <div className="shrink-0 border-t border-border p-3">
          <Link
            to="/perfil"
            onClick={onNavigate}
            className="mb-1 flex items-center gap-3 rounded-md px-2 py-2 transition-colors hover:bg-muted"
            aria-label="Meu perfil"
          >
            <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
              <User2 className="h-5 w-5" />
            </span>
            <div className="min-w-0 leading-tight">
              <p className="truncate text-sm font-semibold text-foreground">
                {user.nome || user.email}
              </p>
              <PapelBadge papel={user.papel} verificado={user.verificado} />
            </div>
          </Link>

          <button
            type="button"
            onClick={() => {
              onNavigate?.()
              onSignOut()
            }}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium text-destructive transition-colors hover:bg-destructive/10"
          >
            <LogOut className="h-5 w-5 shrink-0" aria-hidden="true" />
            Sair
          </button>
        </div>
      )}
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
