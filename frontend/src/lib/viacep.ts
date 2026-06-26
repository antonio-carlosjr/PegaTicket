/**
 * Consulta de endereço por CEP via ViaCEP (https://viacep.com.br).
 * API pública com CORS liberado — chamada direta do browser.
 */

export type EnderecoViaCep = {
  logradouro: string
  bairro: string
  localidade: string // cidade
  uf: string
  complemento: string
}

/**
 * Busca o endereço de um CEP. Retorna `null` se o CEP for inválido (≠ 8 dígitos),
 * não existir (`{ erro: true }`) ou a consulta falhar — nesses casos o usuário
 * preenche o endereço manualmente.
 */
export async function consultarCep(cep: string): Promise<EnderecoViaCep | null> {
  const digitos = cep.replace(/\D/g, '')
  if (digitos.length !== 8) return null
  try {
    const resp = await fetch(`https://viacep.com.br/ws/${digitos}/json/`)
    if (!resp.ok) return null
    const data = await resp.json()
    if (!data || data.erro) return null
    return {
      logradouro: data.logradouro ?? '',
      bairro: data.bairro ?? '',
      localidade: data.localidade ?? '',
      uf: data.uf ?? '',
      complemento: data.complemento ?? '',
    }
  } catch {
    return null
  }
}
