/**
 * API de pagamentos — Sprint 4 (US-040/041).
 * Todos os endpoints sao autenticados via JWT (header Authorization: Bearer <token>).
 * O backend le X-User-Id do gateway; o frontend so precisa enviar o JWT.
 */
import { api } from './client'

// ─── Tipos ───────────────────────────────────────────────────────────────────

export interface PagamentoResponse {
  id: number
  inscricaoId: number
  usuarioId: number
  valorBruto: string         // NUMERIC(12,2) como string
  valorTaxa: string          // round(bruto * 0.10, 2)
  valorRepasse: string       // bruto - taxa (computado, nao liberado)
  status: string             // "PENDENTE" | "CONFIRMADO"
  gateway: string            // "SIMULADO"
  gatewayPaymentId: string | null   // preenchido apos confirmacao
  processadoEm: string | null       // OffsetDateTime ISO-8601
  criadoEm: string                  // OffsetDateTime ISO-8601
}

// ─── Funcoes de API ──────────────────────────────────────────────────────────

/**
 * GET /api/payments/inscricao/:inscricaoId
 * Retorna o status do pagamento de uma inscricao do proprio usuario.
 * Usado no polling do CheckoutPage.
 */
export async function getPagamentoDaInscricao(inscricaoId: number): Promise<PagamentoResponse> {
  const { data } = await api.get<PagamentoResponse>(`/api/payments/inscricao/${inscricaoId}`)
  return data
}

/**
 * POST /api/payments/:inscricaoId/confirmar
 * Gateway SIMULADO aprova o pagamento. Idempotente.
 * 1 toque — sem corpo.
 */
export async function confirmarPagamento(inscricaoId: number): Promise<PagamentoResponse> {
  const { data } = await api.post<PagamentoResponse>(`/api/payments/${inscricaoId}/confirmar`)
  return data
}

/**
 * GET /api/payments/me
 * Lista pagamentos do usuario autenticado (mais recente primeiro).
 */
export async function listarMeusPagamentos(): Promise<PagamentoResponse[]> {
  const { data } = await api.get<PagamentoResponse[]>('/api/payments/me')
  return data
}
