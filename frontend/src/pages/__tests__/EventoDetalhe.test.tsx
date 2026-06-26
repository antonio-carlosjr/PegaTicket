import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '@/hooks/useAuth'
import { Toaster } from '@/components/ui/toaster'
import { EventoDetalhe } from '../EventoDetalhe'
import * as eventsApi from '@/api/events'
import * as ticketsApi from '@/api/tickets'

vi.mock('@/api/events', async () => {
  const real = await vi.importActual<typeof import('@/api/events')>('@/api/events')
  return { ...real, detalheEvento: vi.fn() }
})

vi.mock('@/api/tickets', async () => {
  const real = await vi.importActual<typeof import('@/api/tickets')>('@/api/tickets')
  return { ...real, inscrever: vi.fn() }
})

/** Renderiza EventoDetalhe com a rota /eventos/:id corretamente configurada. */
function renderDetalhe(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/eventos/:id" element={<EventoDetalhe />} />
          <Route path="/meus-ingressos" element={<div>Meus ingressos</div>} />
        </Routes>
        <Toaster />
      </AuthProvider>
    </MemoryRouter>
  )
}

const eventoGratuitoPublicado: eventsApi.EventoResponse = {
  id: 1,
  titulo: 'Workshop Gratuito',
  descricao: 'Aprenda React.',
  dataInicio: '2026-09-01T09:00:00-03:00',
  dataFim: '2026-09-01T18:00:00-03:00',
  local: 'Online',
  tipo: 'GRATUITO',
  status: 'PUBLICADO',
  capacidade: 100,
  vagasDisponiveis: 50,
  preco: null,
  prazoReembolsoDias: null,
  imagemUrl: null,
  promotorId: 3,
  criadoEm: '2026-06-01T10:00:00-03:00',
  atualizadoEm: '2026-06-01T10:00:00-03:00',
}

const eventoCompleto: eventsApi.EventoResponse = {
  id: 42,
  titulo: 'Show do Ano',
  descricao: 'O melhor show do ano.',
  dataInicio: '2026-08-15T20:00:00-03:00',
  dataFim: '2026-08-15T23:30:00-03:00',
  local: 'Arena São Paulo',
  tipo: 'PAGO',
  status: 'PUBLICADO',
  capacidade: 10000,
  vagasDisponiveis: 9500,
  preco: '150.00',
  prazoReembolsoDias: 7,
  imagemUrl: null,
  promotorId: 5,
  criadoEm: '2026-06-01T10:00:00-03:00',
  atualizadoEm: '2026-06-01T10:00:00-03:00',
}

describe('<EventoDetalhe>', () => {
  beforeEach(() => {
    vi.mocked(eventsApi.detalheEvento).mockReset()
    vi.mocked(ticketsApi.inscrever).mockReset()
  })

  it('exibe detalhe do evento publicado', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoCompleto)

    renderDetalhe('/eventos/42')

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /show do ano/i })).toBeInTheDocument()
    })

    expect(screen.getByText('Arena São Paulo')).toBeInTheDocument()
  })

  it('exibe erro amigavel quando evento nao encontrado (404)', async () => {
    vi.mocked(eventsApi.detalheEvento).mockRejectedValue({
      response: { status: 404, data: { message: 'Evento não encontrado.' } },
    })

    renderDetalhe('/eventos/999')

    await waitFor(() => {
      expect(
        screen.getByText(/evento nao encontrado ou nao disponivel/i)
      ).toBeInTheDocument()
    })
  })

  it('exibe botao Inscrever-se para evento GRATUITO e PUBLICADO', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoGratuitoPublicado)

    renderDetalhe('/eventos/1')

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /workshop gratuito/i })).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: /inscrever-se neste evento/i })).toBeInTheDocument()
  })

  it('nao exibe botao Inscrever-se para evento PAGO', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoCompleto)

    renderDetalhe('/eventos/42')

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /show do ano/i })).toBeInTheDocument()
    })

    expect(screen.queryByRole('button', { name: /inscrever-se/i })).not.toBeInTheDocument()
    // Texto real: "Inscrições pagas disponíveis em breve."
    expect(
      screen.getByText('Inscrições pagas disponíveis em breve.')
    ).toBeInTheDocument()
  })

  it('happy path: inscricao bem-sucedida exibe QR e toast', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoGratuitoPublicado)
    vi.mocked(ticketsApi.inscrever).mockResolvedValue({
      id: 10,
      eventoId: 1,
      status: 'ATIVA',
      inscritoEm: '2026-06-26T12:00:00-03:00',
      ingresso: {
        id: 5,
        inscricaoId: 10,
        codigoUnico: '550e8400-e29b-41d4-a716-446655440000',
        status: 'ATIVO',
        emitidoEm: '2026-06-26T12:00:00-03:00',
      },
    })

    const user = userEvent.setup()
    renderDetalhe('/eventos/1')

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /inscrever-se neste evento/i })).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: /inscrever-se neste evento/i }))

    // Aguarda o card de ingresso aparecer (o QR é renderizado a partir do codigoUnico)
    await waitFor(() => {
      expect(screen.getByLabelText(/qr code do ingresso/i)).toBeInTheDocument()
    })
  })
})
