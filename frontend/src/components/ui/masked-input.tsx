import { forwardRef } from 'react'
import { IMaskInput } from 'react-imask'
import { cn } from '@/lib/utils'

export interface MaskedInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  mask: string
  invalid?: boolean
  leftIcon?: React.ReactNode
  onValueChange?: (value: string) => void
}

/**
 * Input com mascara dinamica (CPF, telefone, CEP). Devolve o valor *com* a mascara
 * (ex.: '123.456.789-00') ja que o backend valida nesse formato.
 */
export const MaskedInput = forwardRef<HTMLInputElement, MaskedInputProps>(
  ({ className, mask, invalid, leftIcon, value, onValueChange, onChange, ...props }, ref) => {
    const base =
      'flex h-11 w-full rounded-md border bg-card px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50'
    const borderClass = invalid
      ? 'border-destructive focus-visible:ring-destructive'
      : 'border-input'

    const node = (
      <IMaskInput
        mask={mask}
        value={value as string | undefined}
        onAccept={(val: string) => {
          onValueChange?.(val)
          // Compat: dispara onChange para react-hook-form
          if (onChange) {
            const event = { target: { value: val } } as React.ChangeEvent<HTMLInputElement>
            onChange(event)
          }
        }}
        inputRef={ref as never}
        className={cn(base, borderClass, leftIcon && 'pl-10', className)}
        aria-invalid={invalid || undefined}
        {...(props as Record<string, unknown>)}
      />
    )

    if (leftIcon) {
      return (
        <div className="relative">
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
            {leftIcon}
          </span>
          {node}
        </div>
      )
    }
    return node
  }
)
MaskedInput.displayName = 'MaskedInput'
