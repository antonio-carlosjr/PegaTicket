import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { AlertCircle } from 'lucide-react'
import { AuthLayout } from '@/components/AuthLayout'
import { Button } from '@/components/ui/button'
import { PasswordInput } from '@/components/ui/password-input'
import { FormField } from '@/components/ui/form-field'
import { toast } from '@/components/ui/toaster'
import { resetSchema, type ResetFormValues } from '@/lib/validation'
import { resetPassword, extractApiError } from '@/api/auth'

export function ResetPassword() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token')

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetFormValues>({
    resolver: zodResolver(resetSchema),
    defaultValues: { novaSenha: '', confirmar: '' },
  })

  async function onSubmit(values: ResetFormValues) {
    if (!token) return
    try {
      await resetPassword(token, values.novaSenha)
      toast.success('Senha redefinida!', { description: 'Agora voce pode fazer login.' })
      navigate('/login', { replace: true })
    } catch (e) {
      toast.error('Nao foi possivel redefinir', { description: extractApiError(e) })
    }
  }

  if (!token) {
    return (
      <AuthLayout title="Link invalido">
        <div className="space-y-6">
          <div className="flex items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
            <AlertCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-destructive" />
            <div className="text-sm">
              <p className="font-medium text-foreground">Token nao encontrado</p>
              <p className="mt-1 text-muted-foreground">
                O link de redefinicao parece estar quebrado ou incompleto.
                Solicite um novo abaixo.
              </p>
            </div>
          </div>
          <Link to="/forgot-password">
            <Button className="w-full" size="lg">Solicitar novo link</Button>
          </Link>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      title="Criar nova senha"
      subtitle="Escolha uma senha que voce nao usou em outros sites."
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
        <FormField
          label="Nova senha"
          htmlFor="nova"
          error={errors.novaSenha}
          hint="No minimo 6 caracteres"
          required
        >
          <PasswordInput
            id="nova"
            autoComplete="new-password"
            placeholder="Sua nova senha"
            invalid={!!errors.novaSenha}
            {...register('novaSenha')}
          />
        </FormField>

        <FormField label="Confirmar senha" htmlFor="conf" error={errors.confirmar} required>
          <PasswordInput
            id="conf"
            autoComplete="new-password"
            placeholder="Digite novamente"
            invalid={!!errors.confirmar}
            {...register('confirmar')}
          />
        </FormField>

        <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>
          Redefinir senha
        </Button>
      </form>
    </AuthLayout>
  )
}
