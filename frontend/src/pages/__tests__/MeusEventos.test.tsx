import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { MeusEventos } from '../MeusEventos'
import { renderWithProviders } from '@/test/utils'
import * as eventsApi from '@/api/events'

vi.mock('@/api/events', async () => {
  const real = await vi.importActual<typeof import('@/api/events')>('@/api/events')
  return { ...real, meusEventos: vi.fn() }
})

const rascunho: eventsApi.EventoResumo = {
  id: 10,
  titulo: 'Meu Evento Rascunho',
  dataInicio: '2026-09-01T10:00:00-03:00',
  dataFim: '2026-09-01T18:00:00-03:00',
  local: 'Centro de Convenções',
  tipo: 'GRATUITO',
  status: 'RASCUNHO',
  preco: null,
  capacidade: 100,
  imagemUrl: null,
}

const publicado: eventsApi.EventoResumo = {
  ...rascunho,
  id: 11,
  titulo: 'Meu Evento Publicado',
  status: 'PUBLICADO',
  capacidade: 100,
}

describe('<MeusEventos>', () => {
  beforeEach(() => {
    vi.mocked(eventsApi.meusEventos).mockReset()
  })

  it('exibe lista de eventos do promotor com badges de status', async () => {
    vi.mocked(eventsApi.meusEventos).mockResolvedValue({
      content: [rascunho, publicado],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 100,
    })

    renderWithProviders(<MeusEventos />, { routerProps: { initialEntries: ['/meus-eventos'] } })

    await waitFor(() => {
      expect(screen.getByText('Meu Evento Rascunho')).toBeInTheDocument()
    })
    expect(screen.getByText('Meu Evento Publicado')).toBeInTheDocument()
    expect(screen.getByText('Rascunho')).toBeInTheDocument()
    expect(screen.getByText('Publicado')).toBeInTheDocument()
  })

  it('R1: botao Editar aparece apenas para RASCUNHO', async () => {
    vi.mocked(eventsApi.meusEventos).mockResolvedValue({
      content: [rascunho, publicado],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 100,
    })

    renderWithProviders(<MeusEventos />, { routerProps: { initialEntries: ['/meus-eventos'] } })

    await waitFor(() => {
      expect(screen.getByText('Meu Evento Rascunho')).toBeInTheDocument()
    })

    // Apenas 1 botão Editar (para o rascunho)
    const botoesEditar = screen.getAllByRole('link', { name: /editar/i })
    expect(botoesEditar).toHaveLength(1)
  })

  it('R3: vagas null (RASCUNHO) exibe texto amigavel', async () => {
    vi.mocked(eventsApi.meusEventos).mockResolvedValue({
      content: [rascunho],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 100,
    })

    renderWithProviders(<MeusEventos />, { routerProps: { initialEntries: ['/meus-eventos'] } })

    await waitFor(() => {
      expect(screen.getByText('Meu Evento Rascunho')).toBeInTheDocument()
    })

    expect(screen.getByText('Disponível após publicar')).toBeInTheDocument()
    expect(screen.queryByText('null')).not.toBeInTheDocument()
  })

  it('exibe estado vazio quando promotor nao tem eventos', async () => {
    vi.mocked(eventsApi.meusEventos).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 100,
    })

    renderWithProviders(<MeusEventos />, { routerProps: { initialEntries: ['/meus-eventos'] } })

    await waitFor(() => {
      expect(screen.getByText(/voce ainda nao criou nenhum evento/i)).toBeInTheDocument()
    })
  })
})
