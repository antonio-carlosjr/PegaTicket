import { type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { CheckCircle2, Shield, Zap } from 'lucide-react'
import { Logo } from './Logo'

interface AuthLayoutProps {
  title: string
  subtitle?: string
  children: ReactNode
  footer?: ReactNode
}

export function AuthLayout({ title, subtitle, children, footer }: AuthLayoutProps) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Lado esquerdo: branding (so em telas grandes) */}
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-gradient-to-br from-primary via-blue-700 to-indigo-900 p-12 text-primary-foreground lg:flex">
        <div
          className="absolute inset-0 opacity-30"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 30%, rgba(255,255,255,0.18) 0%, transparent 40%), radial-gradient(circle at 80% 70%, rgba(255,255,255,0.12) 0%, transparent 40%)',
          }}
          aria-hidden="true"
        />

        <Link to="/" className="relative z-10 inline-flex">
          <Logo size="lg" className="text-white [&_span]:!text-white [&_span:last-child]:!from-white [&_span:last-child]:!to-white/80" />
        </Link>

        <div className="relative z-10 max-w-md">
          <h2 className="text-4xl font-bold leading-tight tracking-tight">
            Eventos sem complicacao,
            <br />
            ingresso seguro de verdade.
          </h2>
          <p className="mt-4 text-base text-white/80">
            Compre, gerencie e faca check-in com QR code unico. Pagamento retido
            em escrow ate o evento acontecer.
          </p>

          <ul className="mt-10 space-y-4 text-sm">
            <Feature icon={<Shield className="h-5 w-5" />} text="Escrow nativo: dinheiro retido ate o dia do evento" />
            <Feature icon={<Zap className="h-5 w-5" />} text="Alta concorrencia na abertura de vendas" />
            <Feature icon={<CheckCircle2 className="h-5 w-5" />} text="Check-in em segundos pelo QR" />
          </ul>
        </div>

        <p className="relative z-10 text-xs text-white/60">
          &copy; 2026 PegaTicket - Projeto academico ESOF II
        </p>
      </aside>

      {/* Lado direito: formulario */}
      <main className="flex flex-col justify-center bg-background p-6 sm:p-12">
        <div className="mx-auto w-full max-w-sm">
          <Link to="/" className="mb-8 inline-flex lg:hidden">
            <Logo size="md" />
          </Link>

          <header className="mb-8">
            <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
            {subtitle && (
              <p className="mt-2 text-sm text-muted-foreground">{subtitle}</p>
            )}
          </header>

          {children}

          {footer && <div className="mt-8 text-sm text-muted-foreground">{footer}</div>}
        </div>
      </main>
    </div>
  )
}

function Feature({ icon, text }: { icon: ReactNode; text: string }) {
  return (
    <li className="flex items-start gap-3">
      <span className="mt-0.5 inline-flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-md bg-white/10">
        {icon}
      </span>
      <span className="text-white/90">{text}</span>
    </li>
  )
}
