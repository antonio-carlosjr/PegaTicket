import { api } from './client'
import { Papel } from './auth'

export type PerfilVerificado = {
  telefone: string
  cpf: string
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
  status: string
  motivoRejeicao?: string
}

export type UsuarioAdmin = {
  id: number
  nome: string
  email: string
  papel: Papel
  verificado: boolean
  ativo: boolean
  criadoEm: string
}

export type UsuarioDetalhe = UsuarioAdmin & {
  perfil?: PerfilVerificado
}

export async function listarUsuarios(): Promise<UsuarioAdmin[]> {
  const { data } = await api.get<{ content: UsuarioAdmin[] }>('/api/users')
  return data.content
}

export async function detalharUsuario(id: number): Promise<UsuarioDetalhe> {
  const { data } = await api.get<UsuarioDetalhe>(`/api/users/${id}`)
  return data
}

export async function aprovarPromotor(id: number): Promise<void> {
  await api.put(`/api/users/${id}/aprovar`)
}

export async function rejeitarPromotor(id: number, motivo: string): Promise<void> {
  await api.put(`/api/users/${id}/rejeitar`, { motivo })
}

export async function ativarUsuario(id: number): Promise<void> {
  await api.put(`/api/users/${id}/ativar`)
}

export async function inativarUsuario(id: number): Promise<void> {
  await api.put(`/api/users/${id}/inativar`)
}
