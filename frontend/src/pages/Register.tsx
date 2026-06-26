import { useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate } from 'react-router-dom'
import { Mail, User, IdCard, Phone, Sparkles, BadgeCheck } from 'lucide-react'
import { AuthLayout } from '@/components/AuthLayout'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PasswordInput } from '@/components/ui/password-input'
import { MaskedInput } from '@/components/ui/masked-input'
import { FormField } from '@/components/ui/form-field'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { toast } from '@/components/ui/toaster'
import {
  registerParticipanteSchema,
  registerPromotorSchema,
  type RegisterParticipanteValues,
  type RegisterPromotorValues,
} from '@/lib/validation'
import { register as registerApi, login, extractApiError } from '@/api/auth'
import { useAuth } from '@/hooks/useAuth'

type Papel = 'PARTICIPANTE' | 'PROMOTOR'

export function Register() {
  const navigate = useNavigate()
  const { signIn } = useAuth()
  const [papel, setPapel] = useState<Papel>('PARTICIPANTE')

  // Auto-login apos o registro: autentica e vai pra home; se o login falhar
  // (ex.: cold start), cai no /login (a conta ja foi criada).
  async function entrarOuLogin(email: string, senha: string) {
    try {
      const resp = await login(email, senha)
      signIn(resp)
      navigate('/')
    } catch {
      navigate('/login')
    }
  }

  return (
    <AuthLayout
      title="Criar conta"
      subtitle="Escolha como voce quer usar a plataforma."
      footer={
        <p>
          Ja tem conta?{' '}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Faca login
          </Link>
        </p>
      }
    >
      <Tabs value={papel} onValueChange={(v) => setPapel(v as Papel)}>
        <TabsList>
          <TabsTrigger value="PARTICIPANTE">
            <Sparkles className="h-4 w-4" />
            Participante
          </TabsTrigger>
          <TabsTrigger value="PROMOTOR">
            <BadgeCheck className="h-4 w-4" />
            Promotor
          </TabsTrigger>
        </TabsList>

        <TabsContent value="PARTICIPANTE">
          <FormParticipante onSuccess={entrarOuLogin} />
        </TabsContent>

        <TabsContent value="PROMOTOR">
          <FormPromotor onSuccess={entrarOuLogin} />
        </TabsContent>
      </Tabs>
    </AuthLayout>
  )
}

function FormParticipante({ onSuccess }: { onSuccess: (email: string, senha: string) => void }) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterParticipanteValues>({
    resolver: zodResolver(registerParticipanteSchema),
    defaultValues: { nome: '', email: '', senha: '' },
  })

  async function onSubmit(values: RegisterParticipanteValues) {
    try {
      await registerApi({ ...values, papel: 'PARTICIPANTE' })
      toast.success('Conta criada!', { description: 'Bem-vindo(a) ao PegaTicket.' })
      onSuccess(values.email, values.senha)
    } catch (e) {
      toast.error('Nao foi possivel criar a conta', { description: extractApiError(e) })
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
      <FormField label="Nome completo" htmlFor="nome" error={errors.nome} required>
        <Input
          id="nome"
          autoComplete="name"
          placeholder="Como deseja ser chamado(a)"
          leftIcon={<User className="h-4 w-4" />}
          invalid={!!errors.nome}
          {...register('nome')}
        />
      </FormField>

      <FormField label="E-mail" htmlFor="email" error={errors.email} required>
        <Input
          id="email"
          type="email"
          autoComplete="email"
          placeholder="voce@email.com"
          leftIcon={<Mail className="h-4 w-4" />}
          invalid={!!errors.email}
          {...register('email')}
        />
      </FormField>

      <FormField label="Senha" htmlFor="senha" error={errors.senha} hint="No minimo 6 caracteres" required>
        <PasswordInput
          id="senha"
          autoComplete="new-password"
          placeholder="Crie uma senha"
          invalid={!!errors.senha}
          {...register('senha')}
        />
      </FormField>

      <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>
        Criar conta de participante
      </Button>
    </form>
  )
}

function FormPromotor({ onSuccess }: { onSuccess: (email: string, senha: string) => void }) {
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<RegisterPromotorValues>({
    resolver: zodResolver(registerPromotorSchema),
    defaultValues: { nome: '', email: '', senha: '', cpf: '', telefone: '', emailContato: '', cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '', instagram: '', website: '' },
  })

  async function onSubmit(values: RegisterPromotorValues) {
    try {
      await registerApi({ ...values, papel: 'PROMOTOR' })
      toast.success('Cadastro enviado!', {
        description: 'Aguarde a aprovacao do administrador para criar eventos.',
      })
      onSuccess(values.email, values.senha)
    } catch (e) {
      toast.error('Nao foi possivel criar a conta', { description: extractApiError(e) })
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
      <div className="rounded-md bg-accent p-3 text-xs text-accent-foreground">
        <strong>Atencao:</strong> cadastros de promotor passam por verificacao do
        administrador antes de poder criar eventos.
      </div>

      <FormField label="Nome completo" htmlFor="nome" error={errors.nome} required>
        <Input
          id="nome"
          autoComplete="name"
          placeholder="Razao social ou nome"
          leftIcon={<User className="h-4 w-4" />}
          invalid={!!errors.nome}
          {...register('nome')}
        />
      </FormField>

      <FormField label="E-mail" htmlFor="email-p" error={errors.email} required>
        <Input
          id="email-p"
          type="email"
          autoComplete="email"
          placeholder="voce@email.com"
          leftIcon={<Mail className="h-4 w-4" />}
          invalid={!!errors.email}
          {...register('email')}
        />
      </FormField>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        <FormField label="CPF" htmlFor="cpf" error={errors.cpf} required>
          <Controller
            name="cpf"
            control={control}
            render={({ field }) => (
              <MaskedInput
                id="cpf"
                mask="000.000.000-00"
                placeholder="000.000.000-00"
                leftIcon={<IdCard className="h-4 w-4" />}
                invalid={!!errors.cpf}
                value={field.value}
                onValueChange={field.onChange}
                onBlur={field.onBlur}
                name={field.name}
              />
            )}
          />
        </FormField>

        <FormField label="Telefone" htmlFor="tel" error={errors.telefone} required>
          <Controller
            name="telefone"
            control={control}
            render={({ field }) => (
              <MaskedInput
                id="tel"
                mask="(00) 00000-0000"
                placeholder="(11) 99999-0000"
                leftIcon={<Phone className="h-4 w-4" />}
                invalid={!!errors.telefone}
                value={field.value}
                onValueChange={field.onChange}
                onBlur={field.onBlur}
                name={field.name}
              />
            )}
          />
        </FormField>
      </div>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        <FormField label="E-mail de Contato" htmlFor="emailContato" error={errors.emailContato}>
          <Input id="emailContato" type="email" placeholder="contato@empresa.com" invalid={!!errors.emailContato} {...register('emailContato')} />
        </FormField>
        <FormField label="Website" htmlFor="website" error={errors.website}>
          <Input id="website" placeholder="https://..." invalid={!!errors.website} {...register('website')} />
        </FormField>
      </div>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
        <FormField label="CEP" htmlFor="cep" error={errors.cep}>
          <Controller
            name="cep"
            control={control}
            render={({ field }) => (
              <MaskedInput id="cep" mask="00000-000" placeholder="00000-000" invalid={!!errors.cep} value={field.value || ''} onValueChange={field.onChange} onBlur={field.onBlur} name={field.name} />
            )}
          />
        </FormField>
        <div className="sm:col-span-2">
          <FormField label="Logradouro" htmlFor="logradouro" error={errors.logradouro}>
            <Input id="logradouro" placeholder="Rua, Avenida..." invalid={!!errors.logradouro} {...register('logradouro')} />
          </FormField>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
        <FormField label="Numero" htmlFor="numero" error={errors.numero}>
          <Input id="numero" placeholder="123" invalid={!!errors.numero} {...register('numero')} />
        </FormField>
        <div className="sm:col-span-2">
          <FormField label="Complemento" htmlFor="complemento" error={errors.complemento}>
            <Input id="complemento" placeholder="Apto, Sala..." invalid={!!errors.complemento} {...register('complemento')} />
          </FormField>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
        <FormField label="Bairro" htmlFor="bairro" error={errors.bairro}>
          <Input id="bairro" placeholder="Bairro" invalid={!!errors.bairro} {...register('bairro')} />
        </FormField>
        <FormField label="Cidade" htmlFor="cidade" error={errors.cidade}>
          <Input id="cidade" placeholder="Cidade" invalid={!!errors.cidade} {...register('cidade')} />
        </FormField>
        <FormField label="UF" htmlFor="uf" error={errors.uf}>
          <Input id="uf" placeholder="SP" invalid={!!errors.uf} maxLength={2} {...register('uf')} />
        </FormField>
      </div>

      <FormField label="Instagram" htmlFor="instagram" error={errors.instagram}>
        <Input id="instagram" placeholder="@seu.perfil" invalid={!!errors.instagram} {...register('instagram')} />
      </FormField>

      <FormField label="Senha" htmlFor="senha-p" error={errors.senha} hint="No minimo 6 caracteres" required>
        <PasswordInput
          id="senha-p"
          autoComplete="new-password"
          placeholder="Crie uma senha"
          invalid={!!errors.senha}
          {...register('senha')}
        />
      </FormField>

      <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>
        Solicitar cadastro de promotor
      </Button>
    </form>
  )
}
