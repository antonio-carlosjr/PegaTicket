import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '@/test/utils'
import * as paymentsApi from '@/api/payments'
import { MeusIngressosPendentes } from '../MeusIngressosPendentes'

/**
 * E — Frontend: extrato de pagamentos reflete status REPASSADO / REEMBOLSADO.
 * Casos: E (tests-spec.md, US-043/US-042).
 *
 * VERMELHO:
 * - PagamentoResponse nao tem eventoId/promotorId (TECH-S4-01).
 * - A tela que lista pagamentos (MeusIngressosPendentes ou extrato) nao exibe badges
 *   para os novos status REPASSADO/REEMBOLSADO.
 * - A funcao listarMeusPagamentos nao retorna esses campos.
 */

vi.mock('@/api/payments', async () => {
  const real = await vi.importActual<typeof import('@/api/payments')>('@/api/payments')
  return {
    ...real,
    listarMeusPagamentos: vi.fn(),
  }
})

// Fixtures de pagamentos com os novos status (Sprint 5A)
const pagamentoRepassado: paymentsApi.PagamentoResponse = {
  id: 1,
  inscricaoId: 10,
  usuarioId: 5,
  valorBruto: '100.00',
  valorTaxa: '10.00',
  valorRepasse: '90.00',
  status: 'REPASSADO',
  gateway: 'SIMULADO',
  gatewayPaymentId: 'SIM-abc',
  processadoEm: '2026-06-30T10:00:00-03:00',
  criadoEm: '2026-06-29T10:00:00-03:00',
  // TECH-S4-01 — campos novos; VERMELHO se PagamentoResponse nao os tiver
  eventoId: 42,
  promotorId: 7,
}

const pagamentoReembolsado: paymentsApi.PagamentoResponse = {
  id: 2,
  inscricaoId: 11,
  usuarioId: 5,
  valorBruto: '150.00',
  valorTaxa: '15.00',
  valorRepasse: '135.00',
  status: 'REEMBOLSADO',
  gateway: 'SIMULADO',
  gatewayPaymentId: 'SIM-def',
  processadoEm: '2026-06-30T10:01:00-03:00',
  criadoEm: '2026-06-29T10:01:00-03:00',
  eventoId: 43,
  promotorId: 7,
}

describe('Extrato de pagamentos — status REPASSADO e REEMBOLSADO', () => {
  beforeEach(() => {
    vi.mocked(paymentsApi.listarMeusPagamentos).mockReset()
  })

  /**
   * E — extrato_mostraStatusRepassado [US-043]
   * GET /payments/me retorna status=REPASSADO → badge "Repassado" + valor visivel.
   */
  it('E — extrato exibe badge "Repassado" e valor quando status=REPASSADO', async () => {
    vi.mocked(paymentsApi.listarMeusPagamentos).mockResolvedValue([pagamentoRepassado])

    // Renderiza a tela de extrato (nome pode variar — ajustar quando a tela existir)
    // VERMELHO: MeusIngressosPendentes pode nao ter esse extrato ainda
    renderWithProviders(<MeusIngressosPendentes />)

    await waitFor(() => {
      // Badge com texto "Repassado" (case-insensitive)
      expect(screen.getByText(/repassado/i)).toBeInTheDocument()
    })

    // Valor do repasse visivel
    expect(screen.getByText(/90[,.]00/)).toBeInTheDocument()
  })

  /**
   * E — extrato_mostraStatusReembolsado [US-042]
   * GET /payments/me retorna status=REEMBOLSADO → badge "Reembolsado".
   */
  it('E — extrato exibe badge "Reembolsado" quando status=REEMBOLSADO', async () => {
    vi.mocked(paymentsApi.listarMeusPagamentos).mockResolvedValue([pagamentoReembolsado])

    renderWithProviders(<MeusIngressosPendentes />)

    await waitFor(() => {
      expect(screen.getByText(/reembolsado/i)).toBeInTheDocument()
    })

    // Valor bruto (reembolso e do valor bruto)
    expect(screen.getByText(/150[,.]00/)).toBeInTheDocument()
  })

  /**
   * E — extrato_mostraAmbosStatus
   * Lista com 1 REPASSADO e 1 REEMBOLSADO exibe ambos os badges.
   */
  it('E — extrato exibe ambos badges em lista mista', async () => {
    vi.mocked(paymentsApi.listarMeusPagamentos).mockResolvedValue([
      pagamentoRepassado,
      pagamentoReembolsado,
    ])

    renderWithProviders(<MeusIngressosPendentes />)

    await waitFor(() => {
      expect(screen.getByText(/repassado/i)).toBeInTheDocument()
      expect(screen.getByText(/reembolsado/i)).toBeInTheDocument()
    })
  })
})
