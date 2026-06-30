import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/utils'
import { AvaliacaoEvento } from '../AvaliacaoEvento'
import * as eventsApi from '@/api/events'

/**
 * Testes Vitest da tela de avaliacao de evento + exibicao de reputacao.
 * [US-024] avaliar (nota 1-5) + [US-025] exibir reputacao (media/total).
 *
 * VERMELHO: AvaliacaoEvento nao existe; eventsApi.avaliar nao existe;
 *           EventoResponse.reputacao nao existe.
 */

vi.mock('@/api/events', async () => {
  const real = await vi.importActual<typeof import('@/api/events')>('@/api/events')
  return {
    ...real,
    detalheEvento: vi.fn(),
    avaliar: vi.fn(),
  }
})

const eventoRealizadoComReputacao: eventsApi.EventoResponse = {
  id: 10,
  titulo: 'Festival de Jazz',
  descricao: 'Melhor festival do ano',
  dataInicio: '2026-08-01T18:00:00-03:00',
  dataFim: '2026-08-01T23:00:00-03:00',
  local: 'Parque da Cidade',
  tipo: 'PAGO',
  status: 'REALIZADO',
  capacidade: 500,
  vagasDisponiveis: 0,
  preco: '80.00',
  prazoReembolsoDias: 7,
  imagemUrl: null,
  promotorId: 5,
  criadoEm: '2026-06-01T10:00:00-03:00',
  atualizadoEm: '2026-08-02T00:00:00-03:00',
  reputacao: { media: 4.2, total: 37 },   // NOVO (US-025)
}

const eventoRealizadoSemAvaliacoes: eventsApi.EventoResponse = {
  ...eventoRealizadoComReputacao,
  id: 11,
  reputacao: { media: null, total: 0 },
}

describe('<AvaliacaoEvento>', () => {
  beforeEach(() => {
    vi.mocked(eventsApi.detalheEvento).mockReset()
    vi.mocked(eventsApi.avaliar).mockReset()
  })

  // US-025: exibe reputacao com media e total
  it('US-025: exibe media e total de avaliacoes quando evento tem reputacao', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoRealizadoComReputacao)

    renderWithProviders(<AvaliacaoEvento eventoId={10} />)

    await waitFor(() => {
      expect(screen.getByText(/4\.2|4,2/)).toBeInTheDocument()
      expect(screen.getByText(/37/)).toBeInTheDocument()
    })
  })

  // US-025: exibe "sem avaliacoes" quando total=0
  it('US-025: exibe estado vazio quando nao ha avaliacoes (total=0)', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoRealizadoSemAvaliacoes)

    renderWithProviders(<AvaliacaoEvento eventoId={11} />)

    await waitFor(() => {
      expect(
        screen.getByText(/sem avaliacoes|nenhuma avaliacao|0 avaliacoes/i)
      ).toBeInTheDocument()
    })
  })

  // US-024: happy path — avaliacao nota 4 enviada com sucesso
  it('US-024: happy path — seleciona nota 4 e envia -> exibe confirmacao', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoRealizadoComReputacao)
    vi.mocked(eventsApi.avaliar).mockResolvedValueOnce({
      id: 1,
      eventoId: 10,
      usuarioId: 10,
      nota: 4,
      comentario: 'Excelente!',
      avaliadoEm: '2026-08-02T10:00:00-03:00',
    })

    const user = userEvent.setup()
    renderWithProviders(<AvaliacaoEvento eventoId={10} />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /festival de jazz/i })).toBeInTheDocument()
    })

    // Seleciona nota 4
    const nota4 = screen.getByRole('radio', { name: /4/i }) ||
                  screen.getByLabelText(/4 estrelas?|nota 4/i)
    await user.click(nota4)

    await user.click(screen.getByRole('button', { name: /avaliar|enviar avaliacao/i }))

    await waitFor(() => {
      expect(screen.getByText(/avaliacao enviada|obrigado|avaliado/i)).toBeInTheDocument()
    })
  })

  // US-024: nota fora de 1-5 nao submete
  it('US-024: nota fora de 1-5 nao e aceita (validacao frontend)', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoRealizadoComReputacao)

    renderWithProviders(<AvaliacaoEvento eventoId={10} />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /festival de jazz/i })).toBeInTheDocument()
    })

    // Nao seleciona nenhuma nota e tenta enviar
    await userEvent.setup().click(
      screen.getByRole('button', { name: /avaliar|enviar avaliacao/i })
    )

    // Nao deve ter chamado a API
    expect(eventsApi.avaliar).not.toHaveBeenCalled()
  })

  // US-024: 409 duplicada
  it('US-024: 2a avaliacao -> exibe 409 AVALIACAO_DUPLICADA', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoRealizadoComReputacao)
    vi.mocked(eventsApi.avaliar).mockRejectedValueOnce({
      response: { status: 409, data: { message: 'AVALIACAO_DUPLICADA' } },
    })

    const user = userEvent.setup()
    renderWithProviders(<AvaliacaoEvento eventoId={10} />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /festival de jazz/i })).toBeInTheDocument()
    })

    const notaInput = screen.getAllByRole('radio').find(el =>
      el.getAttribute('value') === '5'
    ) || screen.getByLabelText(/5 estrelas?|nota 5/i)
    await user.click(notaInput as Element)
    await user.click(screen.getByRole('button', { name: /avaliar|enviar avaliacao/i }))

    await waitFor(() => {
      expect(
        screen.getByText(/ja avaliou|avaliacao_duplicada|voce ja avaliou/i)
      ).toBeInTheDocument()
    })
  })

  // US-024: 403 nao-elegivel
  it('US-024: nao-elegivel -> exibe 403 AVALIACAO_NAO_ELEGIVEL', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoRealizadoComReputacao)
    vi.mocked(eventsApi.avaliar).mockRejectedValueOnce({
      response: { status: 403, data: { message: 'AVALIACAO_NAO_ELEGIVEL' } },
    })

    const user = userEvent.setup()
    renderWithProviders(<AvaliacaoEvento eventoId={10} />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /festival de jazz/i })).toBeInTheDocument()
    })

    const notaInput = screen.getAllByRole('radio')[0]
    await user.click(notaInput)
    await user.click(screen.getByRole('button', { name: /avaliar|enviar avaliacao/i }))

    await waitFor(() => {
      expect(
        screen.getByText(/nao elegivel|nao participou|avaliacao_nao_elegivel/i)
      ).toBeInTheDocument()
    })
  })
})
