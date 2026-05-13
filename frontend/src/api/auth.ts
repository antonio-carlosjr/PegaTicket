import { api } from './client'

export type Usuario = {
  id: number
  nome: string
  email: string
  verificado: boolean
  criadoEm: string
}

export type LoginResponse = {
  token: string
  tokenType: string
  expiresInMs: number
  userId: number
  email: string
  verificado: boolean
}

export async function register(nome: string, email: string, senha: string): Promise<Usuario> {
  const { data } = await api.post<Usuario>('/api/auth/register', { nome, email, senha })
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

export async function gatewayHealth(): Promise<{ status: string }> {
  const { data } = await api.get<{ status: string }>('/actuator/health')
  return data
}
