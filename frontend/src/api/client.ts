import axios from 'axios'

const TOKEN_KEY = 'ticketeira.token'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  timeout: 10000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ─── Resiliência a cold-start (Railway "App Sleeping") ──────────────────────────
// Quando um serviço está dormindo, a 1ª requisição espera ~8s o startup; nesse
// intervalo o gateway responde 502/503/504 ou a conexão expira (sem resposta).
// Re-tentamos APENAS requisições idempotentes (GET) — o backoff cobre o cold start,
// então o usuário vê o spinner e os dados carregam em vez de um erro. POST/PUT/DELETE
// NÃO são re-tentados automaticamente (não-idempotentes; ex.: inscrição reserva vaga).
const RETRY_STATUSES = [502, 503, 504]
const MAX_RETRIES = 3
const RETRY_DELAYS_MS = [1500, 3000, 4500] // ~9s acumulado: cobre o startup do serviço

api.interceptors.response.use(undefined, async (error) => {
  const config = error.config as (typeof error.config & { __retryCount?: number }) | undefined
  if (!config) return Promise.reject(error)

  const method = (config.method ?? 'get').toLowerCase()
  const status = error.response?.status
  const transitorio = !error.response || (status != null && RETRY_STATUSES.includes(status))
  if (method !== 'get' || !transitorio) return Promise.reject(error)

  const tentativa = config.__retryCount ?? 0
  if (tentativa >= MAX_RETRIES) return Promise.reject(error)
  config.__retryCount = tentativa + 1

  await new Promise((r) => setTimeout(r, RETRY_DELAYS_MS[tentativa]))
  return api(config)
})

export function storeToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export function loadToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}
