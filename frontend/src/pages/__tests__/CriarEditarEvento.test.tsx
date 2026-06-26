import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CriarEditarEvento } from '../CriarEditarEvento'
import { renderWithProviders } from '@/test/utils'
import * as eventsApi from '@/api/events'

vi.mock('@/api/events', async () => {
  const real = await vi.importActual<typeof import('@/api/events')>('@/api/events')
  return { ...real, criarEvento: vi.fn(), detalheEvento: vi.fn() }
})

vi.mock('react-router-dom', async () => {
  const real = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...real, useNavigate: () => vi.fn(), useParams: () => ({}) }
})

describe('<CriarEditarEvento>', () => {
  beforeEach(() => {
    vi.mocked(eventsApi.criarEvento).mockReset()
  })

  it('renderiza a etapa 1 com campos de titulo e descricao', () => {
    renderWithProviders(<CriarEditarEvento />, {
      routerProps: { initialEntries: ['/eventos/novo'] },
    })

    expect(screen.getByRole('heading', { name: /criar evento/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/título/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/descrição/i)).toBeInTheDocument()
  })

  it('avanca para etapa 2 apos preencher titulo', async () => {
    renderWithProviders(<CriarEditarEvento />, {
      routerProps: { initialEntries: ['/eventos/novo'] },
    })

    await userEvent.type(screen.getByLabelText(/título/i), 'Meu Evento Teste')
    await userEvent.click(screen.getByRole('button', { name: /próximo/i }))

    await waitFor(() => {
      expect(screen.getByLabelText(/início/i)).toBeInTheDocument()
    })
  })

  it('nao avanca sem preencher titulo (validacao inline)', async () => {
    renderWithProviders(<CriarEditarEvento />, {
      routerProps: { initialEntries: ['/eventos/novo'] },
    })

    await userEvent.click(screen.getByRole('button', { name: /próximo/i }))

    await waitFor(() => {
      expect(screen.getByText(/titulo e obrigatorio/i)).toBeInTheDocument()
    })

    // Ainda na etapa 1
    expect(screen.getByLabelText(/título/i)).toBeInTheDocument()
  })
})
