import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { MeusIngressos } from '../MeusIngressos'
import { renderWithProviders } from '@/test/utils'
import * as ticketsApi from '@/api/tickets'
import * as eventsApi from '@/api/events'

/**
 * C3 — Estados de "Meus ingressos" para inscricao PENDENTE_PAGAMENTO.
 * C2.b — Regressao: evento GRATUITO nao passa por checkout.
 * Casos de teste: C3 (tests-spec.md), C2.b (regressao S3)
 */

vi.mock('@/api/tickets', async () => {
  const real = await vi.importActual<typeof import('@/api/tickets')>('@/api/tickets')
  return { ...real, meusIngressos: vi.fn() }
})

vi.mock('@/api/events', async () => {
  const real = await vi.importActual<typeof import('@/api/events')>('@/api/events')
  return { ...real, detalheEvento: vi.fn() }
})

const eventoPago: eventsApi.EventoResponse = {
  id: 42,
  titulo: 'Show Pago',
  descricao: 'Um show top.',
  dataInicio: '2026-08-15T20:00:00-03:00',
  dataFim: '2026-08-15T23:30:00-03:00',
  local: 'Arena SP',
  tipo: 'PAGO',
  status: 'PUBLICADO',
  capacidade: 1000,
  vagasDisponiveis: 500,
  preco: '150.00',
  prazoReembolsoDias: 7,
  imagemUrl: null,
  promotorId: 5,
  criadoEm: '2026-06-01T10:00:00-03:00',
  atualizadoEm: '2026-06-01T10:00:00-03:00',
}

const eventoGratuito: eventsApi.EventoResponse = {
  ...eventoPago,
  tipo: 'GRATUITO',
  preco: null,
  titulo: 'Workshop Gratis',
}

/**
 * Inscricao PENDENTE_PAGAMENTO sem ingresso (estado intermediario do ramo PAGO).
 * MeusIngressos deve mostrar "aguardando confirmacao" em vez de QR.
 */
const inscricaoPendenteSemIngresso: ticketsApi.MeuIngressoResponse = {
  ingressoId: 0,         // sem ingresso: id invalido
  codigoUnico: '',        // vazio
  statusIngresso: '',     // sem status de ingresso
  inscricaoId: 10,
  eventoId: 42,
  statusInscricao: 'PENDENTE_PAGAMENTO',  // NOVO estado S4
  emitidoEm: '',
}

const ingressoAtivo: ticketsApi.MeuIngressoResponse = {
  ingressoId: 1,
  codigoUnico: '550e8400-e29b-41d4-a716-446655440000',
  statusIngresso: 'ATIVO',
  inscricaoId: 11,
  eventoId: 42,
  statusInscricao: 'ATIVA',
  emitidoEm: '2026-06-30T10:00:00-03:00',
}

describe('<MeusIngressos> — estados de inscricao PENDENTE_PAGAMENTO', () => {
  beforeEach(() => {
    vi.mocked(ticketsApi.meusIngressos).mockReset()
    vi.mocked(eventsApi.detalheEvento).mockReset()
  })

  /**
   * C3 — inscricaoPendente_semIngresso_mostraAguardando
   * Inscricao com status PENDENTE_PAGAMENTO sem ingresso deve mostrar
   * "aguardando confirmacao de pagamento" (nao mostrar QR).
   */
  it('C3 — inscricaoPendente_semIngresso_mostraAguardando', async () => {
    vi.mocked(ticketsApi.meusIngressos).mockResolvedValue([inscricaoPendenteSemIngresso])
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoPago)

    renderWithProviders(<MeusIngressos />)

    await waitFor(() => {
      expect(screen.getByText(/aguardando confirmacao de pagamento|pagamento pendente/i))
        .toBeInTheDocument()
    })

    // NAO deve mostrar QR code
    expect(screen.queryByRole('img', { hidden: true })).not.toBeInTheDocument()

    // NAO deve mostrar codigo unico vazio como QR
    expect(screen.queryByText(/550e8400/)).not.toBeInTheDocument()
  })

  /**
   * C3 variante — inscricao PENDENTE_PAGAMENTO com link para checkout
   */
  it('C3 — inscricaoPendente_exibeLinkParaCheckout', async () => {
    vi.mocked(ticketsApi.meusIngressos).mockResolvedValue([inscricaoPendenteSemIngresso])
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoPago)

    renderWithProviders(<MeusIngressos />)

    await waitFor(() => {
      expect(screen.getByText(/show pago/i)).toBeInTheDocument()
    })

    // Deve haver um link/botao para ir ao checkout
    const linkCheckout = screen.queryByRole('link', { name: /pagar|finalizar pagamento|ir para o checkout/i })
        || screen.queryByRole('button', { name: /pagar|finalizar pagamento|ir para o checkout/i })
    expect(linkCheckout).toBeInTheDocument()
  })

  /**
   * C2.b — Regressao: inscricao ATIVA (GRATUITO) exibe QR normalmente.
   * O fluxo GRATUITO nao deve ter sido quebrado pela S4.
   */
  it('C2.b — regressao GRATUITO: inscricao ATIVA exibe QR (fluxo S3 intacto)', async () => {
    vi.mocked(ticketsApi.meusIngressos).mockResolvedValue([ingressoAtivo])
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoGratuito)

    renderWithProviders(<MeusIngressos />)

    await waitFor(() => {
      expect(screen.getByText(/workshop gratis/i)).toBeInTheDocument()
    })

    // QR code deve estar presente (inscricao ATIVA com ingresso)
    expect(screen.getByRole('img', { hidden: true })).toBeDefined()

    // NAO deve mostrar "aguardando confirmacao" para inscricao ATIVA
    expect(
      screen.queryByText(/aguardando confirmacao de pagamento/i)
    ).not.toBeInTheDocument()
  })
})
