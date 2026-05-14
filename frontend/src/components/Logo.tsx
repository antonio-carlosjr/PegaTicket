import { Ticket } from 'lucide-react'
import { cn } from '@/lib/utils'

interface LogoProps {
  size?: 'sm' | 'md' | 'lg'
  className?: string
  showText?: boolean
}

export function Logo({ size = 'md', className, showText = true }: LogoProps) {
  const sizes = {
    sm: { wrap: 'gap-1.5', icon: 'h-5 w-5 p-1', text: 'text-base' },
    md: { wrap: 'gap-2', icon: 'h-8 w-8 p-1.5', text: 'text-xl' },
    lg: { wrap: 'gap-2.5', icon: 'h-10 w-10 p-2', text: 'text-2xl' },
  } as const

  const s = sizes[size]
  return (
    <div className={cn('inline-flex items-center font-bold tracking-tight', s.wrap, className)}>
      <span
        className={cn(
          'inline-flex items-center justify-center rounded-lg bg-gradient-to-br from-primary to-blue-700 text-primary-foreground shadow-sm',
          s.icon
        )}
      >
        <Ticket className="h-full w-full" />
      </span>
      {showText && (
        <span className={cn('bg-gradient-to-br from-foreground to-foreground/70 bg-clip-text text-transparent', s.text)}>
          PegaTicket
        </span>
      )}
    </div>
  )
}
