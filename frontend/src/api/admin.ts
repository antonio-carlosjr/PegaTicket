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
  /** Status do perfil de promotor: PENDENTE | VERIFICADO | REJEITADO | null (nao tem perfil). */
  statusPerfil?: string | null
}

export type UsuarioDetalhe = UsuarioAdmin & {
  perfil?: PerfilVerificado
}

export async function listarUsuarios(): Promise<UsuarioAdmin[]> {
  // A tela de admin nao pagina na UI: busca todos de uma vez (size alto cobre a base
  // atual). O default do backend e 20 — sem isto, usuarios alem do 20o sumiam da lista
  // e da contagem de pendentes na home.
  const { data } = await api.get<{ content: UsuarioAdmin[] }>('/api/users', { params: { size: 1000 } })
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
