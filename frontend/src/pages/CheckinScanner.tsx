import { useState } from 'react'
import { ScanLine, CheckCircle2, XCircle } from 'lucide-react'
import { checkin, type CheckinResponse } from '@/api/tickets'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

// ─── Helpers ──────────────────────────────────────────────────────────────────

type CheckinErro = {
  tipo: 'JA_UTILIZADO' | 'EVENTO_ALHEIO' | 'NAO_ENCONTRADO' | 'DESCONHECIDO'
  mensagem: string
}

function resolverErro(err: unknown): CheckinErro {
  const status = (err as { response?: { status?: number } })?.response?.status
  const code = (err as { response?: { data?: { message?: string } } })?.response?.data?.message

  if (status === 409 || code === 'INGRESSO_JA_UTILIZADO') {
    return { tipo: 'JA_UTILIZADO', mensagem: 'INGRESSO_JA_UTILIZADO — este ingresso ja utilizado e nao pode ser validado novamente.' }
  }
  if (status === 403 && code === 'CHECKIN_EVENTO_ALHEIO') {
    return { tipo: 'EVENTO_ALHEIO', mensagem: 'CHECKIN_EVENTO_ALHEIO — este evento nao e seu e voce nao pode fazer check-in.' }
  }
  if (status === 403) {
    return { tipo: 'EVENTO_ALHEIO', mensagem: 'Acesso negado — apenas promotores podem fazer check-in.' }
  }
  if (status === 404 || code === 'INGRESSO_NAO_ENCONTRADO') {
    return { tipo: 'NAO_ENCONTRADO', mensagem: 'INGRESSO_NAO_ENCONTRADO — codigo invalido, cancelado ou inexistente.' }
  }
  return { tipo: 'DESCONHECIDO', mensagem: 'Nao foi possivel validar o ingresso. Tente novamente.' }
}

function formatarDataHora(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short',
  })
}

// ─── Componente ───────────────────────────────────────────────────────────────

interface CheckinScannerProps {
  eventoId: number
}

export function CheckinScanner({ eventoId: _eventoId }: CheckinScannerProps) {
  const [codigo, setCodigo] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [resultado, setResultado] = useState<CheckinResponse | null>(null)
  const [erro, setErro] = useState<CheckinErro | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!codigo.trim()) return

    setCarregando(true)
    setResultado(null)
    setErro(null)

    try {
      const res = await checkin(codigo.trim())
      setResultado(res)
      setCodigo('')
    } catch (err) {
      setErro(resolverErro(err))
    } finally {
      setCarregando(false)
    }
  }

  function handleNovoCheckin() {
    setResultado(null)
    setErro(null)
    setCodigo('')
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <div className="flex items-center gap-2">
        <ScanLine className="h-6 w-6 text-primary" aria-hidden="true" />
        <h1 className="text-2xl font-bold tracking-tight">Scanner de Check-in</h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Validar ingresso</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <label htmlFor="codigo-unico" className="text-sm font-medium">
                Código do ingresso
              </label>
              <Input
                id="codigo-unico"
                placeholder="codigo UUID do ingresso"
                value={codigo}
                onChange={(e) => setCodigo(e.target.value)}
                disabled={carregando}
                autoComplete="off"
                aria-label="Código do ingresso"
              />
            </div>

            <Button
              type="submit"
              className="w-full"
              loading={carregando}
              disabled={carregando || !codigo.trim()}
            >
              <ScanLine className="h-4 w-4" />
              Validar ingresso
            </Button>
          </form>
        </CardContent>
      </Card>

      {/* Resultado: sucesso */}
      {resultado && (
        <Card className="border-green-500/40 bg-green-50 dark:bg-green-950/20">
          <CardContent className="flex flex-col items-center gap-3 py-6 text-center">
            <CheckCircle2
              className="h-12 w-12 text-green-600 dark:text-green-400"
              aria-hidden="true"
            />
            <div>
              <p className="text-lg font-semibold text-green-700 dark:text-green-300">
                Check-in OK — ingresso{' '}
                <span className="uppercase">{resultado.status}</span>
              </p>
              <p className="mt-1 text-sm text-muted-foreground">
                Realizado em{' '}
                <strong>{formatarDataHora(resultado.realizadoEm)}</strong>
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Ingresso #{resultado.ingressoId} · Inscrição #{resultado.inscricaoId}
              </p>
            </div>
            <Button variant="outline" size="sm" onClick={handleNovoCheckin}>
              Validar outro ingresso
            </Button>
          </CardContent>
        </Card>
      )}

      {/* Resultado: erro */}
      {erro && (
        <Card className="border-destructive/40 bg-destructive/5">
          <CardContent className="flex flex-col items-center gap-3 py-6 text-center">
            <XCircle
              className="h-12 w-12 text-destructive"
              aria-hidden="true"
            />
            <p className="font-medium text-destructive">{erro.mensagem}</p>
            <Button variant="outline" size="sm" onClick={handleNovoCheckin}>
              Tentar outro código
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
