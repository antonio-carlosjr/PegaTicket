import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Calendar, MapPin, QrCode, Ticket } from 'lucide-react'
import { QRCodeSVG } from 'qrcode.react'
import { meusIngressos, type MeuIngressoResponse } from '@/api/tickets'
import { detalheEvento, type EventoResponse } from '@/api/events'
import { extractApiError } from '@/api/auth'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { PageLoader } from '@/components/ui/spinner'
import { cn } from '@/lib/utils'
import { toast } from '@/components/ui/toaster'

// ─── Tipos auxiliares ─────────────────────────────────────────────────────────

interface IngressoComEvento {
  ingresso: MeuIngressoResponse
  evento: EventoResponse | null
  erroEvento: boolean
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatarDataHora(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function badgeStatusIngresso(status: string) {
  switch (status) {
    case 'ATIVO':
      return <Badge variant="success">Ativo</Badge>
    case 'UTILIZADO':
      return <Badge variant="secondary">Utilizado</Badge>
    case 'CANCELADO':
      return <Badge variant="destructive">Cancelado</Badge>
    default:
      return <Badge variant="outline">{status}</Badge>
  }
}

// ─── Componente ───────────────────────────────────────────────────────────────

export function MeusIngressos() {
  const [itens, setItens] = useState<IngressoComEvento[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setErro(null)

    meusIngressos()
      .then(async (ingressos) => {
        if (cancelled) return

        // Compõe dados do evento para cada ingresso em paralelo
        const compostos = await Promise.all(
          ingressos.map(async (ing): Promise<IngressoComEvento> => {
            try {
              const evento = await detalheEvento(ing.eventoId)
              return { ingresso: ing, evento, erroEvento: false }
            } catch {
              // Evento pode ter sido cancelado/removido — exibe dados parciais
              return { ingresso: ing, evento: null, erroEvento: true }
            }
          })
        )

        if (!cancelled) setItens(compostos)
      })
      .catch((e) => {
        if (cancelled) return
        const msg = extractApiError(e, 'Não foi possível carregar seus ingressos.')
        setErro(msg)
        toast.error('Erro ao carregar ingressos', { description: msg })
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [])

  if (loading) return <PageLoader label="Carregando seus ingressos..." />

  if (erro) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <p className="text-lg font-medium text-destructive">{erro}</p>
        <Button
          variant="outline"
          className="mt-6"
          onClick={() => window.location.reload()}
        >
          Tentar novamente
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Cabeçalho */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Meus ingressos</h1>
        <p className="mt-1 text-muted-foreground">
          Todos os seus ingressos gerados após inscrição em eventos.
        </p>
      </div>

      {/* Estado vazio */}
      {itens.length === 0 && (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed py-20 text-center">
          <QrCode className="h-14 w-14 text-muted-foreground/40" aria-hidden="true" />
          <p className="mt-4 text-lg font-medium text-muted-foreground">
            Você ainda não se inscreveu em nenhum evento
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            Que tal explorar os eventos disponíveis?
          </p>
          <Link
            to="/eventos"
            className={cn(buttonVariants({ variant: 'default' }), 'mt-6')}
          >
            <Ticket className="h-4 w-4" />
            Explorar eventos
          </Link>
        </div>
      )}

      {/* Lista de ingressos */}
      {itens.length > 0 && (
        <div className="grid gap-6 sm:grid-cols-2">
          {itens.map(({ ingresso, evento, erroEvento }) => (
            <IngressoCard
              key={ingresso.ingressoId}
              ingresso={ingresso}
              evento={evento}
              erroEvento={erroEvento}
            />
          ))}
        </div>
      )}
    </div>
  )
}

// ─── Card individual ──────────────────────────────────────────────────────────

function IngressoCard({
  ingresso,
  evento,
  erroEvento,
}: {
  ingresso: MeuIngressoResponse
  evento: EventoResponse | null
  erroEvento: boolean
}) {
  return (
    <Card className="overflow-hidden">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-base leading-snug">
            {evento ? evento.titulo : `Evento #${ingresso.eventoId}`}
          </CardTitle>
          {badgeStatusIngresso(ingresso.statusIngresso)}
        </div>
        {erroEvento && (
          <p className="text-xs text-muted-foreground">
            Não foi possível carregar detalhes do evento.
          </p>
        )}
      </CardHeader>

      <CardContent className="space-y-4">
        {/* Dados do evento */}
        {evento && (
          <div className="space-y-1.5 text-sm text-muted-foreground">
            <div className="flex items-center gap-1.5">
              <Calendar className="h-4 w-4 flex-shrink-0" />
              <span>{formatarDataHora(evento.dataInicio)}</span>
            </div>
            <div className="flex items-center gap-1.5">
              <MapPin className="h-4 w-4 flex-shrink-0" />
              <span className="line-clamp-1">{evento.local}</span>
            </div>
          </div>
        )}

        {/* QR Code */}
        <div className="flex flex-col items-center gap-3 rounded-lg border bg-white p-4">
          <div aria-label={`QR code do ingresso para ${evento?.titulo ?? 'evento'}`}>
            <QRCodeSVG
              value={ingresso.codigoUnico}
              size={180}
              level="M"
            />
          </div>
          <p className="max-w-[180px] break-all text-center text-[10px] font-mono text-muted-foreground">
            {ingresso.codigoUnico}
          </p>
        </div>

        <p className="text-center text-xs text-muted-foreground">
          Emitido em {formatarDataHora(ingresso.emitidoEm)}
        </p>
      </CardContent>
    </Card>
  )
}
