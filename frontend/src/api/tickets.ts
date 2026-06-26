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

export interface InscricaoResponse {
  id: number
  eventoId: number
  status: string        // "ATIVA"
  inscritoEm: string    // OffsetDateTime como ISO-8601
  ingresso: IngressoResponse
}

export interface MeuIngressoResponse {
  ingressoId: number
  codigoUnico: string       // p/ render do QR
  statusIngresso: string    // ATIVO | UTILIZADO | CANCELADO
  inscricaoId: number
  eventoId: number          // front busca nome/data/local no event-service
  statusInscricao: string   // ATIVA | CANCELADA
  emitidoEm: string
}

export interface InscricaoHistoricoResponse {
  id: number
  eventoId: number
  status: string      // ATIVA | CANCELADA
  inscritoEm: string  // OffsetDateTime como ISO-8601
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
