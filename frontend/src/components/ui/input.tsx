import { forwardRef } from 'react'
import { cn } from '@/lib/utils'

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean
  leftIcon?: React.ReactNode
  rightIcon?: React.ReactNode
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, type = 'text', invalid, leftIcon, rightIcon, ...props }, ref) => {
    const base =
      'flex h-11 w-full rounded-md border bg-card px-3 py-2 text-sm shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50'
    const borderClass = invalid
      ? 'border-destructive focus-visible:ring-destructive'
      : 'border-input'

    if (leftIcon || rightIcon) {
      return (
        <div className="relative">
          {leftIcon && (
            <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
              {leftIcon}
            </span>
          )}
          <input
            type={type}
            className={cn(
              base,
              borderClass,
              leftIcon && 'pl-10',
              rightIcon && 'pr-10',
              className
            )}
            ref={ref}
            aria-invalid={invalid || undefined}
            {...props}
          />
          {rightIcon && (
            <span className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground">
              {rightIcon}
            </span>
          )}
        </div>
      )
    }

    return (
      <input
        type={type}
        className={cn(base, borderClass, className)}
        ref={ref}
        aria-invalid={invalid || undefined}
        {...props}
      />
    )
  }
)
Input.displayName = 'Input'
