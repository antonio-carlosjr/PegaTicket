import { api } from './client'
import type { Page } from './events'

// ─── Tipos ────────────────────────────────────────────────────────────────────

export interface IngressoResponse {
  id: number
  inscricaoId: number
  codigoUnico: string   // UUID v4 — front renderiza QR a partir disto
  status: string        // "ATIVO"
  emitidoEm: string     // OffsetDateTime como ISO-8601
}

/** Referencia de checkout retornada pelo ticket-service para inscricoes PAGAS. */
export interface PagamentoPendenteResponse {
  inscricaoId: number
  valor: string          // BigDecimal como string
  status: string         // "AGUARDANDO"
}

export interface InscricaoResponse {
  id: number
  eventoId: number
  status: string          // "ATIVA" (GRATUITO) | "PENDENTE_PAGAMENTO" (PAGO)
  inscritoEm: string      // OffsetDateTime como ISO-8601
  ingresso: IngressoResponse | null     // null quando PENDENTE_PAGAMENTO
  pagamento?: PagamentoPendenteResponse | null  // null/ausente quando GRATUITO
}

export interface MeuIngressoResponse {
  ingressoId: number
  codigoUnico: string       // p/ render do QR
  statusIngresso: string    // ATIVO | UTILIZADO | CANCELADO
  inscricaoId: number
  eventoId: number          // front busca nome/data/local no event-service
  statusInscricao: string   // ATIVA | CANCELADA | PENDENTE_PAGAMENTO | EXPIRADA
  emitidoEm: string
}

/** Resumo do evento embutido no histórico de inscrições (adicionado na 5B). */
export interface EventoResumoInscricao {
  dataInicio: string        // ISO-8601
  prazoReembolsoDias: number | null
  tipo: 'GRATUITO' | 'PAGO'
}

export interface InscricaoHistoricoResponse {
  id: number
  eventoId: number
  status: string      // ATIVA | CANCELADA
  inscritoEm: string  // OffsetDateTime como ISO-8601
  /** Campos da 5B: resumo do evento para exibir prazo e tipo no cancelamento. */
  evento?: EventoResumoInscricao
}

/** Response do check-in de ingresso (US-034). */
export interface CheckinResponse {
  ingressoId: number
  inscricaoId: number
  status: string         // "UTILIZADO"
  realizadoEm: string    // ISO-8601
}

/** Response do cancelamento de inscrição (US-035). */
export interface CancelamentoResponse {
  inscricaoId: number
  status: string            // "CANCELADA"
  reembolsoIniciado: boolean
}

// ─── Funções de API ───────────────────────────────────────────────────────────

/** POST /api/tickets/inscricoes — inscreve o usuário autenticado num evento GRATUITO. */
export async function inscrever(eventoId: number): Promise<InscricaoResponse> {
  const { data } = await api.post<InscricaoResponse>('/api/tickets/inscricoes', { eventoId })
  return data
}

/** GET /api/tickets/me — lista os ingressos do usuário autenticado (não paginado). */
export async function meusIngressos(): Promise<MeuIngressoResponse[]> {
  const { data } = await api.get<MeuIngressoResponse[]>('/api/tickets/me')
  return data
}

/** GET /api/tickets/inscricoes/me — histórico de inscrições paginado, mais recente primeiro. */
export async function historicoInscricoes(page = 0): Promise<Page<InscricaoHistoricoResponse>> {
  const { data } = await api.get<Page<InscricaoHistoricoResponse>>('/api/tickets/inscricoes/me', {
    params: { page, size: 20, sort: 'inscritoEm,desc' },
  })
  return data
}

/**
 * POST /api/tickets/checkin — valida ingresso na entrada do evento (US-034).
 * Exclusivo para promotor dono do evento.
 * 200 → CheckinResponse; 409 INGRESSO_JA_UTILIZADO; 403 CHECKIN_EVENTO_ALHEIO / papel;
 * 404 INGRESSO_NAO_ENCONTRADO.
 */
export async function checkin(codigoUnico: string): Promise<CheckinResponse> {
  const { data } = await api.post<CheckinResponse>('/api/tickets/checkin', { codigoUnico })
  return data
}

/**
 * DELETE /api/tickets/inscricoes/:id — cancela a própria inscrição (US-035).
 * 200 → CancelamentoResponse (reembolsoIniciado=true se PAGO dentro do prazo).
 * 422 PRAZO_CANCELAMENTO_ENCERRADO; 409 INSCRICAO_JA_CANCELADA; 403 CANCELAMENTO_DE_OUTRO.
 */
export async function cancelarInscricao(id: number): Promise<CancelamentoResponse> {
  const { data } = await api.delete<CancelamentoResponse>(`/api/tickets/inscricoes/${id}`)
  return data
}
