import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ForgotPassword } from '../ForgotPassword'
import { renderWithProviders } from '@/test/utils'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth', async () => {
  const real = await vi.importActual<typeof import('@/api/auth')>('@/api/auth')
  return {
    ...real,
    forgotPassword: vi.fn(),
    me: vi.fn(),
  }
})

describe('<ForgotPassword>', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(authApi.forgotPassword).mockReset()
  })
  afterEach(() => vi.clearAllMocks())

  it('mostra o campo de email', () => {
    renderWithProviders(<ForgotPassword />)
    expect(screen.getByLabelText(/e-mail/i)).toBeInTheDocument()
  })

  it('envia solicitacao e mostra confirmacao', async () => {
    vi.mocked(authApi.forgotPassword).mockResolvedValue({
      message: 'Se o e-mail estiver cadastrado...',
    })

    renderWithProviders(<ForgotPassword />)
    await userEvent.type(screen.getByLabelText(/e-mail/i), 'a@x.com')
    await userEvent.click(screen.getByRole('button', { name: /enviar/i }))

    await waitFor(() => {
      expect(authApi.forgotPassword).toHaveBeenCalledWith('a@x.com')
    })
    expect(await screen.findByText(/solicitacao enviada/i)).toBeInTheDocument()
    expect(screen.getByText('a@x.com')).toBeInTheDocument()
  })

  it('rejeita email vazio (validacao client)', async () => {
    renderWithProviders(<ForgotPassword />)
    await userEvent.click(screen.getByRole('button', { name: /enviar/i }))

    expect(await screen.findByText(/obrigatorio|invalido/i)).toBeInTheDocument()
    expect(authApi.forgotPassword).not.toHaveBeenCalled()
  })
})
