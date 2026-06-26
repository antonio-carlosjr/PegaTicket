import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  Calendar,
  Clock,
  Image as ImageIcon,
  MapPin,
  Tag,
  Users,
} from 'lucide-react'
import { detalheEvento, type EventoResponse } from '@/api/events'
import { extractApiError } from '@/api/auth'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { PageLoader } from '@/components/ui/spinner'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatarDataHora(iso: string): string {
  // Exibe no fuso do navegador do usuário
  return new Date(iso).toLocaleString('pt-BR', {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short',
  })
}

function formatarPreco(preco: string | null): string {
  if (!preco) return 'Gratuito'
  const n = parseFloat(preco)
  return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function badgeStatus(status: EventoResponse['status']) {
  switch (status) {
    case 'PUBLICADO': return <Badge variant="success">Publicado</Badge>
    case 'RASCUNHO': return <Badge variant="warning">Rascunho</Badge>
    case 'CANCELADO': return <Badge variant="destructive">Cancelado</Badge>
    case 'REALIZADO': return <Badge variant="secondary">Realizado</Badge>
  }
}

function vagasTexto(vagas: number | null, status: EventoResponse['status']): string {
  if (status === 'RASCUNHO') return 'Disponível após publicar'
  if (vagas === null) return '—'
  if (vagas === 0) return 'Esgotado'
  return `${vagas} vaga${vagas !== 1 ? 's' : ''} disponível`
}

// ─── Componente ───────────────────────────────────────────────────────────────

export function EventoDetalhe() {
  const { id } = useParams<{ id: string }>()
  const [evento, setEvento] = useState<EventoResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return

    let cancelled = false
    setLoading(true)
    setErro(null)

    detalheEvento(Number(id))
      .then((data) => {
        if (!cancelled) setEvento(data)
      })
      .catch((e) => {
        if (cancelled) return
        const status = (e as { response?: { status?: number } })?.response?.status
        if (status === 404) {
          setErro('Evento nao encontrado ou nao disponivel.')
        } else {
          setErro(extractApiError(e, 'Nao foi possivel carregar o evento.'))
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [id])

  if (loading) return <PageLoader label="Carregando evento..." />

  if (erro) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <p className="text-lg font-medium text-destructive">{erro}</p>
        <Link
          to="/eventos"
          className={cn(buttonVariants({ variant: 'outline' }), 'mt-6')}
        >
          <ArrowLeft className="h-4 w-4" />
          Voltar para eventos
        </Link>
      </div>
    )
  }

  if (!evento) return null

  const ehCancelado = evento.status === 'CANCELADO'

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      {/* Botão voltar */}
      <Link
        to="/eventos"
        className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }), '-ml-2')}
      >
        <ArrowLeft className="h-4 w-4" />
        Voltar
      </Link>

      {/* Imagem do evento */}
      {evento.imagemUrl && (
        <div className="overflow-hidden rounded-xl border bg-muted">
          <img
            src={evento.imagemUrl}
            alt={`Imagem do evento ${evento.titulo}`}
            className="h-64 w-full object-cover"
          />
        </div>
      )}

      {/* Banner de cancelado */}
      {ehCancelado && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-center">
          <p className="font-medium text-destructive">Este evento foi cancelado.</p>
        </div>
      )}

      {/* Cabeçalho */}
      <div className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          {badgeStatus(evento.status)}
          <Badge variant={evento.tipo === 'GRATUITO' ? 'success' : 'default'}>
            {evento.tipo === 'GRATUITO' ? 'Gratuito' : 'Pago'}
          </Badge>
        </div>
        <h1 className="text-3xl font-bold tracking-tight">{evento.titulo}</h1>
      </div>

      {/* Card de informações */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Informações</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <InfoItem
            icon={<Calendar className="h-4 w-4" />}
            label="Início"
            value={formatarDataHora(evento.dataInicio)}
          />
          <InfoItem
            icon={<Clock className="h-4 w-4" />}
            label="Término"
            value={formatarDataHora(evento.dataFim)}
          />
          <InfoItem
            icon={<MapPin className="h-4 w-4" />}
            label="Local"
            value={evento.local}
          />
          <InfoItem
            icon={<Tag className="h-4 w-4" />}
            label="Preço"
            value={formatarPreco(evento.preco)}
          />
          <InfoItem
            icon={<Users className="h-4 w-4" />}
            label="Capacidade"
            value={`${evento.capacidade} pessoa${evento.capacidade !== 1 ? 's' : ''}`}
          />
          <InfoItem
            icon={<Users className="h-4 w-4" />}
            label="Vagas"
            value={vagasTexto(evento.vagasDisponiveis, evento.status)}
          />
          {evento.tipo === 'PAGO' && evento.prazoReembolsoDias != null && (
            <InfoItem
              icon={<Clock className="h-4 w-4" />}
              label="Prazo de reembolso"
              value={`${evento.prazoReembolsoDias} dia${evento.prazoReembolsoDias !== 1 ? 's' : ''} antes do evento`}
              className="sm:col-span-2"
            />
          )}
        </CardContent>
      </Card>

      {/* Descrição */}
      {evento.descricao && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Sobre o evento</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="whitespace-pre-line text-sm leading-relaxed text-foreground">
              {evento.descricao}
            </p>
          </CardContent>
        </Card>
      )}

      {/* Imagem URL alternativa quando nao renderizada acima */}
      {!evento.imagemUrl && (
        <Card className="border-dashed">
          <CardContent className="flex items-center gap-3 p-4 text-muted-foreground">
            <ImageIcon className="h-5 w-5 flex-shrink-0" />
            <span className="text-sm">Nenhuma imagem cadastrada para este evento.</span>
          </CardContent>
        </Card>
      )}

      {/* Nota Sprint 3 */}
      <p className="text-center text-xs text-muted-foreground">
        Inscrições disponíveis em breve — Sprint 3.
      </p>
    </div>
  )
}

function InfoItem({
  icon,
  label,
  value,
  className,
}: {
  icon: React.ReactNode
  label: string
  value: string
  className?: string
}) {
  return (
    <div className={`flex gap-3 ${className ?? ''}`}>
      <span className="mt-0.5 flex-shrink-0 text-primary">{icon}</span>
      <div className="min-w-0">
        <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
          {label}
        </p>
        <p className="mt-0.5 text-sm text-foreground">{value}</p>
      </div>
    </div>
  )
}
