/**
 * MeusIngressosPendentes — Extrato de pagamentos do usuario autenticado.
 * Exibe todos os pagamentos (PENDENTE, CONFIRMADO, REPASSADO, REEMBOLSADO).
 * Sprint 5A: TECH-S4-01 — campos eventoId/promotorId; US-043/042 — badges novos.
 */
import { useEffect, useState } from 'react'
import { CreditCard } from 'lucide-react'
import { listarMeusPagamentos, type PagamentoResponse } from '@/api/payments'
import { extractApiError } from '@/api/auth'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PageLoader } from '@/components/ui/spinner'
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

function formatarValor(valor: string): string {
  const n = parseFloat(valor)
  return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function BadgeStatus({ status }: { status: string }) {
  switch (status) {
    case 'PENDENTE':
      return <Badge variant="warning">Pendente</Badge>
    case 'CONFIRMADO':
      return <Badge variant="success">Confirmado</Badge>
    case 'REPASSADO':
      return <Badge variant="secondary">Repassado</Badge>
    case 'REEMBOLSADO':
      return <Badge variant="destructive">Reembolsado</Badge>
    default:
      return <Badge variant="outline">{status}</Badge>
  }
}

// ─── Card de pagamento ────────────────────────────────────────────────────────

function PagamentoCard({ pagamento }: { pagamento: PagamentoResponse }) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-sm font-medium text-muted-foreground">
            Inscrição #{pagamento.inscricaoId}
            {pagamento.eventoId != null && (
              <span className="ml-1 text-xs">· Evento #{pagamento.eventoId}</span>
            )}
          </CardTitle>
          <BadgeStatus status={pagamento.status} />
        </div>
      </CardHeader>

      <CardContent className="space-y-2 text-sm">
        {/* Valores */}
        {pagamento.status !== 'REEMBOLSADO' && (
          <div className="flex justify-between">
            <span className="text-muted-foreground">Valor pago</span>
            <span className="font-medium">{formatarValor(pagamento.valorBruto)}</span>
          </div>
        )}

        {(pagamento.status === 'REPASSADO' || pagamento.status === 'CONFIRMADO') && (
          <div className="flex justify-between">
            <span className="text-muted-foreground">Repasse (−10%)</span>
            <span className="font-medium text-success">{formatarValor(pagamento.valorRepasse)}</span>
          </div>
        )}

        {pagamento.status === 'REEMBOLSADO' && (
          <div className="flex justify-between">
            <span className="text-muted-foreground">Estorno</span>
            <span className="font-medium text-destructive">{formatarValor(pagamento.valorBruto)}</span>
          </div>
        )}

        {/* Gateway */}
        <div className="flex justify-between text-xs text-muted-foreground">
          <span>Gateway</span>
          <span>{pagamento.gateway}</span>
        </div>

        {/* Data */}
        <div className="flex justify-between text-xs text-muted-foreground">
          <span>Criado em</span>
          <span>{formatarData(pagamento.criadoEm)}</span>
        </div>

        {pagamento.processadoEm && (
          <div className="flex justify-between text-xs text-muted-foreground">
            <span>Processado em</span>
            <span>{formatarData(pagamento.processadoEm)}</span>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

// ─── Componente principal ─────────────────────────────────────────────────────

export function MeusIngressosPendentes() {
  const [pagamentos, setPagamentos] = useState<PagamentoResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  async function carregar() {
    setLoading(true)
    setErro(null)
    try {
      const dados = await listarMeusPagamentos()
      setPagamentos(dados)
    } catch (e) {
      const msg = extractApiError(e, 'Não foi possível carregar seus pagamentos.')
      setErro(msg)
      toast.error('Erro ao carregar pagamentos', { description: msg })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    carregar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (loading) return <PageLoader label="Carregando seu extrato..." />

  if (erro) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <p className="text-lg font-medium text-destructive">{erro}</p>
        <Button variant="outline" className="mt-6" onClick={carregar}>
          Tentar novamente
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Cabeçalho */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Extrato de pagamentos</h1>
        <p className="mt-1 text-muted-foreground">
          Histórico de pagamentos e status de repasse/reembolso.
        </p>
      </div>

      {/* Estado vazio */}
      {pagamentos.length === 0 && (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed py-20 text-center">
          <CreditCard className="h-14 w-14 text-muted-foreground/40" aria-hidden="true" />
          <p className="mt-4 text-lg font-medium text-muted-foreground">
            Nenhum pagamento encontrado
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            Seus pagamentos aparecerão aqui após inscrição em eventos pagos.
          </p>
        </div>
      )}

      {/* Lista */}
      {pagamentos.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {pagamentos.map((p) => (
            <PagamentoCard key={p.id} pagamento={p} />
          ))}
        </div>
      )}
    </div>
  )
}
