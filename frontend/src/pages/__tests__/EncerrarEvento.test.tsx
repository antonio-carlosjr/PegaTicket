import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/utils'
import * as eventsApi from '@/api/events'

/**
 * E (frontend) — Botao "Encerrar evento" (US-043).
 * Promotor dono + evento PUBLICADO → POST /api/events/:id/encerrar → toast sucesso +
 * status do evento vira "Realizado".
 *
 * VERMELHO:
 * - encerrarEvento() nao existe em @/api/events.
 * - MeusEventos nao exibe o botao "Encerrar" para eventos PUBLICADO.
 * - Tela nao trata 409 TRANSICAO_INVALIDA nem 403/404.
 */

vi.mock('@/api/events', async () => {
  const real = await vi.importActual<typeof import('@/api/events')>('@/api/events')
  return {
    ...real,
    meusEventos: vi.fn(),
    encerrarEvento: vi.fn(), // VERMELHO: funcao nao existe ainda
  }
})

// Importar a pagina de meus eventos apos o mock
import { MeusEventos } from '../MeusEventos'

// Fixture: evento PUBLICADO do promotor logado
const eventoPublicado: eventsApi.EventoResumo = {
  id: 42,
  titulo: 'Show da Terra',
  dataInicio: '2026-09-01T20:00:00-03:00',
  dataFim: '2026-09-01T23:00:00-03:00',
  local: 'Arena Norte',
  tipo: 'PAGO',
  status: 'PUBLICADO',
  preco: '50.00',
  capacidade: 200,
  imagemUrl: null,
}

const eventoRealizado: eventsApi.EventoResumo = {
  ...eventoPublicado,
  status: 'REALIZADO',
}

// Fixture de page
const paginaComUmEvento = {
  content: [eventoPublicado],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 10,
}

describe('MeusEventos — botao Encerrar evento (US-043)', () => {
  beforeEach(() => {
    vi.mocked(eventsApi.meusEventos).mockReset()
    vi.mocked(eventsApi.encerrarEvento).mockReset()
  })

  /**
   * E — encerrarEvento_chamaPost_eMostraSucesso [US-043]
   * Promotor dono clica "Encerrar" → POST /api/events/:id/encerrar → toast sucesso;
   * status do evento vira "Realizado".
   */
  it('E — promotor clica Encerrar → chama POST /encerrar → status Realizado + toast', async () => {
    vi.mocked(eventsApi.meusEventos).mockResolvedValue(paginaComUmEvento)
    vi.mocked(eventsApi.encerrarEvento).mockResolvedValue({
      ...eventoPublicado,
      status: 'REALIZADO',
      criadoEm: '2026-01-01T00:00:00-03:00',
      atualizadoEm: '2026-09-01T23:01:00-03:00',
      descricao: null,
      vagasDisponiveis: 200,
      prazoReembolsoDias: null,
      promotorId: 7,
    } as eventsApi.EventoResponse)

    const user = userEvent.setup()
    renderWithProviders(<MeusEventos />)

    // Aguarda carregar a lista
    await waitFor(() => {
      expect(screen.getByText(/show da terra/i)).toBeInTheDocument()
    })

    // Botao "Encerrar" deve aparecer para evento PUBLICADO
    const btnEncerrar = screen.getByRole('button', { name: /encerrar/i })
    expect(btnEncerrar).toBeInTheDocument()

    await user.click(btnEncerrar)

    // Deve ter chamado encerrarEvento com o id do evento
    expect(vi.mocked(eventsApi.encerrarEvento)).toHaveBeenCalledWith(42)

    // Toast de sucesso
    await waitFor(() => {
      expect(screen.getByText(/encerrado|realizado|repasse/i)).toBeInTheDocument()
    })

    // Status atualizado na tela
    await waitFor(() => {
      expect(screen.getByText(/realizado/i)).toBeInTheDocument()
    })
  })

  /**
   * E — encerrar_eventoJaRealizado_exibe409 [US-043]
   * 409 TRANSICAO_INVALIDA deve mostrar mensagem amigavel (nao stack trace).
   */
  it('E — 409 TRANSICAO_INVALIDA exibe mensagem amigavel', async () => {
    vi.mocked(eventsApi.meusEventos).mockResolvedValue(paginaComUmEvento)
    vi.mocked(eventsApi.encerrarEvento).mockRejectedValue({
      response: {
        status: 409,
        data: { message: 'TRANSICAO_INVALIDA' },
      },
    })

    const user = userEvent.setup()
    renderWithProviders(<MeusEventos />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /encerrar/i })).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: /encerrar/i }))

    await waitFor(() => {
      // Mensagem amigavel — nao deve exibir o codigo de erro literal
      expect(
        screen.getByText(/nao e possivel encerrar|transicao invalida|status nao permite/i)
      ).toBeInTheDocument()
    })

    // Nao deve mostrar stack trace
    expect(screen.queryByText(/Error:|at /)).not.toBeInTheDocument()
  })

  /**
   * E — botao_encerrar_naoAparece_paraEventosNaoPublicados
   * Botao "Encerrar" nao deve aparecer para eventos ja REALIZADO ou CANCELADO.
   */
  it('E — botao Encerrar nao aparece para eventos ja REALIZADO', async () => {
    vi.mocked(eventsApi.meusEventos).mockResolvedValue({
      ...paginaComUmEvento,
      content: [eventoRealizado],
    })

    renderWithProviders(<MeusEventos />)

    await waitFor(() => {
      expect(screen.getByText(/show da terra/i)).toBeInTheDocument()
    })

    // Para REALIZADO, botao "Encerrar" nao deve estar presente
    expect(screen.queryByRole('button', { name: /encerrar/i })).not.toBeInTheDocument()
  })
})
