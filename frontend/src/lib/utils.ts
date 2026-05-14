import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Combina classes Tailwind resolvendo conflitos (ex.: `px-2` + `px-4` => `px-4`).
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * Sleep helper para skeletons e debouncing leve.
 */
export const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
