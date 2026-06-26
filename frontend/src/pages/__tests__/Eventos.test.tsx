import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Eventos } from '../Eventos'
import { renderWithProviders } from '@/test/utils'
import * as eventsApi from '@/api/events'

vi.mock('@/api/events', async () => {
  const real = await vi.importActual<typeof import('@/api/events')>('@/api/events')
  return { ...real, listarEventos: vi.fn() }
})

const eventoPublicado: eventsApi.EventoResumo = {
  id: 1,
  titulo: 'Festival de Inverno',
  dataInicio: '2026-07-10T18:00:00-03:00',
  dataFim: '2026-07-10T23:00:00-03:00',
  local: 'Parque da Cidade, São Paulo',
  tipo: 'GRATUITO',
  status: 'PUBLICADO',
  preco: null,
  capacidade: 500,
  imagemUrl: null,
}

const paginaVazia: eventsApi.Page<eventsApi.EventoResumo> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
}

describe('<Eventos>', () => {
  beforeEach(() => {
    vi.mocked(eventsApi.listarEventos).mockReset()
  })

  it('exibe evento publicado retornado pela API', async () => {
    vi.mocked(eventsApi.listarEventos).mockResolvedValue({
      content: [eventoPublicado],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    })

    renderWithProviders(<Eventos />, { routerProps: { initialEntries: ['/eventos'] } })

    // Cabeçalho visível imediatamente
    expect(screen.getByRole('heading', { name: /eventos/i })).toBeInTheDocument()

    // Aguarda o conteúdo carregar
    await waitFor(() => {
      expect(screen.getByText('Festival de Inverno')).toBeInTheDocument()
    })

    expect(screen.getByText('Parque da Cidade, São Paulo')).toBeInTheDocument()
    // Badge de tipo e campo preço ambos mostram "Gratuito" — verifica que pelo menos um existe
    expect(screen.getAllByText('Gratuito').length).toBeGreaterThanOrEqual(1)
    // Link de detalhe presente
    expect(screen.getByRole('link', { name: /ver detalhes/i })).toBeInTheDocument()
  })

  it('exibe estado vazio quando nao ha eventos', async () => {
    vi.mocked(eventsApi.listarEventos).mockResolvedValue(paginaVazia)

    renderWithProviders(<Eventos />, { routerProps: { initialEntries: ['/eventos'] } })

    await waitFor(() => {
      expect(screen.getByText(/nenhum evento encontrado/i)).toBeInTheDocument()
    })
  })

  // Regressao P1: o filtro de data vinha de <input type="datetime-local"> sem offset
  // ("2026-06-20T14:00") e era enviado cru → backend respondia 500. Deve ir como ISO offset.
  it('converte filtro de data (datetime-local) para ISO com offset antes de chamar a API', async () => {
    vi.mocked(eventsApi.listarEventos).mockResolvedValue(paginaVazia)

    renderWithProviders(<Eventos />, {
      routerProps: { initialEntries: ['/eventos?de=2026-06-20T14:00'] },
    })

    await waitFor(() => {
      expect(eventsApi.listarEventos).toHaveBeenCalled()
    })

    const arg = vi.mocked(eventsApi.listarEventos).mock.calls[0][0]
    expect(arg?.de).toBeDefined()
    // ISO offset: termina em Z ou tem +HH:MM / -HH:MM. O valor cru "2026-06-20T14:00" nao tem.
    expect(arg?.de).toMatch(/(Z|[+-]\d{2}:\d{2})$/)
    expect(arg?.de).not.toBe('2026-06-20T14:00')
  })
})
