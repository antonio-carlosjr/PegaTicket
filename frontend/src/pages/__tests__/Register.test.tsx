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
    login: vi.fn(),
    me: vi.fn(),
  }
})

// Labels usam regex anchored para evitar conflito com aria-label do botao
// "Mostrar senha" no PasswordInput.
const nomeLabel = /^Nome/
// Na aba Promotor existe tambem "E-mail de Contato"; o lookahead negativo casa o
// campo de e-mail da conta sem casar o de contato (evita getByLabelText multiplo).
const emailLabel = /^E-mail(?! de Contato)/
const senhaLabel = /^Senha/
const cpfLabel = /^CPF/
const telefoneLabel = /^Telefone/

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
    expect(screen.getByRole('button', { name: /criar conta de participante/i })).toBeInTheDocument()
  })

  it('aba promotor mostra campos CPF e telefone', async () => {
    renderWithProviders(<Register />)
    await userEvent.click(screen.getByRole('tab', { name: /promotor/i }))

    expect(await screen.findByLabelText(cpfLabel)).toBeInTheDocument()
    expect(screen.getByLabelText(telefoneLabel)).toBeInTheDocument()
    expect(screen.getByText(/verificacao do administrador/i)).toBeInTheDocument()
  })

  it('cadastra participante com dados validos', async () => {
    vi.mocked(authApi.register).mockResolvedValue({
      id: 1, nome: 'Ana', email: 'a@x.com', papel: 'PARTICIPANTE',
      verificado: true, criadoEm: '2026-05-12T00:00:00Z',
    })

    renderWithProviders(<Register />)
    await userEvent.type(screen.getByLabelText(nomeLabel), 'Ana Silva')
    await userEvent.type(screen.getByLabelText(emailLabel), 'ana@x.com')
    await userEvent.type(screen.getByLabelText(senhaLabel), 'senha123')
    await userEvent.click(screen.getByRole('button', { name: /criar conta de participante/i }))

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

    await userEvent.type(await screen.findByLabelText(nomeLabel), 'Carlos')
    await userEvent.type(screen.getByLabelText(emailLabel), 'c@x.com')
    await userEvent.type(screen.getByLabelText(senhaLabel), 'senha123')
    // CPF parcial - mascara nao completa
    await userEvent.type(screen.getByLabelText(cpfLabel), '123')
    await userEvent.type(screen.getByLabelText(telefoneLabel), '11912345678')

    await userEvent.click(screen.getByRole('button', { name: /solicitar cadastro/i }))

    expect(await screen.findByText(/CPF deve estar no formato/i)).toBeInTheDocument()
    expect(authApi.register).not.toHaveBeenCalled()
  })
})
