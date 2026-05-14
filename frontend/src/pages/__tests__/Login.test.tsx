import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Login } from '../Login'
import { renderWithProviders } from '@/test/utils'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth', async () => {
  const real = await vi.importActual<typeof import('@/api/auth')>('@/api/auth')
  return {
    ...real,
    login: vi.fn(),
    me: vi.fn(),
  }
})

describe('<Login>', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(authApi.login).mockReset()
    vi.mocked(authApi.me).mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renderiza campos de email e senha', () => {
    renderWithProviders(<Login />)
    expect(screen.getByLabelText(/e-mail/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/senha/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /entrar/i })).toBeInTheDocument()
  })

  it('mostra erro de validacao para email invalido', async () => {
    renderWithProviders(<Login />)
    await userEvent.type(screen.getByLabelText(/e-mail/i), 'naoeemail')
    await userEvent.type(screen.getByLabelText(/senha/i), 'qualquer')
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }))

    expect(await screen.findByText(/E-mail invalido/)).toBeInTheDocument()
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('chama login com dados validos', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: 'tok',
      tokenType: 'Bearer',
      expiresInMs: 3600000,
      userId: 1,
      email: 'a@b.com',
      papel: 'PARTICIPANTE',
      verificado: true,
    })
    vi.mocked(authApi.me).mockResolvedValue({
      id: 1, nome: 'Ana', email: 'a@b.com', papel: 'PARTICIPANTE',
      verificado: true, criadoEm: '2026-05-12T00:00:00Z',
    })

    renderWithProviders(<Login />)
    await userEvent.type(screen.getByLabelText(/e-mail/i), 'a@b.com')
    await userEvent.type(screen.getByLabelText(/senha/i), 'senha123')
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }))

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith('a@b.com', 'senha123')
    })
  })

  it('tem link para recuperar senha', () => {
    renderWithProviders(<Login />)
    const link = screen.getByRole('link', { name: /esqueci minha senha/i })
    expect(link).toHaveAttribute('href', '/forgot-password')
  })

  it('tem link para cadastro', () => {
    renderWithProviders(<Login />)
    const link = screen.getByRole('link', { name: /crie agora/i })
    expect(link).toHaveAttribute('href', '/register')
  })
})
