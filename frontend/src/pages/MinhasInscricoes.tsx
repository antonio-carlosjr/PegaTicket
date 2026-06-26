import { useCallback, useEffect, useState } from 'react'
import { Calendar, ClipboardList } from 'lucide-react'
import { historicoInscricoes, type InscricaoHistoricoResponse } from '@/api/tickets'
import { extractApiError } from '@/api/auth'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PageLoader } from '@/components/ui/spinner'
import { toast } from '@/components/ui/toaster'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatarDataHora(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function badgeStatusInscricao(status: string) {
  switch (status) {
    case 'ATIVA':
      return <Badge variant="success">Ativa</Badge>
    case 'CANCELADA':
      return <Badge variant="destructive">Cancelada</Badge>
    default:
      return <Badge variant="outline">{status}</Badge>
  }
}

// ─── Componente ───────────────────────────────────────────────────────────────

export function MinhasInscricoes() {
  const [inscricoes, setInscricoes] = useState<InscricaoHistoricoResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  const carregar = useCallback(async (p: number) => {
    setLoading(true)
    setErro(null)
    try {
      const resp = await historicoInscricoes(p)
      setInscricoes(resp.content)
      setTotalPages(resp.totalPages)
      setTotalElements(resp.totalElements)
      setPage(resp.number)
    } catch (e) {
      const msg = extractApiError(e, 'Não foi possível carregar o histórico de inscrições.')
      setErro(msg)
      toast.error('Erro ao carregar histórico', { description: msg })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void carregar(0)
  }, [carregar])

  if (loading) return <PageLoader label="Carregando histórico de inscrições..." />

  return (
    <div className="space-y-6">
      {/* Cabeçalho */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Minhas inscrições</h1>
        <p className="mt-1 text-muted-foreground">
          Histórico de todas as suas inscrições em eventos.
        </p>
      </div>

      {/* Estado de erro */}
      {erro && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 p-6 text-center">
          <p className="font-medium text-destructive">{erro}</p>
          <Button variant="outline" className="mt-4" onClick={() => void carregar(page)}>
            Tentar novamente
          </Button>
        </div>
      )}

      {/* Estado vazio */}
      {!erro && inscricoes.length === 0 && (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed py-20 text-center">
          <ClipboardList className="h-14 w-14 text-muted-foreground/40" aria-hidden="true" />
          <p className="mt-4 text-lg font-medium text-muted-foreground">
            Nenhuma inscrição encontrada
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            Você ainda não se inscreveu em nenhum evento.
          </p>
        </div>
      )}

      {/* Lista de inscrições */}
      {!erro && inscricoes.length > 0 && (
        <>
          <p className="text-sm text-muted-foreground">
            {totalElements} inscrição{totalElements !== 1 ? 'ões' : ''} no total
          </p>

          <div className="space-y-3">
            {inscricoes.map((ins) => (
              <InscricaoItem key={ins.id} inscricao={ins} />
            ))}
          </div>

          {/* Paginação */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-4">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0 || loading}
                onClick={() => void carregar(page - 1)}
                aria-label="Página anterior"
              >
                Anterior
              </Button>
              <span className="text-sm text-muted-foreground">
                Página {page + 1} de {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= totalPages - 1 || loading}
                onClick={() => void carregar(page + 1)}
                aria-label="Próxima página"
              >
                Próxima
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

// ─── Item individual ──────────────────────────────────────────────────────────

function InscricaoItem({ inscricao }: { inscricao: InscricaoHistoricoResponse }) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between gap-4 p-4">
        <div className="flex items-start gap-3">
          <ClipboardList className="mt-0.5 h-5 w-5 flex-shrink-0 text-primary" aria-hidden="true" />
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">
              Inscrição #{inscricao.id}
            </p>
            <p className="text-xs text-muted-foreground">
              Evento #{inscricao.eventoId}
            </p>
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Calendar className="h-3.5 w-3.5" />
              <span>{formatarDataHora(inscricao.inscritoEm)}</span>
            </div>
          </div>
        </div>
        <div className="flex-shrink-0">
          {badgeStatusInscricao(inscricao.status)}
        </div>
      </CardContent>
    </Card>
  )
}
