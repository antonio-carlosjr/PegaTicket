import { useEffect, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { IdCard, Lock, Mail, MapPin, Phone, Save, User } from 'lucide-react'
import { meuPerfil, atualizarPerfil, trocarSenha, type AtualizarPerfilPayload } from '@/api/perfil'
import type { UsuarioDetalhe } from '@/api/admin'
import { extractApiError } from '@/api/auth'
import { consultarCep } from '@/lib/viacep'
import { useAuth } from '@/hooks/useAuth'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { MaskedInput } from '@/components/ui/masked-input'
import { PasswordInput } from '@/components/ui/password-input'
import { FormField } from '@/components/ui/form-field'
import { PageLoader } from '@/components/ui/spinner'
import { toast } from '@/components/ui/toaster'

function badgePapel(papel: string, verificado: boolean) {
  if (papel === 'ADMIN') return <Badge variant="destructive">Admin</Badge>
  if (papel === 'PROMOTOR')
    return verificado
      ? <Badge variant="success">Promotor verificado</Badge>
      : <Badge variant="warning">Promotor (pendente)</Badge>
  return <Badge variant="secondary">Participante</Badge>
}

export function Perfil() {
  const [perfil, setPerfil] = useState<UsuarioDetalhe | null>(null)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  function carregar() {
    setLoading(true)
    setErro(null)
    meuPerfil()
      .then(setPerfil)
      .catch((e) => setErro(extractApiError(e, 'Nao foi possivel carregar seu perfil.')))
      .finally(() => setLoading(false))
  }
  useEffect(() => { carregar() }, [])

  if (loading) return <PageLoader label="Carregando seu perfil..." />
  if (erro) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <p className="text-lg font-medium text-destructive">{erro}</p>
        <Button variant="outline" className="mt-6" onClick={carregar}>Tentar novamente</Button>
      </div>
    )
  }
  if (!perfil) return null

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Meu perfil</h1>
        <p className="mt-1 text-muted-foreground">Gerencie seus dados cadastrais e sua senha.</p>
      </div>
      <DadosForm perfil={perfil} onSaved={setPerfil} />
      <SenhaForm />
    </div>
  )
}

// ─── Dados pessoais ─────────────────────────────────────────────────────────────

function DadosForm({ perfil, onSaved }: { perfil: UsuarioDetalhe; onSaved: (p: UsuarioDetalhe) => void }) {
  const { refresh } = useAuth()
  const ehPromotor = perfil.papel === 'PROMOTOR'
  const p = perfil.perfil

  const { register, handleSubmit, control, setValue, formState: { errors, isSubmitting } } =
    useForm<AtualizarPerfilPayload>({
      defaultValues: {
        nome: perfil.nome,
        cpf: p?.cpf ?? '', telefone: p?.telefone ?? '', emailContato: p?.emailContato ?? '',
        cep: p?.cep ?? '', logradouro: p?.logradouro ?? '', numero: p?.numero ?? '',
        complemento: p?.complemento ?? '', bairro: p?.bairro ?? '', cidade: p?.cidade ?? '',
        uf: p?.uf ?? '', instagram: p?.instagram ?? '', website: p?.website ?? '',
      },
    })

  async function handleCep(valor: string) {
    if (valor.replace(/\D/g, '').length !== 8) return
    const end = await consultarCep(valor)
    if (!end) return
    setValue('logradouro', end.logradouro)
    setValue('bairro', end.bairro)
    setValue('cidade', end.localidade)
    setValue('uf', end.uf)
    if (end.complemento) setValue('complemento', end.complemento)
  }

  async function onSubmit(values: AtualizarPerfilPayload) {
    try {
      const atualizado = await atualizarPerfil(values)
      toast.success('Perfil atualizado!')
      onSaved(atualizado)
      void refresh() // reflete o nome novo no cabecalho
    } catch (e) {
      toast.error('Nao foi possivel salvar', { description: extractApiError(e) })
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Dados pessoais</CardTitle>
        <CardDescription className="flex items-center gap-2">
          {badgePapel(perfil.papel, perfil.verificado)}
          {ehPromotor && <span>Complete os dados do seu perfil de promotor.</span>}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
          <FormField label="Nome completo" htmlFor="nome" error={errors.nome} required>
            <Input id="nome" leftIcon={<User className="h-4 w-4" />} invalid={!!errors.nome}
              {...register('nome', { required: 'Informe o nome', minLength: { value: 2, message: 'Minimo 2 caracteres' } })} />
          </FormField>

          <FormField label="E-mail" htmlFor="email" hint="O e-mail de login nao pode ser alterado.">
            <Input id="email" value={perfil.email} readOnly leftIcon={<Mail className="h-4 w-4" />}
              className="bg-muted text-muted-foreground" />
          </FormField>

          {ehPromotor && (
            <>
              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
                <FormField label="CPF" htmlFor="cpf" error={errors.cpf}>
                  <Controller name="cpf" control={control} render={({ field }) => (
                    <MaskedInput id="cpf" mask="000.000.000-00" placeholder="000.000.000-00"
                      leftIcon={<IdCard className="h-4 w-4" />} invalid={!!errors.cpf}
                      value={field.value || ''} onValueChange={field.onChange} onBlur={field.onBlur} name={field.name} />
                  )} />
                </FormField>
                <FormField label="Telefone" htmlFor="tel" error={errors.telefone}>
                  <Controller name="telefone" control={control} render={({ field }) => (
                    <MaskedInput id="tel" mask="(00) 00000-0000" placeholder="(11) 99999-0000"
                      leftIcon={<Phone className="h-4 w-4" />} invalid={!!errors.telefone}
                      value={field.value || ''} onValueChange={field.onChange} onBlur={field.onBlur} name={field.name} />
                  )} />
                </FormField>
              </div>

              <FormField label="E-mail de contato" htmlFor="emailContato" error={errors.emailContato}>
                <Input id="emailContato" type="email" placeholder="contato@empresa.com" {...register('emailContato')} />
              </FormField>

              <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
                <FormField label="CEP" htmlFor="cep" error={errors.cep} hint="Preenche o endereco automaticamente.">
                  <Controller name="cep" control={control} render={({ field }) => (
                    <MaskedInput id="cep" mask="00000-000" placeholder="00000-000"
                      leftIcon={<MapPin className="h-4 w-4" />} invalid={!!errors.cep}
                      value={field.value || ''} onValueChange={(v) => { field.onChange(v); void handleCep(v) }}
                      onBlur={field.onBlur} name={field.name} />
                  )} />
                </FormField>
                <div className="sm:col-span-2">
                  <FormField label="Logradouro" htmlFor="logradouro" error={errors.logradouro}>
                    <Input id="logradouro" placeholder="Rua, Avenida..." {...register('logradouro')} />
                  </FormField>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
                <FormField label="Numero" htmlFor="numero" error={errors.numero}>
                  <Input id="numero" placeholder="123" {...register('numero')} />
                </FormField>
                <div className="sm:col-span-2">
                  <FormField label="Complemento" htmlFor="complemento" error={errors.complemento}>
                    <Input id="complemento" placeholder="Apto, Sala..." {...register('complemento')} />
                  </FormField>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
                <FormField label="Bairro" htmlFor="bairro" error={errors.bairro}>
                  <Input id="bairro" placeholder="Bairro" {...register('bairro')} />
                </FormField>
                <FormField label="Cidade" htmlFor="cidade" error={errors.cidade}>
                  <Input id="cidade" placeholder="Cidade" {...register('cidade')} />
                </FormField>
                <FormField label="UF" htmlFor="uf" error={errors.uf}>
                  <Input id="uf" placeholder="SP" maxLength={2} {...register('uf')} />
                </FormField>
              </div>

              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
                <FormField label="Instagram" htmlFor="instagram" error={errors.instagram}>
                  <Input id="instagram" placeholder="@seu.perfil" {...register('instagram')} />
                </FormField>
                <FormField label="Website" htmlFor="website" error={errors.website}>
                  <Input id="website" placeholder="https://..." {...register('website')} />
                </FormField>
              </div>
            </>
          )}

          <Button type="submit" size="lg" loading={isSubmitting}>
            <Save className="h-4 w-4" />
            Salvar alteracoes
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

// ─── Trocar senha ───────────────────────────────────────────────────────────────

type SenhaValues = { senhaAtual: string; novaSenha: string; confirmar: string }

function SenhaForm() {
  const { register, handleSubmit, reset, getValues, formState: { errors, isSubmitting } } = useForm<SenhaValues>()

  async function onSubmit(v: SenhaValues) {
    try {
      await trocarSenha(v.senhaAtual, v.novaSenha)
      toast.success('Senha alterada com sucesso!')
      reset()
    } catch (e) {
      toast.error('Nao foi possivel alterar a senha', { description: extractApiError(e) })
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <Lock className="h-5 w-5 text-primary" />
          Seguranca
        </CardTitle>
        <CardDescription>Troque sua senha. E preciso informar a senha atual.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
          <FormField label="Senha atual" htmlFor="senhaAtual" error={errors.senhaAtual} required>
            <PasswordInput id="senhaAtual" autoComplete="current-password" placeholder="Sua senha atual"
              invalid={!!errors.senhaAtual} {...register('senhaAtual', { required: 'Informe a senha atual' })} />
          </FormField>
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <FormField label="Nova senha" htmlFor="novaSenha" error={errors.novaSenha} hint="No minimo 6 caracteres" required>
              <PasswordInput id="novaSenha" autoComplete="new-password" placeholder="Nova senha"
                invalid={!!errors.novaSenha}
                {...register('novaSenha', { required: 'Informe a nova senha', minLength: { value: 6, message: 'Minimo 6 caracteres' } })} />
            </FormField>
            <FormField label="Confirmar nova senha" htmlFor="confirmar" error={errors.confirmar} required>
              <PasswordInput id="confirmar" autoComplete="new-password" placeholder="Repita a nova senha"
                invalid={!!errors.confirmar}
                {...register('confirmar', {
                  required: 'Confirme a nova senha',
                  validate: (v) => v === getValues('novaSenha') || 'As senhas nao conferem',
                })} />
            </FormField>
          </div>
          <Button type="submit" size="lg" loading={isSubmitting}>
            <Lock className="h-4 w-4" />
            Alterar senha
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
