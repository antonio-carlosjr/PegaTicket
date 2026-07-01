import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  Calendar,
  Clock,
  Image as ImageIcon,
  MapPin,
  Star,
  Tag,
  Ticket,
  Users,
} from 'lucide-react'
import { detalheEvento, type EventoResponse } from '@/api/events'
import { extractApiError } from '@/api/auth'
import { inscrever, type InscricaoResponse } from '@/api/tickets'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { PageLoader } from '@/components/ui/spinner'
import { toast } from '@/components/ui/toaster'
import { QRCodeSVG } from 'qrcode.react'

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

// ─── Mapeamento de erros da inscrição ─────────────────────────────────────────

function mensagemErroInscricao(err: unknown): string {
  const codigo = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
  if (!codigo) return extractApiError(err, 'Não foi possível realizar a inscrição.')

  if (codigo.startsWith('JA_INSCRITO')) return 'Você já está inscrito neste evento.'
  if (codigo.startsWith('EVENTO_ESGOTADO')) return 'Não há mais vagas disponíveis.'
  if (codigo.startsWith('EVENTO_NAO_PUBLICADO')) return 'Evento não disponível para inscrição.'
  if (codigo.startsWith('EVENTO_PAGO_NAO_SUPORTADO')) return 'Inscrição em eventos pagos ainda não disponível.'
  if (codigo.startsWith('EVENTO_INDISPONIVEL')) return 'Serviço temporariamente indisponível. Tente novamente em instantes.'
  return extractApiError(err, 'Não foi possível realizar a inscrição.')
}

// ─── Componente ───────────────────────────────────────────────────────────────

export function EventoDetalhe() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [evento, setEvento] = useState<EventoResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [inscrevendo, setInscrevendo] = useState(false)
  const [inscricao, setInscricao] = useState<InscricaoResponse | null>(null)

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

  async function handleInscrever() {
    if (!evento) return
    setInscrevendo(true)
    try {
      const resultado = await inscrever(evento.id)
      // Evento PAGO: redireciona para checkout (inscricao PENDENTE_PAGAMENTO)
      if (resultado.status === 'PENDENTE_PAGAMENTO' && resultado.id) {
        navigate(`/checkout/${resultado.id}`)
        return
      }
      // Evento GRATUITO: exibe ingresso no lugar (comportamento S3 intacto)
      setInscricao(resultado)
      toast.success('Inscrição confirmada!', {
        description: 'Seu ingresso foi gerado com sucesso.',
      })
    } catch (e) {
      toast.error('Erro na inscrição', { description: mensagemErroInscricao(e) })
    } finally {
      setInscrevendo(false)
    }
  }

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
  const podeInscrever =
    evento.tipo === 'GRATUITO' &&
    evento.status === 'PUBLICADO' &&
    (evento.vagasDisponiveis === null || evento.vagasDisponiveis > 0)

  const eventoPago = evento.tipo === 'PAGO'

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
        {/* Reputação + avaliação (US-024/025) */}
        <div className="flex flex-wrap items-center gap-3 pt-1">
          <ReputacaoInline reputacao={evento.reputacao} />
          <Link
            to={`/eventos/${evento.id}/avaliar`}
            className={cn(buttonVariants({ variant: 'outline', size: 'sm' }))}
            aria-label="Avaliar evento e ver avaliações"
          >
            <Star className="h-4 w-4" />
            Avaliar evento
          </Link>
        </div>
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

      {/* Ingresso gerado (após inscrição bem-sucedida — apenas GRATUITO; PAGO redireciona) */}
      {inscricao && inscricao.ingresso && (
        <Card className="border-success/40 bg-success/5">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg text-success">
              <Ticket className="h-5 w-5" />
              Inscrição confirmada!
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col items-center gap-4">
            <div className="rounded-lg bg-white p-4 shadow-sm" aria-label="QR code do ingresso">
              <QRCodeSVG
                value={inscricao.ingresso.codigoUnico}
                size={200}
                level="M"
              />
            </div>
            <p className="text-center text-sm text-muted-foreground">
              Apresente este QR code na entrada do evento.
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/meus-ingressos')}
            >
              <Ticket className="h-4 w-4" />
              Ver todos os meus ingressos
            </Button>
          </CardContent>
        </Card>
      )}

      {/* CTA de inscrição */}
      {!inscricao && (
        <div className="flex flex-col items-center gap-3 rounded-xl border bg-card p-6 text-center">
          {podeInscrever && (
            <>
              <p className="text-sm text-muted-foreground">
                Evento gratuito com{' '}
                <strong>
                  {evento.vagasDisponiveis !== null
                    ? `${evento.vagasDisponiveis} vaga${evento.vagasDisponiveis !== 1 ? 's' : ''}`
                    : 'vagas'}
                </strong>{' '}
                {evento.vagasDisponiveis === 1 ? 'disponível' : 'disponíveis'}.
              </p>
              <Button
                size="lg"
                onClick={handleInscrever}
                disabled={inscrevendo}
                aria-label="Inscrever-se neste evento"
              >
                <Ticket className="h-4 w-4" />
                {inscrevendo ? 'Inscrevendo...' : 'Inscrever-se'}
              </Button>
            </>
          )}

          {eventoPago && evento.status === 'PUBLICADO' && (evento.vagasDisponiveis === null || evento.vagasDisponiveis > 0) && (
            <>
              <p className="text-sm text-muted-foreground">
                Evento pago —{' '}
                <strong>{formatarPreco(evento.preco)}</strong>.{' '}
                {evento.vagasDisponiveis !== null
                  ? `${evento.vagasDisponiveis} vaga${evento.vagasDisponiveis !== 1 ? 's' : ''} disponível${evento.vagasDisponiveis !== 1 ? 'is' : ''}.`
                  : ''}
              </p>
              <Button
                size="lg"
                onClick={handleInscrever}
                disabled={inscrevendo}
                aria-label="Inscrever-se e pagar neste evento"
              >
                <Ticket className="h-4 w-4" />
                {inscrevendo ? 'Processando...' : 'Inscrever-se e pagar'}
              </Button>
            </>
          )}
          {eventoPago && evento.status === 'PUBLICADO' && evento.vagasDisponiveis === 0 && (
            <p className="font-medium text-destructive">
              Não há mais vagas disponíveis para este evento.
            </p>
          )}

          {!podeInscrever && !eventoPago && evento.status === 'PUBLICADO' && evento.vagasDisponiveis === 0 && (
            <p className="font-medium text-destructive">
              Não há mais vagas disponíveis para este evento.
            </p>
          )}
        </div>
      )}
    </div>
  )
}

/** Reputação compacta no cabeçalho do evento (US-025). Nulo/zero → convite a avaliar. */
function ReputacaoInline({ reputacao }: { reputacao?: { media: number | null; total: number } }) {
  const total = reputacao?.total ?? 0
  if (total === 0) {
    return <span className="text-sm text-muted-foreground">Sem avaliações ainda</span>
  }
  const media = reputacao?.media
  return (
    <span className="inline-flex items-center gap-1.5 text-sm">
      <Star className="h-4 w-4 fill-yellow-400 text-yellow-400" aria-hidden="true" />
      <strong>
        {media != null
          ? media.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
          : '—'}
      </strong>
      <span className="text-muted-foreground">
        ({total} avaliação{total !== 1 ? 'ões' : ''})
      </span>
    </span>
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
