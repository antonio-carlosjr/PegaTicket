import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/utils'
import { CancelarInscricao } from '../CancelarInscricao'
import * as ticketsApi from '@/api/tickets'

/**
 * Testes Vitest da tela de cancelar inscricao (PA-01 — Bruno).
 * [US-035] — mostrar data-limite do prazo + estado reembolsoIniciado.
 *
 * VERMELHO: CancelarInscricao nao existe; ticketsApi.cancelarInscricao nao existe.
 */

vi.mock('@/api/tickets', async () => {
  const real = await vi.importActual<typeof import('@/api/tickets')>('@/api/tickets')
  return {
    ...real,
    cancelarInscricao: vi.fn(),
  }
})

// Inscricao paga com prazo de reembolso (data 30 dias no futuro, prazo 7 dias)
const inscricaoPaga: ticketsApi.InscricaoHistoricoResponse = {
  id: 17,
  eventoId: 10,
  status: 'ATIVA',
  inscritoEm: '2026-08-01T10:00:00-03:00',
  // Campos da 5B:
  evento: {
    dataInicio: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
    prazoReembolsoDias: 7,
    tipo: 'PAGO',
  },
}

const inscricaoGratuita: ticketsApi.InscricaoHistoricoResponse = {
  id: 18,
  eventoId: 11,
  status: 'ATIVA',
  inscritoEm: '2026-08-02T10:00:00-03:00',
  evento: {
    dataInicio: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
    prazoReembolsoDias: null,
    tipo: 'GRATUITO',
  },
}

describe('<CancelarInscricao>', () => {
  beforeEach(() => {
    vi.mocked(ticketsApi.cancelarInscricao).mockReset()
  })

  // PA-01: exibe data-limite do prazo para evento PAGO
  it('PA-01: exibe data-limite do prazo de reembolso para evento PAGO', () => {
    renderWithProviders(<CancelarInscricao inscricao={inscricaoPaga} />)

    // Deve exibir a data-limite calculada (dataInicio - prazoReembolsoDias)
    expect(screen.getByText(/prazo|limite|reembolso/i)).toBeInTheDocument()
  })

  // GRATUITO: sem prazo exibido
  it('evento GRATUITO: nao exibe prazo de reembolso', () => {
    renderWithProviders(<CancelarInscricao inscricao={inscricaoGratuita} />)

    // Nao deve mostrar data de prazo de reembolso para GRATUITO
    expect(screen.queryByText(/prazo de reembolso/i)).not.toBeInTheDocument()
  })

  // Happy path: GRATUITO cancela sem reembolso
  it('GRATUITO: cancela e exibe confirmacao sem reembolsoIniciado', async () => {
    vi.mocked(ticketsApi.cancelarInscricao).mockResolvedValueOnce({
      inscricaoId: 18,
      status: 'CANCELADA',
      reembolsoIniciado: false,
    })

    const user = userEvent.setup()
    renderWithProviders(<CancelarInscricao inscricao={inscricaoGratuita} />)

    await user.click(screen.getByRole('button', { name: /cancelar inscricao|cancelar/i }))

    await waitFor(() => {
      expect(screen.getByText(/cancelada/i)).toBeInTheDocument()
    })
    // Nao deve mencionar reembolso para GRATUITO
    expect(screen.queryByText(/reembolso iniciado|reembolso em andamento/i)).not.toBeInTheDocument()
  })

  // Happy path PAGO: cancela dentro do prazo -> reembolsoIniciado=true
  it('PAGO dentro do prazo: cancela e exibe estado reembolsoIniciado=true', async () => {
    vi.mocked(ticketsApi.cancelarInscricao).mockResolvedValueOnce({
      inscricaoId: 17,
      status: 'CANCELADA',
      reembolsoIniciado: true,
    })

    const user = userEvent.setup()
    renderWithProviders(<CancelarInscricao inscricao={inscricaoPaga} />)

    await user.click(screen.getByRole('button', { name: /cancelar inscricao|cancelar/i }))

    await waitFor(() => {
      expect(screen.getByText(/cancelada/i)).toBeInTheDocument()
    })
    // Deve exibir que o reembolso foi iniciado
    expect(screen.getByText(/reembolso iniciado|reembolso em andamento/i)).toBeInTheDocument()
  })

  // Fora do prazo: 422 -> exibe mensagem de bloqueio
  it('PAGO fora do prazo: exibe mensagem 422 PRAZO_CANCELAMENTO_ENCERRADO', async () => {
    vi.mocked(ticketsApi.cancelarInscricao).mockRejectedValueOnce({
      response: { status: 422, data: { message: 'PRAZO_CANCELAMENTO_ENCERRADO' } },
    })

    const user = userEvent.setup()
    renderWithProviders(<CancelarInscricao inscricao={inscricaoPaga} />)

    await user.click(screen.getByRole('button', { name: /cancelar inscricao|cancelar/i }))

    await waitFor(() => {
      expect(
        screen.getByText(/prazo.*encerrado|nao e possivel cancelar|prazo_cancelamento_encerrado/i)
      ).toBeInTheDocument()
    })
  })

  // 2o cancelamento: 409
  it('2o cancelamento: exibe 409 INSCRICAO_JA_CANCELADA', async () => {
    vi.mocked(ticketsApi.cancelarInscricao).mockRejectedValueOnce({
      response: { status: 409, data: { message: 'INSCRICAO_JA_CANCELADA' } },
    })

    const user = userEvent.setup()
    renderWithProviders(<CancelarInscricao inscricao={inscricaoPaga} />)

    await user.click(screen.getByRole('button', { name: /cancelar inscricao|cancelar/i }))

    await waitFor(() => {
      expect(
        screen.getByText(/ja cancelada|inscricao_ja_cancelada/i)
      ).toBeInTheDocument()
    })
  })
})
