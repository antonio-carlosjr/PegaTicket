import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowLeft, ArrowRight, Check } from 'lucide-react'
import {
  criarEvento,
  editarEvento,
  detalheEvento,
  type EventoCreatePayload,
} from '@/api/events'
import { extractApiError } from '@/api/auth'
import { eventoSchema, type EventoFormValues } from '@/lib/validation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { FormField } from '@/components/ui/form-field'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PageLoader } from '@/components/ui/spinner'
import { toast } from '@/components/ui/toaster'

// ─── Tipos ───────────────────────────────────────────────────────────────────

type FieldName = keyof EventoFormValues

// ─── Wizard steps ─────────────────────────────────────────────────────────────

interface Step {
  titulo: string
  campos: FieldName[]
}

const STEPS: Step[] = [
  {
    titulo: 'Dados gerais',
    campos: ['titulo', 'descricao'],
  },
  {
    titulo: 'Data e local',
    campos: ['dataInicio', 'dataFim', 'local'],
  },
  {
    titulo: 'Tipo e capacidade',
    campos: ['tipo', 'capacidade', 'preco', 'prazoReembolsoDias', 'imagemUrl'],
  },
]

// ─── Helper: converte datetime-local para ISO offset ─────────────────────────

/**
 * `<input type="datetime-local">` retorna "2026-06-20T14:00" (sem timezone).
 * Converter para OffsetDateTime exato do fuso do usuário.
 */
function localParaIso(local: string): string {
  if (!local) return local
  // new Date interpreta como local time se não tiver timezone
  return new Date(local).toISOString()
}

/** Converte ISO para o formato do datetime-local input "YYYY-MM-DDTHH:mm" */
function isoParaLocal(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ─── Componente ───────────────────────────────────────────────────────────────

export function CriarEditarEvento() {
  const { id } = useParams<{ id?: string }>()
  const isEdicao = !!id
  const navigate = useNavigate()

  const [etapa, setEtapa] = useState(0)
  const [carregandoEvento, setCarregandoEvento] = useState(isEdicao)

  const {
    register,
    handleSubmit,
    trigger,
    watch,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<EventoFormValues>({
    resolver: zodResolver(eventoSchema),
    defaultValues: {
      titulo: '',
      descricao: '',
      dataInicio: '',
      dataFim: '',
      local: '',
      tipo: 'GRATUITO',
      capacidade: 1,
      preco: '',
      prazoReembolsoDias: undefined,
      imagemUrl: '',
    },
    mode: 'onTouched',
  })

  const tipoWatched = watch('tipo')

  // ─── Carregar dados do evento no modo edição ─────────────────────────────
  useEffect(() => {
    if (!isEdicao || !id) return

    let cancelled = false
    setCarregandoEvento(true)

    detalheEvento(Number(id))
      .then((ev) => {
        if (cancelled) return
        // Só RASCUNHO é editável; se chegou aqui mas não é RASCUNHO, o backend
        // retornará 409 no PUT — mas a PromotorRoute já protege a rota.
        setValue('titulo', ev.titulo)
        setValue('descricao', ev.descricao ?? '')
        setValue('dataInicio', isoParaLocal(ev.dataInicio))
        setValue('dataFim', isoParaLocal(ev.dataFim))
        setValue('local', ev.local)
        setValue('tipo', ev.tipo)
        setValue('capacidade', ev.capacidade)
        setValue('preco', ev.preco ?? '')
        setValue('prazoReembolsoDias', ev.prazoReembolsoDias ?? undefined)
        setValue('imagemUrl', ev.imagemUrl ?? '')
      })
      .catch((e) => {
        if (cancelled) return
        toast.error('Nao foi possivel carregar o evento.', {
          description: extractApiError(e),
        })
        navigate('/meus-eventos', { replace: true })
      })
      .finally(() => {
        if (!cancelled) setCarregandoEvento(false)
      })

    return () => { cancelled = true }
  }, [id, isEdicao, navigate, setValue])

  // ─── Navegação entre etapas ───────────────────────────────────────────────
  async function avancar() {
    const campos = STEPS[etapa].campos
    const ok = await trigger(campos)
    if (ok) setEtapa((prev) => prev + 1)
  }

  function voltar() {
    setEtapa((prev) => prev - 1)
  }

  // ─── Submissão final ─────────────────────────────────────────────────────
  async function onSubmit(values: EventoFormValues) {
    try {
      const payload: EventoCreatePayload = {
        titulo: values.titulo,
        descricao: values.descricao || null,
        dataInicio: localParaIso(values.dataInicio),
        dataFim: localParaIso(values.dataFim),
        local: values.local,
        tipo: values.tipo,
        capacidade: values.capacidade,
        preco: values.tipo === 'PAGO' ? (values.preco || null) : null,
        prazoReembolsoDias:
          values.tipo === 'PAGO' ? (values.prazoReembolsoDias ?? null) : null,
        imagemUrl: values.imagemUrl || null,
      }

      if (isEdicao) {
        await editarEvento(Number(id), payload)
        toast.success('Evento atualizado!')
      } else {
        await criarEvento(payload)
        toast.success('Evento criado como rascunho!')
      }
      navigate('/meus-eventos')
    } catch (e) {
      const status = (e as { response?: { status?: number } })?.response?.status
      const msg = extractApiError(e, 'Nao foi possivel salvar o evento.')

      if (status === 400) {
        // Tenta mapear erros de campo do Bean Validation
        // Formato do back: "titulo: nao deve estar em branco; capacidade: deve ser >= 1"
        const partes = msg.split(';')
        let mapeou = false
        for (const parte of partes) {
          const [campo, ...resto] = parte.trim().split(':')
          const nomeCampo = campo.trim() as FieldName
          const validFields: FieldName[] = [
            'titulo', 'descricao', 'dataInicio', 'dataFim',
            'local', 'tipo', 'capacidade', 'preco', 'prazoReembolsoDias', 'imagemUrl',
          ]
          if (validFields.includes(nomeCampo)) {
            setError(nomeCampo, { message: resto.join(':').trim() })
            mapeou = true
          }
        }
        if (mapeou) {
          toast.error('Verifique os campos do formulario.')
        } else {
          toast.error('Dados invalidos', { description: msg })
        }
      } else if (status === 409) {
        toast.error('Operacao nao permitida', { description: msg })
      } else {
        toast.error('Erro ao salvar evento', { description: msg })
      }
    }
  }

  if (carregandoEvento) return <PageLoader label="Carregando evento..." />

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      {/* Cabeçalho */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">
          {isEdicao ? 'Editar evento' : 'Criar evento'}
        </h1>
        <p className="mt-1 text-muted-foreground">
          {isEdicao
            ? 'Atualize as informacoes do evento (apenas rascunhos).'
            : 'Preencha as informacoes em ate 3 etapas.'}
        </p>
      </div>

      {/* Indicador de etapas */}
      <StepIndicator etapaAtual={etapa} total={STEPS.length} />

      {/* Formulário */}
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">
              Etapa {etapa + 1} — {STEPS[etapa].titulo}
            </CardTitle>
          </CardHeader>

          <CardContent className="space-y-5">
            {/* ── Etapa 1: Dados gerais ─────────────────────────────────── */}
            {etapa === 0 && (
              <>
                <FormField label="Título" htmlFor="titulo" error={errors.titulo} required>
                  <Input
                    id="titulo"
                    placeholder="Nome do evento"
                    invalid={!!errors.titulo}
                    {...register('titulo')}
                  />
                </FormField>

                <FormField
                  label="Descrição"
                  htmlFor="descricao"
                  error={errors.descricao}
                  hint="Opcional. Ate 5000 caracteres."
                >
                  <textarea
                    id="descricao"
                    rows={5}
                    placeholder="Descreva o evento, atrações, informações importantes..."
                    className="flex w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 resize-y"
                    aria-invalid={!!errors.descricao}
                    {...register('descricao')}
                  />
                </FormField>
              </>
            )}

            {/* ── Etapa 2: Data e local ─────────────────────────────────── */}
            {etapa === 1 && (
              <>
                <FormField
                  label="Data e hora de início"
                  htmlFor="dataInicio"
                  error={errors.dataInicio}
                  required
                >
                  <Input
                    id="dataInicio"
                    type="datetime-local"
                    invalid={!!errors.dataInicio}
                    {...register('dataInicio')}
                  />
                </FormField>

                <FormField
                  label="Data e hora de término"
                  htmlFor="dataFim"
                  error={errors.dataFim}
                  required
                >
                  <Input
                    id="dataFim"
                    type="datetime-local"
                    invalid={!!errors.dataFim}
                    {...register('dataFim')}
                  />
                </FormField>

                <FormField label="Local" htmlFor="local" error={errors.local} required>
                  <Input
                    id="local"
                    placeholder="Nome do local, endereço, cidade..."
                    invalid={!!errors.local}
                    {...register('local')}
                  />
                </FormField>
              </>
            )}

            {/* ── Etapa 3: Tipo/preço/capacidade/imagem ─────────────────── */}
            {etapa === 2 && (
              <>
                <FormField label="Tipo do evento" htmlFor="tipo" error={errors.tipo} required>
                  <select
                    id="tipo"
                    className="flex h-11 w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                    aria-invalid={!!errors.tipo}
                    {...register('tipo')}
                  >
                    <option value="GRATUITO">Gratuito</option>
                    <option value="PAGO">Pago</option>
                  </select>
                </FormField>

                <FormField
                  label="Capacidade"
                  htmlFor="capacidade"
                  error={errors.capacidade}
                  hint="Numero maximo de participantes."
                  required
                >
                  <Input
                    id="capacidade"
                    type="number"
                    min={1}
                    step={1}
                    placeholder="Ex: 200"
                    invalid={!!errors.capacidade}
                    {...register('capacidade')}
                  />
                </FormField>

                {tipoWatched === 'PAGO' && (
                  <>
                    <FormField
                      label="Preço (R$)"
                      htmlFor="preco"
                      error={errors.preco}
                      required
                    >
                      <Input
                        id="preco"
                        type="number"
                        min={0.01}
                        step={0.01}
                        placeholder="Ex: 50.00"
                        invalid={!!errors.preco}
                        {...register('preco')}
                      />
                    </FormField>

                    <FormField
                      label="Prazo de reembolso (dias)"
                      htmlFor="prazoReembolsoDias"
                      error={errors.prazoReembolsoDias}
                      hint="Numero de dias antes do evento para solicitar reembolso."
                      required
                    >
                      <Input
                        id="prazoReembolsoDias"
                        type="number"
                        min={0}
                        step={1}
                        placeholder="Ex: 7"
                        invalid={!!errors.prazoReembolsoDias}
                        {...register('prazoReembolsoDias')}
                      />
                    </FormField>
                  </>
                )}

                <FormField
                  label="URL da imagem"
                  htmlFor="imagemUrl"
                  error={errors.imagemUrl}
                  hint="Opcional. Link para a imagem de divulgacao do evento."
                >
                  <Input
                    id="imagemUrl"
                    type="url"
                    placeholder="https://exemplo.com/imagem.jpg"
                    invalid={!!errors.imagemUrl}
                    {...register('imagemUrl')}
                  />
                </FormField>
              </>
            )}
          </CardContent>
        </Card>

        {/* Controles do wizard */}
        <div className="mt-4 flex justify-between gap-3">
          {/* Botão Voltar */}
          {etapa > 0 ? (
            <Button type="button" variant="outline" onClick={voltar}>
              <ArrowLeft className="h-4 w-4" />
              Voltar
            </Button>
          ) : (
            <Button
              type="button"
              variant="ghost"
              onClick={() => navigate('/meus-eventos')}
            >
              <ArrowLeft className="h-4 w-4" />
              Cancelar
            </Button>
          )}

          {/* Botão Avançar / Salvar */}
          {etapa < STEPS.length - 1 ? (
            <Button type="button" onClick={avancar}>
              Próximo
              <ArrowRight className="h-4 w-4" />
            </Button>
          ) : (
            <Button type="submit" loading={isSubmitting}>
              <Check className="h-4 w-4" />
              {isEdicao ? 'Salvar alterações' : 'Criar rascunho'}
            </Button>
          )}
        </div>
      </form>
    </div>
  )
}

// ─── Indicador de etapas ──────────────────────────────────────────────────────

function StepIndicator({ etapaAtual, total }: { etapaAtual: number; total: number }) {
  return (
    <div className="flex items-center gap-2" role="list" aria-label="Etapas do formulário">
      {Array.from({ length: total }).map((_, i) => (
        <div key={i} className="flex flex-1 items-center gap-2" role="listitem">
          <div
            className={`flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full text-xs font-bold transition-colors ${
              i < etapaAtual
                ? 'bg-primary text-primary-foreground'
                : i === etapaAtual
                  ? 'bg-primary text-primary-foreground ring-2 ring-primary ring-offset-2'
                  : 'bg-muted text-muted-foreground'
            }`}
            aria-current={i === etapaAtual ? 'step' : undefined}
          >
            {i < etapaAtual ? <Check className="h-3.5 w-3.5" /> : i + 1}
          </div>
          {i < total - 1 && (
            <div
              className={`h-0.5 flex-1 transition-colors ${
                i < etapaAtual ? 'bg-primary' : 'bg-muted'
              }`}
            />
          )}
        </div>
      ))}
    </div>
  )
}
