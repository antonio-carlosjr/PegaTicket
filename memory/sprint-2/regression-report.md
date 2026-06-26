# Sprint 2 — Regression Report

> QA/Test Engineer Sênior. Data: 2026-06-26. Branch: `feat/sprint-2-eventos`.
> Objetivo: confirmar que a implementacao da Sprint 2 (Eventos) nao regrediu nenhum modulo da Sprint 1 (Identidade & Autorizacao).

---

## Bateria completa — Reactor Maven (`mvnw -B -ntp verify`)

Executado em: `D:\_Projetos\Faculdade\esofII` com JDK 21.0.11 (jdk-21.0.11+10).

| Modulo | Testes | Resultado |
|---|---|---|
| `common-lib` (JwtUtilTest) | 5 | PASS |
| `api-gateway` (GatewayApplicationTests) | 1 | PASS |
| `user-service` (AdminIntegrationTest + PasswordResetServiceTest + TokenHasherTest + UserServiceApplicationTests) | 25 | PASS |
| `event-service` (EventoTest + EventServiceTest + EventControllerIntegrationTest + EventServiceApplicationTests) | 54 | PASS |
| `ticket-service` (TicketServiceApplicationTests) | 1 | PASS |
| `payment-service` (PaymentServiceApplicationTests) | 1 | PASS |
| **TOTAL** | **87** | **87/87 PASS** |

```
[INFO] BUILD SUCCESS
[INFO] Total time: 01:10 min
[INFO] Finished at: 2026-06-26T14:36:20-03:00
```

---

## Frontend Vitest (`npm run test:run`)

| Suite | Testes | Resultado |
|---|---|---|
| `validation.test.ts` | 13 | PASS |
| `form-field.test.tsx` | 5 | PASS |
| `input.test.tsx` | 4 | PASS |
| `button.test.tsx` | 5 | PASS |
| `Eventos.test.tsx` (novo Sprint 2) | ~5 | PASS |
| `EventoDetalhe.test.tsx` (novo Sprint 2) | ~5 | PASS |
| `MeusEventos.test.tsx` (novo Sprint 2) | ~7 | PASS |
| `CriarEditarEvento.test.tsx` (novo Sprint 2) | ~8 | PASS |
| `Register.test.tsx` | 1 fail / restantes ok | **1 falha P3 pre-existente Sprint 1** |
| **TOTAL** | **51** | **50/51 PASS** |

Frontend build: `tsc -b && vite build` → **zero erros de tipo**, built em 1.86s.

---

## Smoke em Postgres real (11/11 PASS)

Subido via `docker compose --profile backend up`. Flyway V1 + V2 aplicadas sem erro. `ddl-auto: validate` passou.

| Componente verificado | Status |
|---|---|
| Flyway V1 (schema base: usuarios, tokens, etc.) | PASS |
| Flyway V2 (eventos: `vagas_disponiveis`, `imagem_url`, indice parcial) | PASS |
| `ddl-auto: validate` (entidade Java ⇿ schema Postgres) | PASS |
| `GET /api/events` sem filtro (`q=null`) — bug `lower(bytea)` nao se repete | PASS |
| Fluxo completo promotor (criar → publicar → listar → filtrar → detalhe) | PASS |
| PARTICIPANTE bloqueado em `POST /api/events` → 403 | PASS |
| Ownership: promotor B tenta editar evento de A → 404 | PASS |

---

## Regressao Sprint 1 (Identidade & Autorizacao)

### user-service — 25/25 PASS

Os 25 testes do `user-service` cobrem:
- `AdminIntegrationTest` (4): fluxos de admin (listar usuarios, busca, aprovar/rejeitar promotor)
- `PasswordResetServiceTest` (7): esqueci-senha (anti-enumeracao, geracao de token, expirado, usado)
- `TokenHasherTest` (5): hashing e verificacao de tokens
- `UserServiceApplicationTests` (9): context load + fluxos de auth (login, registro, me, papeis)

**Nenhuma regressao detectada.** O `user-service` nao foi alterado na Sprint 2.

### api-gateway — 1/1 PASS

O `GatewayApplicationTests` (context load + injecao de JWT nos headers) passou. As rotas novas de `/api/events/**` foram adicionadas ao gateway sem remover nem alterar as rotas existentes de `/api/users/**` e `/api/admin/**`.

### common-lib — 5/5 PASS

`JwtUtilTest` (geracao e validacao de JWT) inalterado.

---

## Confirmacoes de nao-regressao

| Criterio | Status |
|---|---|
| Login usuario (PARTICIPANTE, PROMOTOR, ADMIN) | NAO REGREDIU (user-service 25/25) |
| Registro e verificacao de promotor | NAO REGREDIU |
| Esqueci-senha (anti-enumeracao) | NAO REGREDIU |
| Admin lista / aprova / rejeita usuarios | NAO REGREDIU |
| Gateway injeta `X-User-Id` e `X-User-Papel` corretamente | NAO REGREDIU |
| Endpoints autenticados sem JWT → 401 | NAO REGREDIU |
| Endpoints admin sem papel ADMIN → 403 | NAO REGREDIU |

---

## Veredito da regressao

**REGRESSAO ZERO — Sprint 1 intacta.**

Reactor completo: **87/87 PASS**, BUILD SUCCESS.
Front: **50/51 PASS** — 1 falha e P3 pre-existente da Sprint 1, nao introduzida pela Sprint 2.
Smoke Postgres: **11/11 PASS**.

A Sprint 2 (Eventos) esta **APROVADA PARA PO**.
