import { afterEach, describe, expect, it, vi } from 'vitest'
import { consultarCep } from '../viacep'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('consultarCep (ViaCEP)', () => {
  it('retorna null sem chamar a API se o CEP nao tem 8 digitos', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    expect(await consultarCep('123')).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('preenche o endereco quando o CEP existe', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          logradouro: 'Praca da Se',
          bairro: 'Se',
          localidade: 'Sao Paulo',
          uf: 'SP',
          complemento: 'lado impar',
        }),
      })
    )
    const end = await consultarCep('01001-000')
    expect(end).toEqual({
      logradouro: 'Praca da Se',
      bairro: 'Se',
      localidade: 'Sao Paulo',
      uf: 'SP',
      complemento: 'lado impar',
    })
  })

  it('retorna null quando o CEP nao existe ({ erro: true })', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ erro: true }) }))
    expect(await consultarCep('00000-000')).toBeNull()
  })

  it('retorna null quando a consulta falha (rede)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')))
    expect(await consultarCep('01001-000')).toBeNull()
  })
})
