import { useEffect, useState } from 'react'
import {
  listarUsuarios,
  detalharUsuario,
  aprovarPromotor,
  rejeitarPromotor,
  ativarUsuario,
  inativarUsuario,
  UsuarioAdmin,
  UsuarioDetalhe
} from '@/api/admin'
import { toast } from '@/components/ui/toaster'
import { extractApiError } from '@/api/auth'
import { Button } from '@/components/ui/button'

export function AdminUsuarios() {
  const [usuarios, setUsuarios] = useState<UsuarioAdmin[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedUser, setSelectedUser] = useState<UsuarioDetalhe | null>(null)
  const [motivoRejeicao, setMotivoRejeicao] = useState('')

  async function carregarUsuarios() {
    setLoading(true)
    try {
      const data = await listarUsuarios()
      setUsuarios(data)
    } catch (e) {
      toast.error('Erro ao listar usuarios', { description: extractApiError(e) })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    carregarUsuarios()
  }, [])

  async function handleAction(action: 'ativar' | 'inativar', id: number) {
    try {
      if (action === 'ativar') await ativarUsuario(id)
      else await inativarUsuario(id)
      toast.success(`Usuário ${action === 'ativar' ? 'ativado' : 'inativado'}!`)
      carregarUsuarios()
    } catch (e) {
      toast.error('Erro', { description: extractApiError(e) })
    }
  }

  async function abrirDetalhes(id: number) {
    try {
      const data = await detalharUsuario(id)
      setSelectedUser(data)
      setMotivoRejeicao('')
    } catch (e) {
      toast.error('Erro ao detalhar', { description: extractApiError(e) })
    }
  }

  async function handleAprovar() {
    if (!selectedUser) return
    try {
      await aprovarPromotor(selectedUser.id)
      toast.success('Promotor aprovado com sucesso!')
      setSelectedUser(null)
      carregarUsuarios()
    } catch (e) {
      toast.error('Erro', { description: extractApiError(e) })
    }
  }

  async function handleRejeitar() {
    if (!selectedUser || !motivoRejeicao.trim()) {
      toast.error('Motivo da rejeição é obrigatório.')
      return
    }
    try {
      await rejeitarPromotor(selectedUser.id, motivoRejeicao)
      toast.success('Promotor rejeitado.')
      setSelectedUser(null)
      carregarUsuarios()
    } catch (e) {
      toast.error('Erro', { description: extractApiError(e) })
    }
  }

  if (loading) return <div className="p-8">Carregando...</div>

  return (
    <div className="p-8 space-y-6">
      <h1 className="text-2xl font-bold">Gerenciamento de Usuários</h1>

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="w-full text-sm text-left text-gray-500">
          <thead className="text-xs text-gray-700 uppercase bg-gray-50">
            <tr>
              <th className="px-6 py-3">ID</th>
              <th className="px-6 py-3">Nome</th>
              <th className="px-6 py-3">Email</th>
              <th className="px-6 py-3">Papel</th>
              <th className="px-6 py-3">Status</th>
              <th className="px-6 py-3">Ações</th>
            </tr>
          </thead>
          <tbody>
            {usuarios.map(u => (
              <tr key={u.id} className="bg-white border-b hover:bg-gray-50">
                <td className="px-6 py-4">{u.id}</td>
                <td className="px-6 py-4">{u.nome}</td>
                <td className="px-6 py-4">{u.email}</td>
                <td className="px-6 py-4">{u.papel}</td>
                <td className="px-6 py-4">
                  <div className="flex flex-col items-start gap-1">
                    <span className={`px-2 py-1 rounded text-xs ${u.ativo ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                      {u.ativo ? 'Ativo' : 'Inativo'}
                    </span>
                    {u.statusPerfil === 'PENDENTE' && (
                      <span className="px-2 py-1 rounded text-xs bg-amber-100 text-amber-800 font-medium">Promotor pendente</span>
                    )}
                    {u.statusPerfil === 'VERIFICADO' && (
                      <span className="px-2 py-1 rounded text-xs bg-blue-100 text-blue-800">Verificado</span>
                    )}
                    {u.statusPerfil === 'REJEITADO' && (
                      <span className="px-2 py-1 rounded text-xs bg-red-100 text-red-700">Rejeitado</span>
                    )}
                  </div>
                </td>
                <td className="px-6 py-4 flex gap-2">
                  <Button variant="outline" size="sm" onClick={() => abrirDetalhes(u.id)}>Detalhes</Button>
                  {u.ativo ? (
                    <Button variant="destructive" size="sm" onClick={() => handleAction('inativar', u.id)}>Inativar</Button>
                  ) : (
                    <Button variant="default" size="sm" onClick={() => handleAction('ativar', u.id)}>Ativar</Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {selectedUser && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg p-6 max-w-2xl w-full max-h-[90vh] overflow-y-auto space-y-4">
            <div className="flex justify-between items-start">
              <h2 className="text-xl font-bold">Detalhes do Usuário</h2>
              <button onClick={() => setSelectedUser(null)} className="text-gray-500 hover:text-black">&times;</button>
            </div>
            
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div><strong>ID:</strong> {selectedUser.id}</div>
              <div><strong>Nome:</strong> {selectedUser.nome}</div>
              <div><strong>Email:</strong> {selectedUser.email}</div>
              <div><strong>Papel:</strong> {selectedUser.papel}</div>
            </div>

            {selectedUser.perfil && (
              <div className="mt-6 border-t pt-4 space-y-4">
                <h3 className="font-semibold text-lg">Perfil de Promotor ({selectedUser.perfil.status})</h3>
                <div className="grid grid-cols-2 gap-4 text-sm bg-gray-50 p-4 rounded">
                  <div><strong>CPF:</strong> {selectedUser.perfil.cpf}</div>
                  <div><strong>Telefone:</strong> {selectedUser.perfil.telefone}</div>
                  <div><strong>Contato:</strong> {selectedUser.perfil.emailContato || '-'}</div>
                  <div><strong>CEP:</strong> {selectedUser.perfil.cep || '-'}</div>
                  <div className="col-span-2"><strong>Endereço:</strong> {selectedUser.perfil.logradouro}, {selectedUser.perfil.numero} - {selectedUser.perfil.bairro}, {selectedUser.perfil.cidade}/{selectedUser.perfil.uf}</div>
                  <div><strong>Instagram:</strong> {selectedUser.perfil.instagram || '-'}</div>
                  <div><strong>Website:</strong> {selectedUser.perfil.website || '-'}</div>
                </div>

                {selectedUser.perfil.status === 'PENDENTE' && (
                  <div className="space-y-4 border-t pt-4">
                    <h4 className="font-semibold">Análise de Aprovação</h4>
                    <div className="flex flex-col gap-2">
                      <label className="text-sm font-medium">Motivo da Rejeição (se aplicável):</label>
                      <textarea 
                        className="border rounded p-2 text-sm w-full" 
                        rows={3} 
                        value={motivoRejeicao}
                        onChange={(e) => setMotivoRejeicao(e.target.value)}
                        placeholder="Explique por que o perfil está sendo rejeitado..."
                      />
                    </div>
                    <div className="flex gap-2">
                      <Button variant="default" className="bg-green-600 hover:bg-green-700" onClick={handleAprovar}>Aprovar Promotor</Button>
                      <Button variant="destructive" onClick={handleRejeitar}>Rejeitar Promotor</Button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
