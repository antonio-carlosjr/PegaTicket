import { useState } from 'react'
import { AlertTriangle, CheckCircle2, XCircle, Calendar } from 'lucide-react'
import {
  cancelarInscricao,
  type InscricaoHistoricoResponse,
  type CancelamentoResponse,
} from '@/api/tickets'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Calcula a data-limite de cancelamento com reembolso: dataInicio - prazoReembolsoDias. */
function calcularDataLimite(dataInicio: string, prazoReembolsoDias: number): Date {
  const inicio = new Date(dataInicio)
  const limite = new Date(inicio)
  limite.setDate(limite.getDate() - prazoReembolsoDias)
  return limite
}

function formatarData(date: Date): string {
  return date.toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  })
}

type CancelamentoErro = {
  tipo: 'PRAZO_ENCERRADO' | 'JA_CANCELADA' | 'DESCONHECIDO'
  mensagem: string
}

function resolverErro(err: unknown): CancelamentoErro {
  const status = (err as { response?: { status?: number } })?.response?.status
  const code = (err as { response?: { data?: { message?: string } } })?.response?.data?.message

  if (status === 422 || code === 'PRAZO_CANCELAMENTO_ENCERRADO') {
    return {
      tipo: 'PRAZO_ENCERRADO',
      mensagem: 'Prazo encerrado — não é possível cancelar esta inscrição após o prazo de reembolso.',
    }
  }
  if (status === 409 || code === 'INSCRICAO_JA_CANCELADA') {
    return {
      tipo: 'JA_CANCELADA',
      mensagem: 'Esta inscricao ja cancelada — não é possível cancelar novamente.',
    }
  }
  return {
    tipo: 'DESCONHECIDO',
    mensagem: 'Não foi possível cancelar a inscrição. Tente novamente.',
  }
}

// ─── Componente ───────────────────────────────────────────────────────────────

interface CancelarInscricaoProps {
  inscricao: InscricaoHistoricoResponse
  onCancelado?: () => void
}

export function CancelarInscricao({ inscricao, onCancelado }: CancelarInscricaoProps) {
  const [carregando, setCarregando] = useState(false)
  const [resultado, setResultado] = useState<CancelamentoResponse | null>(null)
  const [erro, setErro] = useState<CancelamentoErro | null>(null)

  const evento = inscricao.evento
  const ehPago = evento?.tipo === 'PAGO'
  const dataLimite =
    ehPago && evento?.prazoReembolsoDias != null && evento?.dataInicio
      ? calcularDataLimite(evento.dataInicio, evento.prazoReembolsoDias)
      : null

  async function handleCancelar() {
    setCarregando(true)
    setResultado(null)
    setErro(null)

    try {
      const res = await cancelarInscricao(inscricao.id)
      setResultado(res)
      onCancelado?.()
    } catch (err) {
      setErro(resolverErro(err))
    } finally {
      setCarregando(false)
    }
  }

  // Estado: inscrição cancelada com sucesso
  if (resultado) {
    return (
      <Card className="border-green-500/40 bg-green-50 dark:bg-green-950/20">
        <CardContent className="flex flex-col items-center gap-3 py-6 text-center">
          <CheckCircle2
            className="h-10 w-10 text-green-600 dark:text-green-400"
            aria-hidden="true"
          />
          <div>
            <p className="font-semibold text-green-700 dark:text-green-300">
              Inscrição cancelada com sucesso.
            </p>
            {resultado.reembolsoIniciado && (
              <p className="mt-1 text-sm text-muted-foreground">
                Reembolso iniciado — o valor será estornado em instantes.
              </p>
            )}
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <AlertTriangle className="h-5 w-5 text-warning" aria-hidden="true" />
          Cancelar inscrição
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <span>Inscrição #{inscricao.id}</span>
          <Badge variant={inscricao.status === 'ATIVA' ? 'success' : 'secondary'}>
            {inscricao.status}
          </Badge>
          {ehPago && <Badge variant="default">Pago</Badge>}
        </div>

        {/* Prazo de reembolso — exibido apenas para eventos PAGO */}
        {ehPago && dataLimite && (
          <div className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 dark:border-amber-700 dark:bg-amber-950/30">
            <Calendar className="mt-0.5 h-4 w-4 flex-shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />
            <p className="text-sm text-amber-800 dark:text-amber-200">
              Prazo de reembolso:{' '}
              <strong>{formatarData(dataLimite)}</strong>. Cancelamentos após esta data não geram reembolso.
            </p>
          </div>
        )}

        {/* Aviso de cancelamento gratuito */}
        {!ehPago && (
          <p className="text-sm text-muted-foreground">
            Este evento é gratuito. O cancelamento não envolve reembolso.
          </p>
        )}

        {/* Mensagem de erro */}
        {erro && (
          <div className="flex items-start gap-2 rounded-lg border border-destructive/40 bg-destructive/5 p-3">
            <XCircle className="mt-0.5 h-4 w-4 flex-shrink-0 text-destructive" aria-hidden="true" />
            <p className="text-sm text-destructive">{erro.mensagem}</p>
          </div>
        )}

        <Button
          variant="destructive"
          className="w-full"
          onClick={handleCancelar}
          loading={carregando}
          disabled={carregando}
          aria-label="Cancelar inscrição"
        >
          Cancelar inscrição
        </Button>
      </CardContent>
    </Card>
  )
}
