import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/utils'
import { CheckinScanner } from '../CheckinScanner'
import * as ticketsApi from '@/api/tickets'

/**
 * Testes Vitest da tela de scanner de check-in (Marina / promotor).
 * [US-034] — scanner/validacao de check-in via QR (entrada manual de codigo).
 *
 * VERMELHO: CheckinScanner nao existe; ticketsApi.checkin nao existe.
 */

vi.mock('@/api/tickets', async () => {
  const real = await vi.importActual<typeof import('@/api/tickets')>('@/api/tickets')
  return {
    ...real,
    checkin: vi.fn(),
  }
})

describe('<CheckinScanner>', () => {
  beforeEach(() => {
    vi.mocked(ticketsApi.checkin).mockReset()
  })

  // Happy path: promotor insere codigo valido, ingresso validado
  it('happy path: codigo valido -> exibe status UTILIZADO e realizadoEm', async () => {
    vi.mocked(ticketsApi.checkin).mockResolvedValueOnce({
      ingressoId: 42,
      inscricaoId: 17,
      status: 'UTILIZADO',
      realizadoEm: '2026-09-01T20:05:00-03:00',
    })

    const user = userEvent.setup()
    renderWithProviders(<CheckinScanner eventoId={10} />)

    const input = screen.getByPlaceholderText(/codigo/i)
    await user.type(input, 'c0ffee00-1111-2222-3333-444455556666')
    await user.click(screen.getByRole('button', { name: /validar|checar|confirmar/i }))

    await waitFor(() => {
      expect(screen.getByText(/utilizado/i)).toBeInTheDocument()
    })
    expect(screen.getByText(/realizado em/i)).toBeInTheDocument()
  })

  // Ingresso ja utilizado (409)
  it('exibe mensagem de erro quando ingresso ja foi utilizado (409)', async () => {
    vi.mocked(ticketsApi.checkin).mockRejectedValueOnce({
      response: { status: 409, data: { message: 'INGRESSO_JA_UTILIZADO' } },
    })

    const user = userEvent.setup()
    renderWithProviders(<CheckinScanner eventoId={10} />)

    await user.type(
      screen.getByPlaceholderText(/codigo/i),
      'codigo-ja-utilizado'
    )
    await user.click(screen.getByRole('button', { name: /validar|checar|confirmar/i }))

    await waitFor(() => {
      expect(screen.getByText(/ja utilizado|ingresso_ja_utilizado/i)).toBeInTheDocument()
    })
  })

  // Evento alheio (403)
  it('exibe 403 CHECKIN_EVENTO_ALHEIO quando promotor nao e dono', async () => {
    vi.mocked(ticketsApi.checkin).mockRejectedValueOnce({
      response: { status: 403, data: { message: 'CHECKIN_EVENTO_ALHEIO' } },
    })

    const user = userEvent.setup()
    renderWithProviders(<CheckinScanner eventoId={10} />)

    await user.type(screen.getByPlaceholderText(/codigo/i), 'codigo-evento-alheio')
    await user.click(screen.getByRole('button', { name: /validar|checar|confirmar/i }))

    await waitFor(() => {
      expect(screen.getByText(/evento alheio|checkin_evento_alheio/i)).toBeInTheDocument()
    })
  })

  // Codigo inexistente (404)
  it('exibe 404 INGRESSO_NAO_ENCONTRADO para codigo invalido', async () => {
    vi.mocked(ticketsApi.checkin).mockRejectedValueOnce({
      response: { status: 404, data: { message: 'INGRESSO_NAO_ENCONTRADO' } },
    })

    const user = userEvent.setup()
    renderWithProviders(<CheckinScanner eventoId={10} />)

    await user.type(screen.getByPlaceholderText(/codigo/i), 'codigo-inexistente')
    await user.click(screen.getByRole('button', { name: /validar|checar|confirmar/i }))

    await waitFor(() => {
      expect(screen.getByText(/nao encontrado|ingresso_nao_encontrado/i)).toBeInTheDocument()
    })
  })

  // Validacao: campo vazio nao submete
  it('nao chama API quando codigo esta vazio', async () => {
    const user = userEvent.setup()
    renderWithProviders(<CheckinScanner eventoId={10} />)

    await user.click(screen.getByRole('button', { name: /validar|checar|confirmar/i }))

    expect(ticketsApi.checkin).not.toHaveBeenCalled()
  })
})
