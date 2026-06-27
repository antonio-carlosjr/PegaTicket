import { api } from './client'

export type Papel = 'PARTICIPANTE' | 'PROMOTOR' | 'ADMIN'

export type Usuario = {
  id: number
  nome: string
  email: string
  papel: Papel
  verificado: boolean
  criadoEm: string
}

export type LoginResponse = {
  token: string
  tokenType: string
  expiresInMs: number
  userId: number
  email: string
  papel: Papel
  verificado: boolean
}

export type RegisterPayload = {
  nome: string
  email: string
  senha: string
  papel?: Papel
  cpf?: string
  telefone?: string
}

export async function register(payload: RegisterPayload): Promise<Usuario> {
  const { data } = await api.post<Usuario>('/api/auth/register', payload)
  return data
}

export async function login(email: string, senha: string): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/api/auth/login', { email, senha })
  return data
}

export async function me(): Promise<Usuario> {
  const { data } = await api.get<Usuario>('/api/users/me')
  return data
}

/** Re-emite o token com papel/verificado atuais do banco (resolve token defasado pos-aprovacao). */
export async function refreshToken(): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/api/users/me/token')
  return data
}

export async function forgotPassword(email: string): Promise<{ message: string }> {
  const { data } = await api.post<{ message: string }>('/api/auth/forgot-password', { email })
  return data
}

export async function resetPassword(token: string, novaSenha: string): Promise<{ message: string }> {
  const { data } = await api.post<{ message: string }>('/api/auth/reset-password', {
    token,
    novaSenha,
  })
  return data
}

export async function gatewayHealth(): Promise<{ status: string }> {
  const { data } = await api.get<{ status: string }>('/actuator/health')
  return data
}

/** Helper para extrair mensagem padronizada do backend (`ErrorResponse.message`). */
export function extractApiError(err: unknown, fallback = 'Falha inesperada'): string {
  const e = err as { response?: { data?: { message?: string } } }
  return e?.response?.data?.message ?? fallback
}
