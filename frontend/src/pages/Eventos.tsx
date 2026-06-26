import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Calendar, MapPin, Search, SlidersHorizontal, Tag, Ticket } from 'lucide-react'
import { listarEventos, type EventoResumo, type TipoEvento } from '@/api/events'
import { extractApiError } from '@/api/auth'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { toast } from '@/components/ui/toaster'

const PAGE_SIZE = 20

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

/**
 * `<input type="datetime-local">` produz "2026-06-20T14:00" (sem offset).
 * O backend bind para OffsetDateTime exige offset — enviar sem offset resulta em 500.
 * Converte para ISO-8601 com offset no fuso do usuario antes de mandar pra API.
 */
function localParaIso(local: string): string | undefined {
  if (!local) return undefined
  const d = new Date(local)
  if (isNaN(d.getTime())) return undefined
  return d.toISOString()
}

export function Eventos() {
  const [searchParams, setSearchParams] = useSearchParams()

  const [eventos, setEventos] = useState<EventoResumo[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  // ─── filtros lidos da URL ──────────────────────────────────────────────────
  const q = searchParams.get('q') ?? ''
  const tipo = (searchParams.get('tipo') as TipoEvento | '') ?? ''
  const de = searchParams.get('de') ?? ''
  const ate = searchParams.get('ate') ?? ''
  const page = Number(searchParams.get('page') ?? '0')

  // ─── estado local dos inputs (sem disparar busca a cada tecla) ─────────────
  const [qInput, setQInput] = useState(q)
  const [tipoInput, setTipoInput] = useState<TipoEvento | ''>(tipo as TipoEvento | '')
  const [deInput, setDeInput] = useState(de)
  const [ateInput, setAteInput] = useState(ate)

  const buscar = useCallback(async () => {
    setLoading(true)
    setErro(null)
    try {
      const resp = await listarEventos({
        q: q || undefined,
        tipo: (tipo as TipoEvento) || undefined,
        de: localParaIso(de),
        ate: localParaIso(ate),
        page,
        size: PAGE_SIZE,
      })
      setEventos(resp.content)
      setTotalPages(resp.totalPages)
      setTotalElements(resp.totalElements)
    } catch (e) {
      const msg = extractApiError(e, 'Nao foi possivel carregar os eventos.')
      setErro(msg)
      toast.error('Erro ao carregar eventos', { description: msg })
    } finally {
      setLoading(false)
    }
  }, [q, tipo, de, ate, page])

  useEffect(() => {
    buscar()
  }, [buscar])

  function aplicarFiltros(e: React.FormEvent) {
    e.preventDefault()
    const params: Record<string, string> = { page: '0' }
    if (qInput) params.q = qInput
    if (tipoInput) params.tipo = tipoInput
    if (deInput) params.de = deInput
    if (ateInput) params.ate = ateInput
    setSearchParams(params, { replace: true })
  }

  function limparFiltros() {
    setQInput('')
    setTipoInput('')
    setDeInput('')
    setAteInput('')
    setSearchParams({}, { replace: true })
  }

  function irPagina(p: number) {
    const params: Record<string, string> = { page: String(p) }
    if (q) params.q = q
    if (tipo) params.tipo = tipo
    if (de) params.de = de
    if (ate) params.ate = ate
    setSearchParams(params, { replace: true })
  }

  const temFiltro = !!(q || tipo || de || ate)

  return (
    <div className="space-y-6">
      {/* Cabeçalho */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Eventos</h1>
        <p className="mt-1 text-muted-foreground">Explore os eventos disponíveis.</p>
      </div>

      {/* Formulário de filtros */}
      <form onSubmit={aplicarFiltros} className="rounded-xl border bg-card p-4 shadow-sm">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="relative sm:col-span-2 lg:col-span-2">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Buscar por título ou local..."
              value={qInput}
              onChange={(e) => setQInput(e.target.value)}
              className="pl-9"
              aria-label="Buscar eventos"
            />
          </div>

          <div>
            <select
              className="flex h-11 w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              value={tipoInput}
              onChange={(e) => setTipoInput(e.target.value as TipoEvento | '')}
              aria-label="Filtrar por tipo"
            >
              <option value="">Todos os tipos</option>
              <option value="GRATUITO">Gratuito</option>
              <option value="PAGO">Pago</option>
            </select>
          </div>

          <div className="flex gap-2">
            <Button type="submit" className="flex-1" size="default">
              <SlidersHorizontal className="h-4 w-4" />
              Filtrar
            </Button>
            {temFiltro && (
              <Button type="button" variant="outline" size="default" onClick={limparFiltros}>
                Limpar
              </Button>
            )}
          </div>
        </div>

        {/* Filtros de data */}
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label htmlFor="de" className="text-xs font-medium text-muted-foreground">
              A partir de
            </label>
            <Input
              id="de"
              type="datetime-local"
              value={deInput}
              onChange={(e) => setDeInput(e.target.value)}
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="ate" className="text-xs font-medium text-muted-foreground">
              Até
            </label>
            <Input
              id="ate"
              type="datetime-local"
              value={ateInput}
              onChange={(e) => setAteInput(e.target.value)}
            />
          </div>
        </div>
      </form>

      {/* Estado de carregamento */}
      {loading && (
        <div className="flex items-center justify-center py-16">
          <Spinner className="h-8 w-8" />
        </div>
      )}

      {/* Estado de erro */}
      {!loading && erro && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 p-6 text-center">
          <p className="font-medium text-destructive">{erro}</p>
          <Button variant="outline" className="mt-4" onClick={buscar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {/* Estado vazio */}
      {!loading && !erro && eventos.length === 0 && (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed py-16 text-center">
          <Ticket className="h-12 w-12 text-muted-foreground/40" />
          <p className="mt-4 text-lg font-medium text-muted-foreground">
            Nenhum evento encontrado
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {temFiltro
              ? 'Tente ajustar ou limpar os filtros.'
              : 'Nao ha eventos disponiveis no momento.'}
          </p>
          {temFiltro && (
            <Button variant="outline" className="mt-4" onClick={limparFiltros}>
              Limpar filtros
            </Button>
          )}
        </div>
      )}

      {/* Lista de eventos */}
      {!loading && !erro && eventos.length > 0 && (
        <>
          <p className="text-sm text-muted-foreground">
            {totalElements} evento{totalElements !== 1 ? 's' : ''} encontrado{totalElements !== 1 ? 's' : ''}
          </p>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {eventos.map((ev) => (
              <EventoCard key={ev.id} evento={ev} />
            ))}
          </div>

          {/* Paginação */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-4">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => irPagina(page - 1)}
                aria-label="Página anterior"
              >
                Anterior
              </Button>
              <span className="text-sm text-muted-foreground">
                Página {page + 1} de {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => irPagina(page + 1)}
                aria-label="Próxima página"
              >
                Próxima
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function EventoCard({ evento }: { evento: EventoResumo }) {
  return (
    <Card className="flex flex-col overflow-hidden transition-shadow hover:shadow-md">
      {/* Imagem */}
      {evento.imagemUrl && (
        <div className="h-40 overflow-hidden bg-muted">
          <img
            src={evento.imagemUrl}
            alt=""
            aria-hidden="true"
            className="h-full w-full object-cover"
          />
        </div>
      )}

      <CardContent className="flex flex-1 flex-col gap-3 p-4">
        {/* Tipo */}
        <div className="flex items-center gap-2">
          <Badge variant={evento.tipo === 'GRATUITO' ? 'success' : 'default'}>
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
            <Calendar className="h-4 w-4 flex-shrink-0" />
            <span>{formatarData(evento.dataInicio)}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <MapPin className="h-4 w-4 flex-shrink-0" />
            <span className="line-clamp-1">{evento.local}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <Tag className="h-4 w-4 flex-shrink-0" />
            <span>{formatarPreco(evento.preco)}</span>
          </div>
        </div>

        {/* CTA */}
        <div className="mt-auto pt-2">
          <Link
            to={`/eventos/${evento.id}`}
            className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'w-full')}
          >
            Ver detalhes
          </Link>
        </div>
      </CardContent>
    </Card>
  )
}
