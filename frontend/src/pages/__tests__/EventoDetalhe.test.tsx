import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { render } from '@testing-library/react'
import { Route, Routes, MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '@/hooks/useAuth'
import { Toaster } from '@/components/ui/toaster'
import { EventoDetalhe } from '../EventoDetalhe'
import * as eventsApi from '@/api/events'

vi.mock('@/api/events', async () => {
  const real = await vi.importActual<typeof import('@/api/events')>('@/api/events')
  return { ...real, detalheEvento: vi.fn() }
})

/** Renderiza EventoDetalhe com a rota /eventos/:id corretamente configurada. */
function renderDetalhe(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/eventos/:id" element={<EventoDetalhe />} />
        </Routes>
        <Toaster />
      </AuthProvider>
    </MemoryRouter>
  )
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
  })

  it('exibe detalhe do evento publicado', async () => {
    vi.mocked(eventsApi.detalheEvento).mockResolvedValue(eventoCompleto)

    renderDetalhe('/eventos/42')

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /show do ano/i })).toBeInTheDocument()
    })

    expect(screen.getByText('Arena São Paulo')).toBeInTheDocument()
    // Sem botão de inscrição (Sprint 3)
    expect(screen.queryByRole('button', { name: /inscrever/i })).not.toBeInTheDocument()
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
})
