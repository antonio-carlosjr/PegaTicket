import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Calendar, MapPin, PlusCircle, Tag, Ticket } from 'lucide-react'
import {
  meusEventos,
  publicarEvento,
  cancelarEvento,
  encerrarEvento,
  type EventoResumo,
  type StatusEvento,
} from '@/api/events'
import { extractApiError } from '@/api/auth'
import { Card, CardContent, CardFooter } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { Spinner } from '@/components/ui/spinner'
import { toast } from '@/components/ui/toaster'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatarData(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatarPreco(preco: string | null): string {
  if (!preco) return 'Gratuito'
  const n = parseFloat(preco)
  return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

/** R3: vagasDisponiveis null/RASCUNHO → texto amigável, nunca "null". */
function vagasTexto(vagas: number | null, status: StatusEvento): string {
  if (status === 'RASCUNHO') return 'Disponível após publicar'
  if (vagas === null) return '—'
  if (vagas === 0) return 'Esgotado'
  return `${vagas} vaga${vagas !== 1 ? 's' : ''}`
}

function StatusBadge({ status }: { status: StatusEvento }) {
  switch (status) {
    case 'RASCUNHO':
      return <Badge variant="warning">Rascunho</Badge>
    case 'PUBLICADO':
      return <Badge variant="success">Publicado</Badge>
    case 'CANCELADO':
      return <Badge variant="destructive">Cancelado</Badge>
    case 'REALIZADO':
      return <Badge variant="secondary">Realizado</Badge>
  }
}

// ─── Componente ───────────────────────────────────────────────────────────────

export function MeusEventos() {
  const [eventos, setEventos] = useState<EventoResumo[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [emAcao, setEmAcao] = useState<number | null>(null) // id do evento em mutação

  const carregar = useCallback(async () => {
    setLoading(true)
    setErro(null)
    try {
      const resp = await meusEventos({ page: 0, size: 100 })
      setEventos(resp.content)
    } catch (e) {
      const msg = extractApiError(e, 'Nao foi possivel carregar seus eventos.')
      setErro(msg)
      toast.error('Erro ao carregar eventos', { description: msg })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    carregar()
  }, [carregar])

  async function handlePublicar(id: number) {
    setEmAcao(id)
    try {
      await publicarEvento(id)
      toast.success('Evento publicado com sucesso!')
      await carregar()
    } catch (e) {
      const msg = extractApiError(e, 'Nao foi possivel publicar o evento.')
      const isConflito = (e as { response?: { status?: number } })?.response?.status === 409
      if (isConflito) {
        toast.error('Transicao invalida', { description: msg })
      } else {
        toast.error('Erro ao publicar', { description: msg })
      }
    } finally {
      setEmAcao(null)
    }
  }

  async function handleEncerrar(id: number) {
    setEmAcao(id)
    try {
      const resposta = await encerrarEvento(id)
      toast.success('Evento marcado como concluido. O pagamento aos participantes sera processado em instantes.')
      // Atualiza o status do evento diretamente na lista usando a resposta do backend
      setEventos((prev) =>
        prev.map((ev) =>
          ev.id === id ? { ...ev, status: resposta.status } : ev
        )
      )
    } catch (e) {
      const code = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      const isConflito = (e as { response?: { status?: number } })?.response?.status === 409
      if (isConflito) {
        if (code === 'TRANSICAO_INVALIDA' || code === 'EVENTO_JA_REALIZADO') {
          toast.error('Nao e possivel encerrar', {
            description: 'O status atual do evento nao permite encerramento.',
          })
        } else {
          toast.error('Nao e possivel encerrar', {
            description: extractApiError(e, 'Status nao permite esta transicao.'),
          })
        }
      } else {
        toast.error('Erro ao encerrar evento', {
          description: extractApiError(e, 'Nao foi possivel encerrar o evento.'),
        })
      }
    } finally {
      setEmAcao(null)
    }
  }

  async function handleCancelar(id: number, titulo: string) {
    const confirmado = window.confirm(
      `Tem certeza que deseja cancelar o evento "${titulo}"? Esta acao nao pode ser desfeita.`
    )
    if (!confirmado) return

    setEmAcao(id)
    try {
      await cancelarEvento(id)
      toast.success('Evento cancelado.')
      await carregar()
    } catch (e) {
      const msg = extractApiError(e, 'Nao foi possivel cancelar o evento.')
      const isConflito = (e as { response?: { status?: number } })?.response?.status === 409
      if (isConflito) {
        toast.error('Transicao invalida', { description: msg })
      } else {
        toast.error('Erro ao cancelar', { description: msg })
      }
    } finally {
      setEmAcao(null)
    }
  }

  return (
    <div className="space-y-6">
      {/* Cabeçalho */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Meus eventos</h1>
          <p className="mt-1 text-muted-foreground">Gerencie seus eventos criados.</p>
        </div>
        <Link
          to="/eventos/novo"
          className={cn(buttonVariants({ variant: 'default' }))}
        >
          <PlusCircle className="h-4 w-4" />
          Criar evento
        </Link>
      </div>

      {/* Loading */}
      {loading && (
        <div className="flex items-center justify-center py-16">
          <Spinner className="h-8 w-8" />
        </div>
      )}

      {/* Erro */}
      {!loading && erro && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 p-6 text-center">
          <p className="font-medium text-destructive">{erro}</p>
          <Button variant="outline" className="mt-4" onClick={carregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {/* Vazio */}
      {!loading && !erro && eventos.length === 0 && (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed py-20 text-center">
          <Ticket className="h-12 w-12 text-muted-foreground/40" />
          <p className="mt-4 text-lg font-medium text-muted-foreground">
            Voce ainda nao criou nenhum evento
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            Crie seu primeiro evento e comece a vender ingressos.
          </p>
          <Link
            to="/eventos/novo"
            className={cn(buttonVariants({ variant: 'default' }), 'mt-6')}
          >
            <PlusCircle className="h-4 w-4" />
            Criar evento
          </Link>
        </div>
      )}

      {/* Lista */}
      {!loading && !erro && eventos.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {eventos.map((ev) => (
            <EventoCard
              key={ev.id}
              evento={ev}
              emAcao={emAcao === ev.id}
              onPublicar={handlePublicar}
              onEncerrar={handleEncerrar}
              onCancelar={handleCancelar}
            />
          ))}
        </div>
      )}
    </div>
  )
}

// ─── Card individual ──────────────────────────────────────────────────────────

function EventoCard({
  evento,
  emAcao,
  onPublicar,
  onEncerrar,
  onCancelar,
}: {
  evento: EventoResumo
  emAcao: boolean
  onPublicar: (id: number) => Promise<void>
  onEncerrar: (id: number) => Promise<void>
  onCancelar: (id: number, titulo: string) => Promise<void>
}) {
  const podePublicar = evento.status === 'RASCUNHO'
  const podeEncerrar = evento.status === 'PUBLICADO'
  const podeCancelar =
    evento.status === 'RASCUNHO' || evento.status === 'PUBLICADO'

  /** R1: Editar só aparece para RASCUNHO. */
  const podeEditar = evento.status === 'RASCUNHO'

  return (
    <Card className="flex flex-col overflow-hidden">
      {/* Imagem */}
      {evento.imagemUrl && (
        <div className="h-36 overflow-hidden bg-muted">
          <img
            src={evento.imagemUrl}
            alt=""
            aria-hidden="true"
            className="h-full w-full object-cover"
          />
        </div>
      )}

      <CardContent className="flex flex-1 flex-col gap-3 p-4">
        {/* Status + tipo */}
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={evento.status} />
          <Badge variant={evento.tipo === 'GRATUITO' ? 'success' : 'default'} className="text-[10px]">
            {evento.tipo === 'GRATUITO' ? 'Gratuito' : 'Pago'}
          </Badge>
        </div>

        {/* Título */}
        <h2 className="font-semibold leading-snug text-foreground line-clamp-2">
          {evento.titulo}
        </h2>

        {/* Meta */}
        <div className="space-y-1.5 text-sm text-muted-foreground">
          <div className="flex items-center gap-1.5">
            <Calendar className="h-3.5 w-3.5 flex-shrink-0" />
            <span>{formatarData(evento.dataInicio)}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <MapPin className="h-3.5 w-3.5 flex-shrink-0" />
            <span className="line-clamp-1">{evento.local}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <Tag className="h-3.5 w-3.5 flex-shrink-0" />
            <span>{formatarPreco(evento.preco)}</span>
          </div>
          {/* R3: vagas com texto amigável — RASCUNHO não tem vagas ainda */}
          <div className="flex items-center gap-1.5">
            <Ticket className="h-3.5 w-3.5 flex-shrink-0" />
            <span>{vagasTexto(null, evento.status)}</span>
          </div>
        </div>
      </CardContent>

      <CardFooter className="flex-col gap-2 border-t p-4">
        <div className="flex w-full gap-2">
          {/* Ver detalhe */}
          <Link
            to={`/eventos/${evento.id}`}
            className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'flex-1')}
          >
            Ver
          </Link>

          {/* R1: Editar só para RASCUNHO */}
          {podeEditar && (
            <Link
              to={`/eventos/${evento.id}/editar`}
              className={cn(buttonVariants({ variant: 'secondary', size: 'sm' }), 'flex-1')}
            >
              Editar
            </Link>
          )}
        </div>

        {/* Ações de transição */}
        <div className="flex w-full gap-2">
          {podePublicar && (
            <Button
              size="sm"
              className="flex-1"
              loading={emAcao}
              onClick={() => onPublicar(evento.id)}
            >
              Publicar
            </Button>
          )}
          {podeEncerrar && (
            <Button
              variant="secondary"
              size="sm"
              className="flex-1"
              loading={emAcao}
              onClick={() => onEncerrar(evento.id)}
            >
              Encerrar
            </Button>
          )}
          {podeCancelar && (
            <Button
              variant="destructive"
              size="sm"
              className="flex-1"
              loading={emAcao}
              onClick={() => onCancelar(evento.id, evento.titulo)}
            >
              Cancelar
            </Button>
          )}
        </div>
      </CardFooter>
    </Card>
  )
}
