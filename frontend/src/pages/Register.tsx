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
import { register as registerApi, extractApiError } from '@/api/auth'

type Papel = 'PARTICIPANTE' | 'PROMOTOR'

export function Register() {
  const navigate = useNavigate()
  const [papel, setPapel] = useState<Papel>('PARTICIPANTE')

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
          <FormParticipante onSuccess={() => navigate('/login')} />
        </TabsContent>

        <TabsContent value="PROMOTOR">
          <FormPromotor onSuccess={() => navigate('/login')} />
        </TabsContent>
      </Tabs>
    </AuthLayout>
  )
}

function FormParticipante({ onSuccess }: { onSuccess: () => void }) {
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
      toast.success('Conta criada!', { description: 'Voce ja pode fazer login.' })
      onSuccess()
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

function FormPromotor({ onSuccess }: { onSuccess: () => void }) {
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<RegisterPromotorValues>({
    resolver: zodResolver(registerPromotorSchema),
    defaultValues: { nome: '', email: '', senha: '', cpf: '', telefone: '' },
  })

  async function onSubmit(values: RegisterPromotorValues) {
    try {
      await registerApi({ ...values, papel: 'PROMOTOR' })
      toast.success('Cadastro enviado!', {
        description: 'Aguarde a aprovacao do administrador para criar eventos.',
      })
      onSuccess()
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
