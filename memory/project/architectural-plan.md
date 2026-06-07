# Plano Arquitetural — Ticketeira / PegaTicket

> **Fonte de verdade compartilhada.** Todo agente lê este arquivo antes de agir.
> Mantido por: Arquiteto + DevOps. Mudanças estruturais viram ADR em [`decisions.md`](decisions.md).

---

## 1. Produto

**Ticketeira** (marca no app: **PegaTicket**) — plataforma de **gestão de eventos e venda de ingressos** em microsserviços (disciplina de Engenharia de Software II). Ciclo de vida: organizador cadastra → admin verifica como promotor → cria evento → participante se inscreve (grátis ou pago com **escrow**) → ingresso único com QR → check-in → avaliação → reembolso ou repasse.

**Atores:** Participante, Promotor verificado, Administrador.

## 2. Arquitetura (visão de containers)

```
React (Vite) ──HTTPS──► API Gateway :8080 ──REST──► { user :8081, event :8082, ticket :8083, payment :8084 }
                            │ valida JWT, injeta X-User-Id/Email/Verified
                  Postgres (4 bancos isolados) + RabbitMQ (topic + DLX) + MailHog/Resend
```

- **API Gateway** (Spring Cloud Gateway, reativo): único ponto de entrada. `JwtAuthGlobalFilter` (order -100) valida o Bearer e injeta `X-User-*`. `StripPrefix=1`. CORS por `FRONTEND_ORIGIN`.
- **4 microsserviços de domínio** (Spring Boot MVC), **banco por serviço**.
- **Auth stateless (JWT)**: emitido pelo user-service via `shared/common-lib` `JwtUtil` (HS256/384; claims `iss=ticketeira`, `sub`, `email`, `verificado`, `exp` 1h). Serviços rodam Spring Security `permitAll()` + STATELESS e confiam nos headers `X-User-*`. **O papel (role) NÃO vai no token hoje** — é dívida conhecida (ver `decisions.md` / `known-gaps`).
- **Mensageria** (RabbitMQ): `infra/rabbitmq/definitions.json` declara 2 exchanges (`ticketeira.events` topic, `ticketeira.dlx`) + 6 filas (`pedido.criado`, `pagamento.aprovado`, `evento.finalizado` + `.dlq`). **Topologia declarada, consumidores/produtores ainda NÃO codados.**

## 3. Stack

| Camada | Tecnologia |
|---|---|
| Backend | Spring Boot 3.3.5 · Java 21 (Temurin) · Maven multi-módulo |
| Gateway | Spring Cloud Gateway 2023.0.3 |
| Auth | Spring Security · BCrypt · jjwt 0.12.6 |
| Persistência | PostgreSQL 16 · Flyway · JPA/Hibernate · `ddl-auto: validate` |
| Mensageria | RabbitMQ 3.13 (topic + DLX + DLQ) |
| API docs | springdoc-openapi (Swagger por serviço) |
| Frontend | React 18 · Vite 5 · TypeScript · react-router-dom 6 · axios · react-hook-form + zod · Tailwind + CVA + Radix (UI estilo shadcn à mão) · sonner · imask |
| Testes | JUnit 5 + AssertJ + Spring Boot Test + H2 (back) · Vitest + Testing Library (front) |
| Infra | Docker Compose (profiles `infra`/`backend`/`frontend`) · MailHog (dev) |
| Build | Maven Wrapper (`./mvnw`) · JDK 21 |
| Deploy | Railway (backend) · Vercel (frontend) · Resend (e-mail prod) |
| CI | GitHub Actions (`backend.yml` = `mvn verify`; `frontend.yml` = build + lint) |

## 4. Layout do monorepo

```
pom.xml                     # parent (BOM Spring Boot, Java 21)
mvnw / mvnw.cmd / .mvn/     # Maven Wrapper (não precisa instalar Maven)
shared/common-lib/          # JwtUtil, AuthenticatedUser, ErrorResponse, exceptions (JAR puro)
services/
  api-gateway/              # Spring Cloud Gateway + JwtAuthGlobalFilter
  user-service/             # ✅ implementado: auth, reset de senha, papéis, e-mail
  event-service/            # 🟡 esqueleto + schema Flyway
  ticket-service/           # 🟡 esqueleto + schema Flyway
  payment-service/          # 🟡 esqueleto + schema Flyway
frontend/                   # React + Vite + TS (5 telas)
infra/postgres/init/        # cria os 4 bancos
infra/rabbitmq/             # definitions.json + rabbitmq.conf
docs/{adr,api,deploy}/      # ADRs, OpenAPI, deploy
docker-compose.yml          # profiles
agents/ workflows/ rules/ memory/   # ⬅ este pipeline SDD
```

## 5. Estado real (≠ README)

O README diz "Sprint 0 / esqueletos". A **realidade** (sempre confiar no código):

| Módulo | Estado |
|---|---|
| `common-lib`, `api-gateway` | ✅ funcional |
| `user-service` | ✅ completo: register (participante verificado / promotor pendente / ADMIN bloqueado 403), login JWT, `GET /users/me`, `PUT /users/{id}/verify` (sem proteção — dívida), reset de senha (token SHA-256, uso único, TTL), papéis, `PerfilVerificado` (PENDENTE/VERIFICADO/REJEITADO — `aprovar()/rejeitar()` existem mas são **código morto**), e-mail Thymeleaf (MailHog/Resend). ~25 testes. |
| `event/ticket/payment-service` | 🟡 stubs (501/lista vazia), **schema Flyway completo**, sem `@Entity`, sem código RabbitMQ |
| `frontend` | ✅ 5 telas (Login, Register c/ abas Participante/Promotor, ForgotPassword, ResetPassword, Home role-aware c/ card admin), Vitest |

## 6. Modelo de dados (banco por serviço)

- **user_db**: `usuarios`(papel, verificado), `perfis_verificados`(cpf único, status), `password_reset_tokens`(token_hash, TTL).
- **event_db**: `eventos`(tipo GRATUITO/PAGO, status, capacidade>0, promotor_id), `avaliacoes`(nota 1-5, UNIQUE evento+usuario).
- **ticket_db**: `inscricoes`(**UNIQUE(usuario_id, evento_id)**), `ingressos`(codigo_unico/QR, status), `checkins`.
- **payment_db**: `configuracao_plataforma`(taxa 10% semeada), `pagamentos`(bruto/taxa/repasse, status), `reembolsos`.
- Refs cross-service são `BIGINT` simples, **sem FK entre bancos**.

## 7. Concorrência (pontos críticos do domínio)

O abre-vendas é o cenário de alta concorrência. As mutações de risco e a estratégia esperada:

| Cenário | Estratégia |
|---|---|
| Dupla inscrição no mesmo evento | `UNIQUE(usuario_id, evento_id)` + tratar `DataIntegrityViolationException` → 409 |
| Esgotar capacidade do evento | decremento atômico `UPDATE eventos SET vagas = vagas - 1 WHERE id = ? AND vagas > 0` (checar `rowsAffected`) **ou** `@Version` (optimistic) |
| Emitir ingresso 1x por inscrição | `UNIQUE(inscricao_id)` em `ingressos` |
| Consumo de evento AMQP duplicado | tabela `processed_events(event_id)` (idempotência — at-least-once do RabbitMQ) |

## 8. Roadmap (sprints)

| Sprint | Escopo | Requisitos |
|---|---|---|
| Atual | Contas/auth, reset, gateway, frontend, infra, CI, deploy | RF01 |
| 1 | Criar eventos · inscrição c/ lock otimista · ingresso QR · histórico | RF02, RF03, RF04, RF09 |
| 2 | Pagamento simulado + escrow · reembolso · saga de inscrição paga | RF05, RF06 |
| 3 | Cancelamento c/ política · avaliações + reputação · check-in QR | RF07, RF08, RF10 |
| 4 | Testes de carga · observabilidade | RNF09 |

## 9. Como rodar / construir (todos os agentes precisam saber)

```powershell
# Build/testes (Maven Wrapper — JDK 21 já instalado em C:\Users\Usuario\.jdks)
./mvnw -B -ntp verify                         # reactor inteiro (= o que o CI roda)
./mvnw -B -ntp -pl services/user-service -am test

# Stack local (Docker)
docker compose --profile backend --profile frontend up -d --build
docker compose ps

# Frontend isolado
cd frontend ; npm install ; npm run dev ; npm run test:run
```

> Memória deste pipeline: in-repo em [`memory/`](../). Não confundir com a memória automática do Claude (fora do repo).
