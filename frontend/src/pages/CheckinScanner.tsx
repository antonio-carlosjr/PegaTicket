import { useState, useRef, useEffect, useCallback } from 'react'
import { ScanLine, CheckCircle2, XCircle, Camera, CameraOff } from 'lucide-react'
import { Html5Qrcode } from 'html5-qrcode'
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

/** Mapeia falhas de acesso à câmera para mensagens claras em pt-BR. */
function mensagemErroCamera(err: unknown): string {
  const name = (err as { name?: string })?.name
  if (name === 'NotAllowedError' || name === 'SecurityError')
    return 'Permissão de câmera negada. Autorize a câmera no navegador e tente de novo, ou use a entrada manual abaixo.'
  if (name === 'NotFoundError' || name === 'OverconstrainedError')
    return 'Nenhuma câmera encontrada neste dispositivo. Use a entrada manual do código abaixo.'
  if (name === 'NotReadableError')
    return 'A câmera está em uso por outro app. Feche-o e tente novamente.'
  return 'Não foi possível iniciar a câmera. Use a entrada manual do código abaixo.'
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

const REGION_ID = 'qr-reader'

// ─── Componente ───────────────────────────────────────────────────────────────

interface CheckinScannerProps {
  /** Opcional: o back deriva o evento/ownership do proprio codigo do ingresso. */
  eventoId?: number
}

export function CheckinScanner(_props: CheckinScannerProps = {}) {
  const [codigo, setCodigo] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [resultado, setResultado] = useState<CheckinResponse | null>(null)
  const [erro, setErro] = useState<CheckinErro | null>(null)

  const [scannerAberto, setScannerAberto] = useState(false)
  const [erroCamera, setErroCamera] = useState<string | null>(null)
  const escaneandoRef = useRef(false) // trava reentrância no callback de sucesso

  // Miolo da validação — reusado pelo form manual E pela câmera.
  const validarCodigo = useCallback(async (valor: string) => {
    const codigoLimpo = valor.trim()
    if (!codigoLimpo) return

    setCarregando(true)
    setResultado(null)
    setErro(null)
    try {
      const res = await checkin(codigoLimpo)
      setResultado(res)
      setCodigo('')
    } catch (err) {
      setErro(resolverErro(err))
    } finally {
      setCarregando(false)
    }
  }, [])

  function abrirCamera() {
    setErro(null)
    setErroCamera(null)
    setScannerAberto(true) // monta o <div id="qr-reader"> antes do start
  }

  // Ciclo de vida da câmera. TODO o "parar" passa pela instância LOCAL (`inst`) e pelo
  // flag `cancelado`, para não vazar MediaStream se o cleanup rodar durante o warm-up
  // (quando stop() ainda lança porque o scanner não chegou a SCANNING). Ver CR-P0/P1.
  useEffect(() => {
    if (!scannerAberto) return
    let cancelado = false
    escaneandoRef.current = false

    const inst = new Html5Qrcode(REGION_ID, /* verbose */ false)
    const pararInstancia = async () => {
      try {
        await inst.stop() // rejeita/lança se ainda não estava escaneando — tolerado
      } catch {
        /* warm-up: ainda não em SCANNING */
      }
      try {
        inst.clear()
      } catch {
        /* noop */
      }
    }

    inst
      .start(
        { facingMode: 'environment' }, // câmera traseira preferida
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (decodedText) => {
          if (escaneandoRef.current || cancelado) return // debounce: 1 leitura só
          escaneandoRef.current = true
          setScannerAberto(false) // dispara o cleanup -> para a câmera
          void validarCodigo(decodedText)
        },
        () => {
          /* onScanFailure por frame — silencioso */
        }
      )
      .then(() => {
        // start() resolveu (câmera em SCANNING). Se o cleanup já rodou nesse meio-tempo,
        // pára agora — senão a câmera ficaria ligada sem referência para desligá-la.
        if (cancelado) void pararInstancia()
      })
      .catch((e: unknown) => {
        if (cancelado) return
        setErroCamera(mensagemErroCamera(e))
        setScannerAberto(false)
      })

    return () => {
      cancelado = true
      void pararInstancia()
    }
  }, [scannerAberto, validarCodigo])

  // Segurança extra: parar a câmera ao chegar num resultado/erro de check-in.
  useEffect(() => {
    if (resultado || erro) setScannerAberto(false)
  }, [resultado, erro])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    await validarCodigo(codigo)
  }

  function handleNovoCheckin() {
    setResultado(null)
    setErro(null)
    setErroCamera(null)
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
        <CardContent className="space-y-4">
          {/* Câmera */}
          <div className="space-y-3">
            {!scannerAberto ? (
              <Button
                type="button"
                variant="secondary"
                className="w-full"
                onClick={abrirCamera}
                disabled={carregando}
              >
                <Camera className="h-4 w-4" />
                Ler QR com a câmera
              </Button>
            ) : (
              <div className="space-y-2">
                <div
                  id={REGION_ID}
                  className="mx-auto w-full max-w-xs overflow-hidden rounded-lg border bg-muted"
                />
                <Button
                  type="button"
                  variant="outline"
                  className="w-full"
                  onClick={() => setScannerAberto(false)}
                >
                  <CameraOff className="h-4 w-4" />
                  Parar câmera
                </Button>
              </div>
            )}

            {erroCamera && (
              <p className="text-sm text-destructive" role="alert">
                {erroCamera}
              </p>
            )}

            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <span className="h-px flex-1 bg-border" /> ou digite o código{' '}
              <span className="h-px flex-1 bg-border" />
            </div>
          </div>

          {/* Entrada manual (fallback sempre disponível) */}
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
            <XCircle className="h-12 w-12 text-destructive" aria-hidden="true" />
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
