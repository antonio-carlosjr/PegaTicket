import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router-dom'
import { Mail, CheckCircle2, ArrowLeft } from 'lucide-react'
import { AuthLayout } from '@/components/AuthLayout'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { FormField } from '@/components/ui/form-field'
import { toast } from '@/components/ui/toaster'
import { forgotSchema, type ForgotFormValues } from '@/lib/validation'
import { forgotPassword, extractApiError } from '@/api/auth'

export function ForgotPassword() {
  const [sentTo, setSentTo] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotFormValues>({
    resolver: zodResolver(forgotSchema),
    defaultValues: { email: '' },
  })

  async function onSubmit(values: ForgotFormValues) {
    try {
      await forgotPassword(values.email)
      setSentTo(values.email)
    } catch (e) {
      toast.error('Falha ao solicitar', { description: extractApiError(e) })
    }
  }

  if (sentTo) {
    return (
      <AuthLayout title="Verifique seu e-mail">
        <div className="space-y-6">
          <div className="flex items-start gap-3 rounded-lg border border-success/30 bg-success/5 p-4">
            <CheckCircle2 className="mt-0.5 h-5 w-5 flex-shrink-0 text-success" />
            <div className="text-sm">
              <p className="font-medium text-foreground">Solicitacao enviada</p>
              <p className="mt-1 text-muted-foreground">
                Se o e-mail <strong className="text-foreground">{sentTo}</strong> estiver cadastrado,
                voce recebera as instrucoes em instantes.
              </p>
            </div>
          </div>

          <p className="text-sm text-muted-foreground">
            O link expira em <strong className="text-foreground">60 minutos</strong>.
            Confira a caixa de spam caso nao veja em alguns minutos.
          </p>

          <Link to="/login">
            <Button variant="outline" className="w-full">
              <ArrowLeft className="h-4 w-4" />
              Voltar para o login
            </Button>
          </Link>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      title="Recuperar senha"
      subtitle="Informe seu e-mail e enviaremos um link para criar uma nova senha."
      footer={
        <p>
          Lembrou a senha?{' '}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Voltar para o login
          </Link>
        </p>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
        <FormField label="E-mail da conta" htmlFor="email" error={errors.email} required>
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

        <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>
          Enviar link de recuperacao
        </Button>
      </form>
    </AuthLayout>
  )
}
