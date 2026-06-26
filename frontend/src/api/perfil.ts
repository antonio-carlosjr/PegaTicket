import { api } from './client'
import type { UsuarioDetalhe } from './admin'

export type AtualizarPerfilPayload = {
  nome: string
  cpf?: string
  telefone?: string
  emailContato?: string
  cep?: string
  logradouro?: string
  numero?: string
  complemento?: string
  bairro?: string
  cidade?: string
  uf?: string
  instagram?: string
  website?: string
}

/** Perfil completo do proprio usuario (inclui perfil rico, se promotor). */
export async function meuPerfil(): Promise<UsuarioDetalhe> {
  const { data } = await api.get<UsuarioDetalhe>('/api/users/me/perfil')
  return data
}

export async function atualizarPerfil(payload: AtualizarPerfilPayload): Promise<UsuarioDetalhe> {
  const { data } = await api.put<UsuarioDetalhe>('/api/users/me', payload)
  return data
}

export async function trocarSenha(senhaAtual: string, novaSenha: string): Promise<void> {
  await api.put('/api/users/me/senha', { senhaAtual, novaSenha })
}
