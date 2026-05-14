import { Toaster as Sonner, toast } from 'sonner'
import { CheckCircle2, AlertCircle, Info, AlertTriangle } from 'lucide-react'

/**
 * Wrapper do Sonner com tokens do design system. Importe no root da app
 * (uma vez), depois chame `toast.success(...)`, `toast.error(...)` etc.
 */
export function Toaster() {
  return (
    <Sonner
      position="top-right"
      richColors
      closeButton
      toastOptions={{
        classNames: {
          toast:
            'group toast group-[.toaster]:bg-card group-[.toaster]:text-foreground group-[.toaster]:border-border group-[.toaster]:shadow-lg',
          description: 'group-[.toast]:text-muted-foreground',
          actionButton: 'group-[.toast]:bg-primary group-[.toast]:text-primary-foreground',
          cancelButton: 'group-[.toast]:bg-muted group-[.toast]:text-muted-foreground',
        },
      }}
      icons={{
        success: <CheckCircle2 className="h-5 w-5 text-success" />,
        error: <AlertCircle className="h-5 w-5 text-destructive" />,
        info: <Info className="h-5 w-5 text-primary" />,
        warning: <AlertTriangle className="h-5 w-5 text-warning" />,
      }}
    />
  )
}

export { toast }
