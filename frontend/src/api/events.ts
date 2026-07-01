import { api } from './client'

// ─── Enums ────────────────────────────────────────────────────────────────────

export type TipoEvento = 'GRATUITO' | 'PAGO'
export type StatusEvento = 'RASCUNHO' | 'PUBLICADO' | 'CANCELADO' | 'REALIZADO'

// ─── Tipos de response ────────────────────────────────────────────────────────

/** Reputação agregada do evento — US-025. media=null quando total=0. */
export interface ReputacaoResponse {
  media: number | null
  total: number
}

/** Response de avaliação criada — US-024. */
export interface AvaliacaoResponse {
  id: number
  eventoId: number
  usuarioId: number
  nota: number
  comentario: string | null
  avaliadoEm: string  // ISO-8601
}

/** Request de avaliação de evento — US-024. */
export interface AvaliacaoRequest {
  nota: number        // 1–5
  comentario?: string | null
}

/** Response completo do evento (detalhe e criação/edição). */
export interface EventoResponse {
  id: number
  titulo: string
  descricao: string | null
  dataInicio: string // ISO-8601 offset
  dataFim: string
  local: string
  tipo: TipoEvento
  status: StatusEvento
  capacidade: number
  vagasDisponiveis: number | null // null enquanto RASCUNHO
  preco: string | null // null se GRATUITO (BigDecimal como string no JSON)
  prazoReembolsoDias: number | null
  imagemUrl: string | null
  promotorId: number
  criadoEm: string
  atualizadoEm: string
  /** Reputação do evento — US-025. Adicionado na Sprint 5B (campo aditivo). */
  reputacao?: ReputacaoResponse
}

/** Projeção enxuta usada nas listagens. */
export interface EventoResumo {
  id: number
  titulo: string
  dataInicio: string
  dataFim: string
  local: string
  tipo: TipoEvento
  status: StatusEvento
  preco: string | null
  capacidade: number
  imagemUrl: string | null
}

/** Envelope Page<T> do Spring. */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ─── Tipos de request ─────────────────────────────────────────────────────────

export interface EventoCreatePayload {
  titulo: string
  descricao?: string | null
  dataInicio: string // ISO-8601
  dataFim: string
  local: string
  tipo: TipoEvento
  capacidade: number
  preco?: string | null
  prazoReembolsoDias?: number | null
  imagemUrl?: string | null
}

export interface EventoUpdatePayload {
  titulo: string
  descricao?: string | null
  dataInicio: string
  dataFim: string
  local: string
  tipo: TipoEvento
  capacidade: number
  preco?: string | null
  prazoReembolsoDias?: number | null
  imagemUrl?: string | null
}

export interface ListarEventosParams {
  q?: string
  tipo?: TipoEvento
  de?: string // ISO date-time
  ate?: string
  page?: number
  size?: number
}

// ─── Funções de API ───────────────────────────────────────────────────────────

/** POST /api/events — cria evento (PROMOTOR). */
export async function criarEvento(payload: EventoCreatePayload): Promise<EventoResponse> {
  const { data } = await api.post<EventoResponse>('/api/events', payload)
  return data
}

/** GET /api/events/meus — lista eventos do promotor logado (PROMOTOR). */
export async function meusEventos(
  params?: { page?: number; size?: number }
): Promise<Page<EventoResumo>> {
  const { data } = await api.get<Page<EventoResumo>>('/api/events/meus', { params })
  return data
}

/** PUT /api/events/:id — edita evento RASCUNHO (PROMOTOR owner). */
export async function editarEvento(
  id: number,
  payload: EventoUpdatePayload
): Promise<EventoResponse> {
  const { data } = await api.put<EventoResponse>(`/api/events/${id}`, payload)
  return data
}

/** POST /api/events/:id/publicar — publica evento RASCUNHO (PROMOTOR owner). */
export async function publicarEvento(id: number): Promise<EventoResponse> {
  const { data } = await api.post<EventoResponse>(`/api/events/${id}/publicar`)
  return data
}

/** POST /api/events/:id/cancelar — cancela evento (PROMOTOR owner). */
export async function cancelarEvento(id: number): Promise<EventoResponse> {
  const { data } = await api.post<EventoResponse>(`/api/events/${id}/cancelar`)
  return data
}

/** POST /api/events/:id/encerrar — encerra evento PUBLICADO → REALIZADO (PROMOTOR owner). */
export async function encerrarEvento(id: number): Promise<EventoResponse> {
  const { data } = await api.post<EventoResponse>(`/api/events/${id}/encerrar`)
  return data
}

/** GET /api/events — lista eventos PUBLICADOS (qualquer autenticado). */
export async function listarEventos(
  params?: ListarEventosParams
): Promise<Page<EventoResumo>> {
  const { data } = await api.get<Page<EventoResumo>>('/api/events', { params })
  return data
}

/** GET /api/events/:id — detalhe do evento (qualquer autenticado). Inclui reputacao (US-025). */
export async function detalheEvento(id: number): Promise<EventoResponse> {
  const { data } = await api.get<EventoResponse>(`/api/events/${id}`)
  return data
}

/**
 * POST /api/events/:id/avaliacoes — avalia o evento (US-024).
 * 201 → AvaliacaoResponse; 409 AVALIACAO_DUPLICADA; 403 AVALIACAO_NAO_ELEGIVEL; 400 nota inválida.
 */
export async function avaliar(id: number, payload: AvaliacaoRequest): Promise<AvaliacaoResponse> {
  const { data } = await api.post<AvaliacaoResponse>(`/api/events/${id}/avaliacoes`, payload)
  return data
}
