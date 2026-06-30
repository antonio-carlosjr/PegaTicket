import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { CreditCard, Clock, CheckCircle2, AlertCircle, QrCode } from 'lucide-react'
import { getPagamentoDaInscricao, confirmarPagamento, type PagamentoResponse } from '@/api/payments'
import { meusIngressos, type MeuIngressoResponse } from '@/api/tickets'
import { extractApiError } from '@/api/auth'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { PageLoader } from '@/components/ui/spinner'
import { toast } from '@/components/ui/toaster'

// Intervalo do polling em ms
const POLLING_INTERVAL_MS = 3000
// Timeout total do polling em ms (60s — ressalva do PO)
const POLLING_TIMEOUT_MS = 60000

type PageState =
  | { fase: 'carregando' }
  | { fase: 'pendente'; pagamento: PagamentoResponse }
  | { fase: 'pagando' }
  | { fase: 'aguardando'; pagamento: PagamentoResponse }
  | { fase: 'confirmado'; ingresso: MeuIngressoResponse }
  | { fase: 'timeout' }
  | { fase: 'recusado' }
  | { fase: 'erro'; mensagem: string }

function formatarValor(valor: string): string {
  const n = parseFloat(valor)
  return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function CheckoutPage() {
  const { inscricaoId } = useParams<{ inscricaoId: string }>()
  const navigate = useNavigate()
  const [state, setState] = useState<PageState>({ fase: 'carregando' })
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const idNumerico = Number(inscricaoId)

  // Carrega o pagamento inicial
  useEffect(() => {
    if (!inscricaoId || isNaN(idNumerico)) return
    let cancelled = false

    getPagamentoDaInscricao(idNumerico)
      .then((pag) => {
        if (cancelled) return
        if (pag.status === 'CONFIRMADO') {
          // Ja confirmado — busca ingresso
          iniciarPolling()
        } else {
          setState({ fase: 'pendente', pagamento: pag })
        }
      })
      .catch((e) => {
        if (cancelled) return
        const msg = extractApiError(e, 'Nao foi possivel carregar o pagamento.')
        setState({ fase: 'erro', mensagem: msg })
      })

    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inscricaoId])

  // Limpa timers ao desmontar
  useEffect(() => {
    return () => {
      pararPolling()
    }
  }, [])

  function pararPolling() {
    if (pollingRef.current) {
      clearInterval(pollingRef.current)
      pollingRef.current = null
    }
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current)
      timeoutRef.current = null
    }
  }

  function iniciarPolling() {
    pararPolling()

    // Timeout de 60s
    timeoutRef.current = setTimeout(() => {
      pararPolling()
      setState({ fase: 'timeout' })
    }, POLLING_TIMEOUT_MS)

    // Polling a cada 3s
    const executarPolling = () => {
      meusIngressos()
        .then((ingressos) => {
          const ingresso = ingressos.find((i) => i.inscricaoId === idNumerico && i.statusInscricao === 'ATIVA' && i.codigoUnico)
          if (ingresso) {
            pararPolling()
            setState({ fase: 'confirmado', ingresso })
          }
        })
        .catch(() => {
          // Erro transiente no polling: continua tentando ate o timeout
        })
    }

    // Executa imediatamente e depois em intervalos
    executarPolling()
    pollingRef.current = setInterval(executarPolling, POLLING_INTERVAL_MS)
  }

  async function handlePagar() {
    setState({ fase: 'pagando' })
    try {
      const pagamento = await confirmarPagamento(idNumerico)
      setState({ fase: 'aguardando', pagamento })
      iniciarPolling()
    } catch (e) {
      const status = (e as { response?: { status?: number } })?.response?.status
      const code = (e as { response?: { data?: { message?: string } } })?.response?.data?.message

      if (status === 402 || code === 'PAGAMENTO_RECUSADO') {
        setState({ fase: 'recusado' })
        toast.error('Erro ao processar pagamento', {
          description: 'O gateway recusou a transacao. Tente de novo em instantes.',
        })
      } else if (code === 'INSCRICAO_EXPIRADA') {
        setState({ fase: 'erro', mensagem: 'Sua reserva expirou. Realize uma nova inscricao.' })
        toast.error('Reserva expirada', {
          description: 'O prazo de pagamento venceu e sua vaga foi liberada.',
        })
      } else {
        const msg = extractApiError(e, 'Nao foi possivel processar o pagamento.')
        setState({ fase: 'erro', mensagem: msg })
        toast.error('Erro no pagamento', { description: msg })
      }
    }
  }

  function handleTentarNovamente() {
    setState({ fase: 'carregando' })
    getPagamentoDaInscricao(idNumerico)
      .then((pag) => {
        setState({ fase: 'pendente', pagamento: pag })
      })
      .catch((e) => {
        const msg = extractApiError(e, 'Nao foi possivel carregar o pagamento.')
        setState({ fase: 'erro', mensagem: msg })
      })
  }

  // ─── Renders por estado ────────────────────────────────────────────────────

  if (state.fase === 'carregando') {
    return <PageLoader label="Carregando pagamento..." />
  }

  if (state.fase === 'erro') {
    return (
      <div className="mx-auto max-w-md space-y-6">
        <Card>
          <CardContent className="flex flex-col items-center gap-4 py-10 text-center">
            <AlertCircle className="h-12 w-12 text-destructive" aria-hidden="true" />
            <p className="text-base font-medium text-destructive">{state.mensagem}</p>
            <Button variant="outline" onClick={() => navigate('/meus-ingressos')}>
              Ver meus ingressos
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (state.fase === 'timeout') {
    return (
      <div className="mx-auto max-w-md space-y-6">
        <Card>
          <CardContent className="flex flex-col items-center gap-4 py-10 text-center">
            <Clock className="h-12 w-12 text-warning" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">
              Nao foi possivel confirmar o recebimento do seu ingresso. A confirmacao demorou
              mais do que o esperado. Verifique em "Meus ingressos" ou tente novamente.
            </p>
            <div className="flex gap-3">
              <Button variant="outline" onClick={() => navigate('/meus-ingressos')}>
                Meus ingressos
              </Button>
              <Button onClick={() => {
                setState({ fase: 'carregando' })
                iniciarPolling()
              }}>
                Verificar novamente
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (state.fase === 'confirmado') {
    return (
      <div className="mx-auto max-w-md space-y-6">
        <Card className="border-success/40 bg-success/5">
          <CardContent className="flex flex-col items-center gap-4 py-8 text-center">
            <CheckCircle2 className="h-12 w-12 text-success" aria-hidden="true" />
            <p className="text-base font-semibold text-success">
              Ingresso confirmado! — {state.ingresso.codigoUnico}
            </p>
            <p className="text-sm text-muted-foreground">
              Seu pagamento foi confirmado. Acesse "Meus ingressos" para resgatar seu ingresso.
            </p>
            <Button onClick={() => navigate('/meus-ingressos')}>
              Ver meus ingressos
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (state.fase === 'recusado') {
    return (
      <div className="mx-auto max-w-md space-y-6">
        <Card>
          <CardContent className="flex flex-col items-center gap-4 py-10 text-center">
            <AlertCircle className="h-12 w-12 text-destructive" aria-hidden="true" />
            <p className="text-base font-medium text-destructive">
              Pagamento recusado — nao foi possivel processar. Tente novamente.
            </p>
            <Button onClick={handleTentarNovamente}>
              Tentar de novo
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  // Pendente ou pagando ou aguardando
  const pagamento = (state.fase === 'pendente' || state.fase === 'aguardando') ? state.pagamento : null
  const aguardando = state.fase === 'aguardando'
  const pagando = state.fase === 'pagando'

  return (
    <div className="mx-auto max-w-md space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Checkout</h1>
        <p className="mt-1 text-muted-foreground">Finalize seu pagamento para garantir o ingresso.</p>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-lg">Pagamento pendente</CardTitle>
            <Badge variant="warning">Pendente</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Valor */}
          {pagamento && (
            <div className="rounded-lg border bg-muted/40 p-4 text-center">
              <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                Valor a pagar
              </p>
              <p className="mt-1 text-3xl font-bold text-foreground">
                {formatarValor(pagamento.valorBruto)}
              </p>
            </div>
          )}

          {/* Aviso de prazo (ressalva do PO) */}
          <div className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/10 p-3">
            <Clock className="h-4 w-4 flex-shrink-0 text-warning mt-0.5" aria-hidden="true" />
            <p className="text-sm text-foreground">
              <strong>Pague em ate 30 min</strong> para garantir sua vaga.
              Apos esse prazo, a reserva e cancelada automaticamente.
            </p>
          </div>

          {/* Estado aguardando confirmacao assincrona */}
          {aguardando && (
            <div className="flex flex-col items-center gap-3 rounded-lg border bg-muted/30 p-4 text-center">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" aria-hidden="true" />
              <p className="text-sm font-medium">Aguardando confirmacao</p>
              <p className="text-xs text-muted-foreground">
                O ingresso sera emitido em instantes apos a confirmacao.
              </p>
            </div>
          )}

          {/* Processando */}
          {pagando && (
            <div className="flex flex-col items-center gap-3 rounded-lg border bg-muted/30 p-4 text-center">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" aria-hidden="true" />
              <p className="text-sm font-medium">Processando...</p>
            </div>
          )}

          {/* Botao Pagar — exibido apenas no estado pendente */}
          {state.fase === 'pendente' && (
            <Button
              size="lg"
              className="w-full"
              onClick={handlePagar}
              disabled={pagando}
            >
              <CreditCard className="h-5 w-5" aria-hidden="true" />
              Pagar
            </Button>
          )}
        </CardContent>
      </Card>

      <p className="text-center text-xs text-muted-foreground">
        Pagamento processado com seguranca pelo gateway simulado.
      </p>
    </div>
  )
}
