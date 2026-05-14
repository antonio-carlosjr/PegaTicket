import { type ReactNode } from 'react'
import { type FieldError } from 'react-hook-form'
import { AlertCircle } from 'lucide-react'
import { Label } from './label'
import { cn } from '@/lib/utils'

interface FormFieldProps {
  label: string
  htmlFor?: string
  error?: FieldError | string
  hint?: string
  required?: boolean
  children: ReactNode
  className?: string
}

/**
 * Wrapper que padroniza label, hint e mensagem de erro acima de um input.
 * Use junto com `register` do react-hook-form passando errors.fieldName.
 */
export function FormField({ label, htmlFor, error, hint, required, children, className }: FormFieldProps) {
  const message = typeof error === 'string' ? error : error?.message

  return (
    <div className={cn('space-y-1.5', className)}>
      <Label htmlFor={htmlFor}>
        {label}
        {required && (
          <span className="ml-0.5 text-destructive" aria-hidden="true">
            *
          </span>
        )}
      </Label>
      {children}
      {message && (
        <p
          role="alert"
          className="flex items-start gap-1 text-xs font-medium text-destructive animate-fade-in"
        >
          <AlertCircle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" aria-hidden="true" />
          <span>{message}</span>
        </p>
      )}
      {!message && hint && (
        <p className="text-xs text-muted-foreground">{hint}</p>
      )}
    </div>
  )
}
