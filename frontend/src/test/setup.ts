import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'

afterEach(() => {
  cleanup()
})

// Expoe `jest` como alias de `vi` para que @testing-library/dom detecte fake timers do Vitest.
// O @testing-library/dom@10.4.x verifica `typeof jest !== 'undefined'` antes de avançar
// automaticamente os timers fake no waitFor. Sem isso, vi.useFakeTimers() nao e detectado
// e o waitFor nao avanca os timers, causando timeout em testes que usam vi.useFakeTimers().
vi.stubGlobal('jest', vi)

// Polyfill localStorage quando o jsdom nao o fornece corretamente
// (ex: --localstorage-file sem caminho valido em alguns ambientes)
if (typeof window !== 'undefined') {
  if (typeof window.localStorage === 'undefined' || typeof window.localStorage.getItem !== 'function') {
    const store: Record<string, string> = {}
    Object.defineProperty(window, 'localStorage', {
      value: {
        getItem: (k: string) => store[k] ?? null,
        setItem: (k: string, v: string) => { store[k] = String(v) },
        removeItem: (k: string) => { delete store[k] },
        clear: () => { for (const k in store) delete store[k] },
        get length() { return Object.keys(store).length },
        key: (i: number) => Object.keys(store)[i] ?? null,
      },
      writable: true,
    })
  }

  // Polyfill basico para matchMedia (alguns componentes Radix podem usar)
  if (!window.matchMedia) {
    window.matchMedia = (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    })
  }
}
