import { afterEach, describe, expect, it } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { api } from '../client'

// Resiliência a cold-start: o interceptor re-tenta GET em 502/503/504/sem-resposta,
// mas NÃO re-tenta métodos não-idempotentes (POST/PUT/DELETE).

const adapterOriginal = api.defaults.adapter

afterEach(() => {
  api.defaults.adapter = adapterOriginal
})

function rejeicao503(config: unknown) {
  return Promise.reject({ config, response: { status: 503, data: {} }, isAxiosError: true })
}

describe('api — resiliência a cold-start (retry interceptor)', () => {
  it('re-tenta GET em 503 e resolve quando o serviço acorda', async () => {
    let chamadas = 0
    api.defaults.adapter = (async (config) => {
      chamadas++
      if (chamadas === 1) return rejeicao503(config)
      return { data: { ok: true }, status: 200, statusText: 'OK', headers: {}, config }
    }) as AxiosAdapter

    const resp = await api.get('/teste-retry')
    expect(resp.status).toBe(200)
    expect(chamadas).toBe(2) // 1 falha (503) + 1 sucesso após o backoff
  }, 8000)

  it('NÃO re-tenta POST em 503 (não-idempotente, ex.: inscrição reserva vaga)', async () => {
    let chamadas = 0
    api.defaults.adapter = (async (config) => {
      chamadas++
      return rejeicao503(config)
    }) as AxiosAdapter

    await expect(api.post('/teste-retry')).rejects.toBeTruthy()
    expect(chamadas).toBe(1) // sem retry
  })
})
