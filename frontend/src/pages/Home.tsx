import { useEffect, useState } from 'react'
import {
  Activity,
  Calendar,
  CheckCircle2,
  Clock,
  Mail,
  PlusCircle,
  Ticket,
  TrendingUp,
  XCircle,
} from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/hooks/useAuth'
import { gatewayHealth } from '@/api/auth'

type HealthState = 'verificando' | 'UP' | 'DOWN'

export function Home() {
  const { user } = useAuth()
  const [health, setHealth] = useState<HealthState>('verificando')

  useEffect(() => {
    // Status da plataforma e visivel apenas para ADMIN; so consulta health nesse caso.
    if (user?.papel !== 'ADMIN') return
    gatewayHealth()
      .then((d) => setHealth(d.status === 'UP' ? 'UP' : 'DOWN'))
      .catch(() => setHealth('DOWN'))
  }, [user])

  if (!user) return null

  const ehPromotor = user.papel === 'PROMOTOR'
  const ehAdmin = user.papel === 'ADMIN'
  const promotorPendente = ehPromotor && !user.verificado

  return (
    <div className="space-y-8">
      {/* Hero / Welcome */}
      <section className="relative overflow-hidden rounded-2xl border bg-gradient-to-br from-primary via-primary to-blue-700 p-8 text-primary-foreground shadow-md">
        <div
          aria-hidden="true"
          className="absolute inset-0 opacity-20"
          style={{
            backgroundImage:
              'radial-gradient(circle at 80% 20%, rgba(255,255,255,0.3) 0%, transparent 40%)',
          }}
        />
        <div className="relative">
          <p className="text-sm font-medium uppercase tracking-wider text-white/70">
            Bem-vindo(a) de volta
          </p>
          <h1 className="mt-1 text-3xl font-bold tracking-tight">
            {user.nome || user.email.split('@')[0]}
          </h1>
          <p className="mt-2 max-w-xl text-white/85">
            {ehPromotor
              ? promotorPendente
                ? 'Seu cadastro de promotor esta em analise. Voce sera notificado por e-mail quando for aprovado.'
                : 'Gerencie seus eventos, vendas e relatorios em um so lugar.'
              : 'Descubra eventos, acompanhe seus ingressos e prepare-se para a proxima experiencia.'}
          </p>

          <div className="mt-6 flex flex-wrap gap-3">
            {ehPromotor && !promotorPendente && (
              <Button variant="secondary" size="lg">
                <PlusCircle className="h-4 w-4" />
                Criar evento
              </Button>
            )}
            <Button
              variant="outline"
              size="lg"
              className="border-white/30 bg-white/10 text-white hover:bg-white/20 hover:text-white"
            >
              <Calendar className="h-4 w-4" />
              Explorar eventos
            </Button>
          </div>
        </div>
      </section>

      {/* Status banner para promotor pendente */}
      {promotorPendente && (
        <div className="flex items-start gap-3 rounded-lg border border-warning/40 bg-warning/10 p-4">
          <Clock className="mt-0.5 h-5 w-5 flex-shrink-0 text-warning" />
          <div className="text-sm">
            <p className="font-medium text-foreground">Cadastro de promotor em analise</p>
            <p className="mt-1 text-muted-foreground">
              Voce ainda nao pode criar eventos. A aprovacao costuma sair em ate
              2 dias uteis. Voce sera avisado por e-mail.
            </p>
          </div>
        </div>
      )}

      {/* Cards de metricas */}
      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <MetricCard
          icon={<Ticket className="h-5 w-5" />}
          label={ehPromotor ? 'Ingressos vendidos' : 'Meus ingressos'}
          value="0"
          hint="Atualizado em tempo real"
        />
        <MetricCard
          icon={<Calendar className="h-5 w-5" />}
          label={ehPromotor ? 'Eventos ativos' : 'Eventos inscritos'}
          value="0"
          hint="Nenhum evento ainda"
        />
        <MetricCard
          icon={<TrendingUp className="h-5 w-5" />}
          label={ehPromotor ? 'Receita projetada' : 'Avaliacoes feitas'}
          value={ehPromotor ? 'R$ 0,00' : '0'}
          hint="Em construcao - Sprint 1"
        />
      </section>

      {/* Status da infra - apenas ADMIN */}
      {ehAdmin && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <Activity className="h-5 w-5 text-primary" />
              Status da plataforma
            </CardTitle>
            <CardDescription>Saude dos servicos backend em tempo real.</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center justify-between rounded-md border bg-card p-4">
              <div className="flex items-center gap-3">
                {health === 'UP' && <CheckCircle2 className="h-5 w-5 text-success" />}
                {health === 'DOWN' && <XCircle className="h-5 w-5 text-destructive" />}
                {health === 'verificando' && <Clock className="h-5 w-5 animate-pulse text-muted-foreground" />}
                <div>
                  <p className="font-medium">API Gateway</p>
                  <p className="text-xs text-muted-foreground">
                    {health === 'UP' && 'Todos os servicos operacionais.'}
                    {health === 'DOWN' && 'Servico indisponivel.'}
                    {health === 'verificando' && 'Verificando saude...'}
                  </p>
                </div>
              </div>
              <Badge
                variant={health === 'UP' ? 'success' : health === 'DOWN' ? 'destructive' : 'secondary'}
              >
                {health}
              </Badge>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Identidade da conta */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Sua conta</CardTitle>
          <CardDescription>Informacoes do seu perfil.</CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <Field label="Nome" value={user.nome || '-'} />
            <Field
              label="E-mail"
              value={
                <span className="inline-flex items-center gap-1.5">
                  <Mail className="h-3.5 w-3.5 text-muted-foreground" />
                  {user.email}
                </span>
              }
            />
            <Field
              label="Papel"
              value={
                user.papel === 'PROMOTOR' ? (
                  user.verificado ? (
                    <Badge variant="success">Promotor verificado</Badge>
                  ) : (
                    <Badge variant="warning">Promotor (pendente)</Badge>
                  )
                ) : user.papel === 'ADMIN' ? (
                  <Badge variant="destructive">Admin</Badge>
                ) : (
                  <Badge variant="secondary">Participante</Badge>
                )
              }
            />
            <Field
              label="Membro desde"
              value={user.criadoEm ? new Date(user.criadoEm).toLocaleDateString('pt-BR') : '-'}
            />
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}

function MetricCard({
  icon,
  label,
  value,
  hint,
}: {
  icon: React.ReactNode
  label: string
  value: string
  hint?: string
}) {
  return (
    <Card>
      <CardContent className="flex items-start justify-between p-6">
        <div>
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          <p className="mt-1 text-3xl font-bold tracking-tight">{value}</p>
          {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
        </div>
        <span className="inline-flex h-10 w-10 items-center justify-center rounded-md bg-primary/10 text-primary">
          {icon}
        </span>
      </CardContent>
    </Card>
  )
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
        {label}
      </dt>
      <dd className="mt-1 text-foreground">{value}</dd>
    </div>
  )
}
