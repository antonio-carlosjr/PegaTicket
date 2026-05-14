import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Register } from '../Register'
import { renderWithProviders } from '@/test/utils'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth', async () => {
  const real = await vi.importActual<typeof import('@/api/auth')>('@/api/auth')
  return {
    ...real,
    register: vi.fn(),
    me: vi.fn(),
  }
})

describe('<Register>', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(authApi.register).mockReset()
    vi.mocked(authApi.me).mockReset()
  })

  afterEach(() => vi.clearAllMocks())

  it('mostra os dois tabs', () => {
    renderWithProviders(<Register />)
    expect(screen.getByRole('tab', { name: /participante/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /promotor/i })).toBeInTheDocument()
  })

  it('participante e a aba padrao', () => {
    renderWithProviders(<Register />)
    expect(screen.getByRole('tab', { name: /participante/i })).toHaveAttribute('data-state', 'active')
    expect(screen.getByRole('button', { name: /participante/i })).toBeInTheDocument()
  })

  it('aba promotor mostra campos CPF e telefone', async () => {
    renderWithProviders(<Register />)
    await userEvent.click(screen.getByRole('tab', { name: /promotor/i }))

    expect(await screen.findByLabelText(/cpf/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/telefone/i)).toBeInTheDocument()
    expect(screen.getByText(/aprovacao do administrador/i)).toBeInTheDocument()
  })

  it('cadastra participante com dados validos', async () => {
    vi.mocked(authApi.register).mockResolvedValue({
      id: 1, nome: 'Ana', email: 'a@x.com', papel: 'PARTICIPANTE',
      verificado: true, criadoEm: '2026-05-12T00:00:00Z',
    })

    renderWithProviders(<Register />)
    await userEvent.type(screen.getByLabelText(/nome/i), 'Ana Silva')
    await userEvent.type(screen.getByLabelText(/e-mail/i), 'ana@x.com')
    await userEvent.type(screen.getByLabelText(/senha/i), 'senha123')
    await userEvent.click(screen.getByRole('button', { name: /participante/i }))

    await waitFor(() => {
      expect(authApi.register).toHaveBeenCalledWith({
        nome: 'Ana Silva',
        email: 'ana@x.com',
        senha: 'senha123',
        papel: 'PARTICIPANTE',
      })
    })
  })

  it('rejeita CPF sem mascara correta', async () => {
    renderWithProviders(<Register />)
    await userEvent.click(screen.getByRole('tab', { name: /promotor/i }))

    await userEvent.type(await screen.findByLabelText(/^nome/i), 'Carlos')
    await userEvent.type(screen.getByLabelText(/e-mail/i), 'c@x.com')
    await userEvent.type(screen.getByLabelText(/senha/i), 'senha123')
    // CPF parcial - mascara nao completou
    await userEvent.type(screen.getByLabelText(/cpf/i), '123')
    await userEvent.type(screen.getByLabelText(/telefone/i), '11912345678')

    await userEvent.click(screen.getByRole('button', { name: /solicitar cadastro/i }))

    // Deve mostrar erro de validacao (zod)
    expect(await screen.findByText(/CPF deve estar no formato/i)).toBeInTheDocument()
    expect(authApi.register).not.toHaveBeenCalled()
  })
})
