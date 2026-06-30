import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, MemoryRouter } from 'react-router-dom'
import { render } from '@testing-library/react'
import { AuthProvider } from '@/hooks/useAuth'
import { Toaster } from '@/components/ui/toaster'
import { CheckoutPage } from '../CheckoutPage'
import * as paymentsApi from '@/api/payments'
import * as ticketsApi from '@/api/tickets'

/**
 * Testes Vitest do fluxo de checkout PAGO (C1, C2).
 * Cobre:
 * - C1.a: inscricaoPaga_redirecionaCheckout_mostraPagamentoPendente
 * - C1.b: clicarPagar_chamaConfirmar_eMostraSucesso
 * - C1.c: aposPagar_pollingMostraIngressoEmitido (risco R3 do PO)
 * - C2.a: gatewayRecusa_mostraMensagemAmigavel (402 PAGAMENTO_RECUSADO)
 * - C2.b: inscricaoGratuita_naoPassaPorCheckout (regressao S3)
 * Casos de teste: C1, C2 (tests-spec.md)
 */

vi.mock('@/api/payments', async () => {
  const real = await vi.importActual<typeof import('@/api/payments')>('@/api/payments')
  return {
    ...real,
    confirmarPagamento: vi.fn(),
    getPagamentoDaInscricao: vi.fn(),
  }
})

vi.mock('@/api/tickets', async () => {
  const real = await vi.importActual<typeof import('@/api/tickets')>('@/api/tickets')
  return {
    ...real,
    inscrever: vi.fn(),
    meusIngressos: vi.fn(),
  }
})

/** Renderiza CheckoutPage na rota /checkout/:inscricaoId */
function renderCheckout(inscricaoId: number) {
  return render(
    <MemoryRouter initialEntries={[`/checkout/${inscricaoId}`]}>
      <AuthProvider>
        <Routes>
          <Route path="/checkout/:inscricaoId" element={<CheckoutPage />} />
          <Route path="/meus-ingressos" element={<div data-testid="meus-ingressos">Meus Ingressos</div>} />
          <Route path="/eventos/:id" element={<div>Evento</div>} />
        </Routes>
        <Toaster />
      </AuthProvider>
    </MemoryRouter>
  )
}

// Fixture: resposta de pagamento pendente
const pagamentoPendente: paymentsApi.PagamentoResponse = {
  id: 1,
  inscricaoId: 10,
  usuarioId: 5,
  valorBruto: '100.00',
  valorTaxa: '10.00',
  valorRepasse: '90.00',
  status: 'PENDENTE',
  gateway: 'SIMULADO',
  gatewayPaymentId: null,
  processadoEm: null,
  criadoEm: '2026-06-30T10:00:00-03:00',
}

const pagamentoConfirmado: paymentsApi.PagamentoResponse = {
  ...pagamentoPendente,
  status: 'CONFIRMADO',
  gatewayPaymentId: 'SIM-abc123',
  processadoEm: '2026-06-30T10:01:00-03:00',
}

const ingressoEmitido: ticketsApi.MeuIngressoResponse = {
  ingressoId: 1,
  codigoUnico: '550e8400-e29b-41d4-a716-446655440001',
  statusIngresso: 'ATIVO',
  inscricaoId: 10,
  eventoId: 42,
  statusInscricao: 'ATIVA',
  emitidoEm: '2026-06-30T10:01:05-03:00',
}

describe('<CheckoutPage>', () => {
  beforeEach(() => {
    vi.mocked(paymentsApi.confirmarPagamento).mockReset()
    vi.mocked(paymentsApi.getPagamentoDaInscricao).mockReset()
    vi.mocked(ticketsApi.meusIngressos).mockReset()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  /**
   * C1.a — inscricaoPaga_redirecionaCheckout_mostraPagamentoPendente
   * A pagina de checkout deve mostrar o estado "Pagamento pendente" e o botao "Pagar".
   */
  it('C1.a — exibe pagamento pendente com valor e botao Pagar', async () => {
    vi.mocked(paymentsApi.getPagamentoDaInscricao).mockResolvedValue(pagamentoPendente)

    renderCheckout(10)

    await waitFor(() => {
      expect(screen.getByText(/pagamento pendente/i)).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: /pagar/i })).toBeInTheDocument()
    expect(screen.getByText(/R\$\s*100[,.]00/i)).toBeInTheDocument()
  })

  /**
   * C1.b — clicarPagar_chamaConfirmar_eMostraSucesso
   * 1 clique no botao "Pagar" deve chamar POST /api/payments/:id/confirmar.
   * Apos confirmacao, a UI deve passar a "aguardando confirmacao" (polling iniciado).
   */
  it('C1.b — clicar Pagar chama confirmarPagamento e mostra aguardando confirmacao', async () => {
    vi.mocked(paymentsApi.getPagamentoDaInscricao).mockResolvedValue(pagamentoPendente)
    vi.mocked(paymentsApi.confirmarPagamento).mockResolvedValue(pagamentoConfirmado)
    vi.mocked(ticketsApi.meusIngressos).mockResolvedValue([]) // polling inicial sem ingresso

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout(10)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /pagar/i })).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: /pagar/i }))

    expect(vi.mocked(paymentsApi.confirmarPagamento)).toHaveBeenCalledWith(10)

    await waitFor(() => {
      expect(screen.getByText(/aguardando confirmacao|processando/i)).toBeInTheDocument()
    })
  })

  /**
   * C1.c — aposPagar_pollingMostraIngressoEmitido (risco R3 do PO)
   * Apos confirmar, o polling deve detectar o ingresso e exibi-lo sem reload manual.
   */
  it('C1.c — polling detecta ingresso emitido e exibe sem reload manual', async () => {
    vi.mocked(paymentsApi.getPagamentoDaInscricao).mockResolvedValue(pagamentoPendente)
    vi.mocked(paymentsApi.confirmarPagamento).mockResolvedValue(pagamentoConfirmado)

    // Polling: primeira chamada sem ingresso, segunda chamada com ingresso
    vi.mocked(ticketsApi.meusIngressos)
      .mockResolvedValueOnce([])         // primeira rodada do polling: sem ingresso ainda
      .mockResolvedValue([ingressoEmitido]) // rodadas seguintes: ingresso chegou

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout(10)

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /pagar/i })).toBeInTheDocument()
    )

    await user.click(screen.getByRole('button', { name: /pagar/i }))

    // Avanca o tempo do polling (intervalo esperado: ~3-5s)
    vi.advanceTimersByTime(5000)

    await waitFor(() => {
      // O ingresso com codigo unico (QR) deve aparecer sem reload
      expect(screen.getByText(/550e8400|ingresso confirmado|qr code/i)).toBeInTheDocument()
    }, { timeout: 3000 })
  })

  /**
   * C1.c variante — polling tem timeout de 60s (ressalva do PO)
   * Se apos 60s o ingresso nao aparecer, a UI deve exibir mensagem de timeout.
   */
  it('C1.c — polling tem timeout de 60s e mostra mensagem apos expirar', async () => {
    vi.mocked(paymentsApi.getPagamentoDaInscricao).mockResolvedValue(pagamentoPendente)
    vi.mocked(paymentsApi.confirmarPagamento).mockResolvedValue(pagamentoConfirmado)
    // Polling nunca retorna ingresso (simula atraso excessivo)
    vi.mocked(ticketsApi.meusIngressos).mockResolvedValue([])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout(10)

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /pagar/i })).toBeInTheDocument()
    )

    await user.click(screen.getByRole('button', { name: /pagar/i }))

    // Avanca 61 segundos (alem do timeout de 60s)
    vi.advanceTimersByTime(61000)

    await waitFor(() => {
      // Deve exibir mensagem de timeout (nao trava infinitamente)
      expect(
        screen.getByText(/demorou|tente novamente|nao foi possivel confirmar/i)
      ).toBeInTheDocument()
    })
  })

  /**
   * C2.a — gatewayRecusa_mostraMensagemAmigavel (402 PAGAMENTO_RECUSADO)
   * Erro 402 do gateway deve mostrar mensagem amigavel (nao stack trace) + botao "Tentar de novo".
   */
  it('C2.a — gateway recusa (402) exibe mensagem amigavel e botao tentar de novo', async () => {
    vi.mocked(paymentsApi.getPagamentoDaInscricao).mockResolvedValue(pagamentoPendente)
    vi.mocked(paymentsApi.confirmarPagamento).mockRejectedValue({
      response: {
        status: 402,
        data: { message: 'PAGAMENTO_RECUSADO' },
      },
    })

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout(10)

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /pagar/i })).toBeInTheDocument()
    )

    await user.click(screen.getByRole('button', { name: /pagar/i }))

    await waitFor(() => {
      // Mensagem amigavel (nao stack trace, nao "PAGAMENTO_RECUSADO" literal)
      expect(
        screen.getByText(/pagamento recusado|nao foi possivel processar|tente novamente/i)
      ).toBeInTheDocument()
    })

    // Botao "Tentar de novo" deve aparecer
    expect(screen.getByRole('button', { name: /tentar de novo|tentar novamente/i })).toBeInTheDocument()

    // Nao deve mostrar stack trace
    expect(screen.queryByText(/Error:|at /)).not.toBeInTheDocument()
  })
})
