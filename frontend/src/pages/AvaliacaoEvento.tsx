import { useEffect, useState } from 'react'
import { Star, AlertCircle, CheckCircle2 } from 'lucide-react'
import { detalheEvento, avaliar, type EventoResponse } from '@/api/events'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { PageLoader } from '@/components/ui/spinner'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatarMedia(media: number | null): string {
  if (media === null) return '—'
  return media.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
}

type AvaliacaoErro = {
  tipo: 'DUPLICADA' | 'NAO_ELEGIVEL' | 'DESCONHECIDO'
  mensagem: string
}

function resolverErro(err: unknown): AvaliacaoErro {
  const status = (err as { response?: { status?: number } })?.response?.status
  const code = (err as { response?: { data?: { message?: string } } })?.response?.data?.message

  if (status === 409 || code === 'AVALIACAO_DUPLICADA') {
    return { tipo: 'DUPLICADA', mensagem: 'AVALIACAO_DUPLICADA — voce ja avaliou este evento.' }
  }
  if (status === 403 || code === 'AVALIACAO_NAO_ELEGIVEL') {
    return {
      tipo: 'NAO_ELEGIVEL',
      mensagem: 'AVALIACAO_NAO_ELEGIVEL — nao elegivel: voce nao participou ou o evento ainda nao foi realizado.',
    }
  }
  return { tipo: 'DESCONHECIDO', mensagem: 'Nao foi possivel enviar a avaliacao. Tente novamente.' }
}

// ─── Sub-componente: Reputação ─────────────────────────────────────────────────

interface ReputacaoProps {
  media: number | null
  total: number
}

function ReputacaoEvento({ media, total }: ReputacaoProps) {
  if (total === 0) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Star className="h-4 w-4" aria-hidden="true" />
        <span>0 avaliacoes — seja o primeiro a avaliar!</span>
      </div>
    )
  }

  return (
    <div className="flex items-center gap-3">
      <div className="flex items-center gap-1">
        <Star className="h-5 w-5 fill-yellow-400 text-yellow-400" aria-hidden="true" />
        <span className="text-lg font-semibold">{formatarMedia(media)}</span>
      </div>
      <span className="text-sm text-muted-foreground">
        {total} avaliação{total !== 1 ? 'ões' : ''}
      </span>
    </div>
  )
}

// ─── Sub-componente: Seletor de nota ─────────────────────────────────────────

interface SeletorNotaProps {
  valor: number | null
  onChange: (nota: number) => void
  disabled?: boolean
}

function SeletorNota({ valor, onChange, disabled = false }: SeletorNotaProps) {
  const notas = [1, 2, 3, 4, 5] as const

  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">Nota</legend>
      <div className="flex gap-2">
        {notas.map((n) => (
          <label
            key={n}
            className="flex cursor-pointer flex-col items-center gap-1"
            aria-label={`${n} estrela${n !== 1 ? 's' : ''}`}
          >
            <input
              type="radio"
              name="nota"
              value={String(n)}
              checked={valor === n}
              onChange={() => onChange(n)}
              disabled={disabled}
              className="sr-only"
              aria-label={`Nota ${n}`}
            />
            <Star
              className={`h-8 w-8 transition-colors ${
                valor !== null && n <= valor
                  ? 'fill-yellow-400 text-yellow-400'
                  : 'fill-transparent text-muted-foreground hover:text-yellow-300'
              } ${disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'}`}
              aria-hidden="true"
            />
            <span className="text-xs text-muted-foreground">{n}</span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}

// ─── Componente principal ─────────────────────────────────────────────────────

interface AvaliacaoEventoProps {
  eventoId: number
}

export function AvaliacaoEvento({ eventoId }: AvaliacaoEventoProps) {
  const [evento, setEvento] = useState<EventoResponse | null>(null)
  const [carregandoEvento, setCarregandoEvento] = useState(true)
  const [erroCarregamento, setErroCarregamento] = useState<string | null>(null)

  const [nota, setNota] = useState<number | null>(null)
  const [comentario, setComentario] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [enviado, setEnviado] = useState(false)
  const [erroEnvio, setErroEnvio] = useState<AvaliacaoErro | null>(null)

  useEffect(() => {
    let cancelled = false
    setCarregandoEvento(true)
    setErroCarregamento(null)

    detalheEvento(eventoId)
      .then((data) => {
        if (!cancelled) setEvento(data)
      })
      .catch(() => {
        if (!cancelled) setErroCarregamento('Não foi possível carregar o evento.')
      })
      .finally(() => {
        if (!cancelled) setCarregandoEvento(false)
      })

    return () => { cancelled = true }
  }, [eventoId])

  async function handleAvaliar(e: React.FormEvent) {
    e.preventDefault()
    if (nota === null) return

    setEnviando(true)
    setErroEnvio(null)

    try {
      await avaliar(eventoId, { nota, comentario: comentario.trim() || null })
      setEnviado(true)
    } catch (err) {
      setErroEnvio(resolverErro(err))
    } finally {
      setEnviando(false)
    }
  }

  if (carregandoEvento) return <PageLoader label="Carregando evento..." />

  if (erroCarregamento || !evento) {
    return (
      <div className="flex flex-col items-center gap-3 py-12 text-center">
        <AlertCircle className="h-10 w-10 text-destructive" aria-hidden="true" />
        <p className="text-destructive">{erroCarregamento ?? 'Evento não encontrado.'}</p>
      </div>
    )
  }

  const reputacao = evento.reputacao ?? { media: null, total: 0 }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      {/* Cabeçalho do evento */}
      <div className="space-y-1">
        <h1 className="text-2xl font-bold tracking-tight">{evento.titulo}</h1>
        <p className="text-sm text-muted-foreground">{evento.local}</p>
      </div>

      {/* Reputação — US-025 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Avaliação geral</CardTitle>
        </CardHeader>
        <CardContent>
          <ReputacaoEvento media={reputacao.media} total={reputacao.total} />
        </CardContent>
      </Card>

      {/* Formulário de avaliação — US-024 */}
      {enviado ? (
        <Card className="border-green-500/40 bg-green-50 dark:bg-green-950/20">
          <CardContent className="flex flex-col items-center gap-3 py-6 text-center">
            <CheckCircle2
              className="h-10 w-10 text-green-600 dark:text-green-400"
              aria-hidden="true"
            />
            <p className="font-semibold text-green-700 dark:text-green-300">
              Avaliação enviada — obrigado pelo seu feedback!
            </p>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Avaliar evento</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleAvaliar} className="space-y-5">
              <SeletorNota
                valor={nota}
                onChange={setNota}
                disabled={enviando}
              />

              <div className="space-y-2">
                <label htmlFor="comentario" className="text-sm font-medium">
                  Comentário <span className="text-muted-foreground">(opcional)</span>
                </label>
                <textarea
                  id="comentario"
                  className="flex min-h-[80px] w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                  placeholder="Compartilhe sua experiência..."
                  value={comentario}
                  onChange={(e) => setComentario(e.target.value)}
                  maxLength={2000}
                  disabled={enviando}
                  rows={3}
                />
              </div>

              {/* Mensagem de erro */}
              {erroEnvio && (
                <div className="flex items-start gap-2 rounded-lg border border-destructive/40 bg-destructive/5 p-3">
                  <AlertCircle
                    className="mt-0.5 h-4 w-4 flex-shrink-0 text-destructive"
                    aria-hidden="true"
                  />
                  <p className="text-sm text-destructive">{erroEnvio.mensagem}</p>
                </div>
              )}

              <Button
                type="submit"
                className="w-full"
                loading={enviando}
                disabled={enviando || nota === null}
              >
                <Star className="h-4 w-4" aria-hidden="true" />
                Avaliar evento
              </Button>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
