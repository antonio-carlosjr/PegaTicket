import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Mail } from 'lucide-react'
import { AuthLayout } from '@/components/AuthLayout'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PasswordInput } from '@/components/ui/password-input'
import { FormField } from '@/components/ui/form-field'
import { toast } from '@/components/ui/toaster'
import { loginSchema, type LoginFormValues } from '@/lib/validation'
import { login, extractApiError } from '@/api/auth'
import { useAuth } from '@/hooks/useAuth'

export function Login() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', senha: '' },
  })

  async function onSubmit(values: LoginFormValues) {
    try {
      const r = await login(values.email, values.senha)
      signIn(r)
      toast.success('Bem-vindo de volta!', {
        description: r.papel === 'PROMOTOR' && !r.verificado
          ? 'Seu cadastro de promotor esta em analise.'
          : undefined,
      })
      navigate(params.get('redirect') ?? '/', { replace: true })
    } catch (e) {
      toast.error('Nao foi possivel entrar', { description: extractApiError(e, 'Verifique seus dados.') })
    }
  }

  return (
    <AuthLayout
      title="Entrar na conta"
      subtitle="Acesse seus ingressos, inscricoes e eventos."
      footer={
        <p>
          Nao tem conta?{' '}
          <Link to="/register" className="font-medium text-primary hover:underline">
            Crie agora
          </Link>
        </p>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
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

        <FormField label="Senha" htmlFor="senha" error={errors.senha} required>
          <PasswordInput
            id="senha"
            autoComplete="current-password"
            placeholder="Sua senha"
            invalid={!!errors.senha}
            {...register('senha')}
          />
        </FormField>

        <div className="flex justify-end">
          <Link
            to="/forgot-password"
            className="text-sm font-medium text-primary hover:underline"
          >
            Esqueci minha senha
          </Link>
        </div>

        <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
          Entrar
        </Button>
      </form>
    </AuthLayout>
  )
}
