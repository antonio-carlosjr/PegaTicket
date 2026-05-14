import { type ReactElement } from 'react'
import { render, type RenderOptions } from '@testing-library/react'
import { MemoryRouter, type MemoryRouterProps } from 'react-router-dom'
import { AuthProvider } from '@/hooks/useAuth'
import { Toaster } from '@/components/ui/toaster'

interface AllProvidersProps {
  children: React.ReactNode
  routerProps?: MemoryRouterProps
}

function AllProviders({ children, routerProps }: AllProvidersProps) {
  return (
    <MemoryRouter {...routerProps}>
      <AuthProvider>
        {children}
        <Toaster />
      </AuthProvider>
    </MemoryRouter>
  )
}

export function renderWithProviders(
  ui: ReactElement,
  options?: RenderOptions & { routerProps?: MemoryRouterProps }
) {
  const { routerProps, ...rest } = options ?? {}
  return render(ui, {
    wrapper: ({ children }) => <AllProviders routerProps={routerProps}>{children}</AllProviders>,
    ...rest,
  })
}

export * from '@testing-library/react'
export { default as userEvent } from '@testing-library/user-event'
