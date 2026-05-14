import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('h-5 w-5 animate-spin text-primary', className)} />
}

export function PageLoader({ label = 'Carregando...' }: { label?: string }) {
  return (
    <div className="flex flex-1 items-center justify-center gap-3 p-12 text-muted-foreground">
      <Spinner />
      <span className="text-sm">{label}</span>
    </div>
  )
}
