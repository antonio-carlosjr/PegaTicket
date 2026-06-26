import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { MinhasInscricoes } from '../MinhasInscricoes'
import { renderWithProviders } from '@/test/utils'
import * as ticketsApi from '@/api/tickets'
import type { Page } from '@/api/events'

vi.mock('@/api/tickets', async () => {
  const real = await vi.importActual<typeof import('@/api/tickets')>('@/api/tickets')
  return { ...real, historicoInscricoes: vi.fn() }
})

function paginaVazia(): Page<ticketsApi.InscricaoHistoricoResponse> {
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }
}

function paginaComItens(
  itens: ticketsApi.InscricaoHistoricoResponse[]
): Page<ticketsApi.InscricaoHistoricoResponse> {
  return { content: itens, totalElements: itens.length, totalPages: 1, number: 0, size: 20 }
}

const inscricaoAtiva: ticketsApi.InscricaoHistoricoResponse = {
  id: 10,
  eventoId: 42,
  status: 'ATIVA',
  inscritoEm: '2026-06-20T15:00:00-03:00',
}

const inscricaoCancelada: ticketsApi.InscricaoHistoricoResponse = {
  id: 11,
  eventoId: 99,
  status: 'CANCELADA',
  inscritoEm: '2026-05-10T10:00:00-03:00',
}

describe('<MinhasInscricoes>', () => {
  beforeEach(() => {
    vi.mocked(ticketsApi.historicoInscricoes).mockReset()
  })

  it('happy path: exibe lista de inscricoes com status correto', async () => {
    vi.mocked(ticketsApi.historicoInscricoes).mockResolvedValue(
      paginaComItens([inscricaoAtiva, inscricaoCancelada])
    )

    renderWithProviders(<MinhasInscricoes />)

    await waitFor(() => {
      expect(screen.getByText('Inscrição #10')).toBeInTheDocument()
    })

    expect(screen.getByText('Inscrição #11')).toBeInTheDocument()
    expect(screen.getByText('Ativa')).toBeInTheDocument()
    expect(screen.getByText('Cancelada')).toBeInTheDocument()
  })

  it('exibe estado vazio quando usuario nao tem inscricoes', async () => {
    vi.mocked(ticketsApi.historicoInscricoes).mockResolvedValue(paginaVazia())

    renderWithProviders(<MinhasInscricoes />)

    await waitFor(() => {
      expect(
        screen.getByText('Você ainda não se inscreveu em nenhum evento.')
      ).toBeInTheDocument()
    })
  })
})
