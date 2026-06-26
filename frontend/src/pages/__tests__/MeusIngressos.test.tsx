import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { MeusIngressos } from '../MeusIngressos'
import { renderWithProviders } from '@/test/utils'
import * as ticketsApi from '@/api/tickets'
import * as eventsApi from '@/api/events'

vi.mock('@/api/tickets', async () => {
  const real = await vi.importActual<typeof import('@/api/tickets')>('@/api/tickets')
  return { ...real, meusIngressos: vi.fn() }
})

vi.mock('@/api/events', async () => {
  const real = await vi.importActual<typeof import('@/api/events')>('@/api/events')
  return { ...real, detalheEvento: vi.fn() }
})

const ingressoAtivo: ticketsApi.MeuIngressoResponse = {
  ingressoId: 1,
  codigoUnico: '550e8400-e29b-41d4-a716-446655440000',
  statusIngresso: 'ATIVO',
  inscricaoId: 10,
  eventoId: 42,
  statusInscricao: 'ATIVA',
  emitidoEm: '2026-06-20T15:00:00-03:00',
}

const eventoDetalhado: eventsApi.EventoResponse = {
  id: 42,
  titulo: 'Festival Tech 2026',
  descricao: 'Evento de tecnologia.',
  dataInicio: '2026-08-10T09:00:00-03:00',
  dataFim: '2026-08-10T18:00:00-03:00',
  local: 'Centro de Eventos SP',
  tipo: 'GRATUITO',
  status: 'PUBLICADO',
  capacidade: 500,
  vagasDisponiveis: 100,
  preco: null,
  prazoReembolsoDias: null,
  imagemUrl: null,
  promotorId: 5,
  criadoEm: '2026-05-01T10:00:00-03:00',
  atualizadoEm: '2026-05-01T10:00:00-03:00',
}

describe('<MeusIngressos>', () => {
  beforeEach(() => {
    vi.mocked(ticketsApi.meusIngressos).mockReset()
    vi.mocked(eventsApi.detalheEvento).mockReset()
  })

  it('happy path: exibe ingresso com nome do evento e QR', async () => {
    vi.mocked(ticketsApi.meusIngressos).mockResolvedValue([ingressoAtivo])
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoDetalhado)

    renderWithProviders(<MeusIngressos />)

    await waitFor(() => {
      expect(screen.getByText('Festival Tech 2026')).toBeInTheDocument()
    })

    // Badge de status ATIVO
    expect(screen.getByText('Ativo')).toBeInTheDocument()

    // Local do evento
    expect(screen.getByText('Centro de Eventos SP')).toBeInTheDocument()

    // QR code renderizado (SVG gerado pelo qrcode.react)
    expect(screen.getByRole('img', { hidden: true })).toBeDefined()
  })

  it('exibe estado vazio quando usuario nao tem ingressos', async () => {
    vi.mocked(ticketsApi.meusIngressos).mockResolvedValue([])
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoDetalhado)

    renderWithProviders(<MeusIngressos />)

    await waitFor(() => {
      expect(
        screen.getByText('Você ainda não se inscreveu em nenhum evento')
      ).toBeInTheDocument()
    })

    expect(screen.getByRole('link', { name: /explorar eventos/i })).toBeInTheDocument()
  })

  it('exibe dados parciais quando event-service falha para um ingresso', async () => {
    vi.mocked(ticketsApi.meusIngressos).mockResolvedValue([ingressoAtivo])
    vi.mocked(eventsApi.detalheEvento).mockRejectedValue(new Error('Service unavailable'))

    renderWithProviders(<MeusIngressos />)

    await waitFor(() => {
      // Exibe o fallback com o ID do evento
      expect(screen.getByText('Evento #42')).toBeInTheDocument()
    })

    expect(
      screen.getByText('Não foi possível carregar detalhes do evento.')
    ).toBeInTheDocument()
  })
})
