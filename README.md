# Ticketeira

Plataforma acadêmica de gestão de eventos e ingressos, projetada como **microsserviços** para suportar alta concorrência em vendas, integridade financeira (escrow) e garantia de unicidade de ingressos.

Projeto da disciplina de **Engenharia de Software II**.

---

## Visão geral da arquitetura

```
                ┌────────────────┐
   Usuários ────►│  Front-end     │ (React + Vite + TS)
                └────────┬───────┘
                         │ REST/HTTPS
                ┌────────▼───────┐
                │  API Gateway   │ (Spring Cloud Gateway, valida JWT)
                └─┬────┬────┬────┬┘
                  │    │    │    │
        ┌─────────┘    │    │    └─────────┐
        ▼              ▼    ▼              ▼
   ┌─────────┐   ┌─────────┐ ┌──────────┐ ┌──────────┐
   │ User    │   │ Event   │ │ Ticket   │ │ Payment  │   (Spring Boot)
   │ Service │   │ Service │ │ Service  │ │ Service  │
   └────┬────┘   └────┬────┘ └────┬─────┘ └────┬─────┘
        │             │           │            │
   ┌────▼───┐   ┌────▼────┐ ┌────▼────┐ ┌─────▼────┐
   │user_db │   │event_db │ │ticket_db│ │payment_db│   (PostgreSQL)
   └────────┘   └─────────┘ └─────────┘ └──────────┘

         │ AMQP (PedidoCriado, PagamentoAprovado, EventoFinalizado)
         ▼
   ┌──────────────────────────────────────────────────────┐
   │         RabbitMQ  (topic + DLX + DLQs)              │
   └──────────────────────────────────────────────────────┘
```

Diagramas detalhados em [`docs/diagrams/`](docs/diagrams/) (containers C4 e visão do broker).

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Spring Boot 3.3 · Java 21 · Maven (multi-module) |
| Gateway | Spring Cloud Gateway 2023.0.x |
| Auth | Spring Security · JWT (jjwt 0.12.x, HS256) |
| Persistência | PostgreSQL 16 · Flyway · JPA/Hibernate |
| Mensageria | RabbitMQ 3.13 (topic + DLQ) |
| Frontend | React 18 · Vite · TypeScript · axios |
| Infra | Docker Compose · GitHub Actions |
| Testes | JUnit 5 · AssertJ · Testcontainers |

---

## Pré-requisitos

- **Docker Desktop** ≥ 4.x (com Docker Compose v2).
- **JDK 21** (Temurin recomendado) — só necessário para rodar Maven fora do container.
- **Maven 3.9+** — opcional; o `mvnw` será adicionado nos serviços conforme forem implementados.
- **pnpm** ≥ 9 ou **npm** ≥ 10 — para o frontend (a partir da Sprint 0 / Bloco C).

---

## Setup rápido (Bloco A — atual)

```powershell
# 1. Clonar e entrar no projeto
git clone <repo-url> ticketeira
cd ticketeira

# 2. Copiar variáveis de ambiente
copy .env.example .env       # PowerShell
# cp .env.example .env       # bash

# 3. Editar .env e substituir o JWT_SECRET por algo aleatório
#    (>= 32 chars). Sugestão:
#    openssl rand -base64 48

# 4. Subir a infra (Postgres + RabbitMQ)
docker compose up -d

# 5. Verificar containers
docker compose ps
```

Após `up -d` você deve ter:

| Serviço | URL / porta |
|---|---|
| PostgreSQL | `localhost:5432` (user `ticketeira`) |
| RabbitMQ AMQP | `localhost:5672` |
| **RabbitMQ Management UI** | http://localhost:15672 (login: `ticketeira` / `ticketeira`) |

A topologia (3 filas de domínio + 3 DLQs + 2 exchanges) é carregada automaticamente a partir de [`infra/rabbitmq/definitions.json`](infra/rabbitmq/definitions.json).

---

## Estrutura do repositório

```
ticketeira/
├── pom.xml                         # Parent POM (BOM Spring Boot, Java 21)
├── docker-compose.yml              # Stack local (infra + perfis backend/frontend)
├── .env.example                    # Variáveis de ambiente (copiar para .env)
├── shared/
│   └── common-lib/                 # DTOs, exceções e JwtUtil compartilhados
├── services/                       # Microsserviços Spring Boot (Bloco B)
│   ├── api-gateway/
│   ├── user-service/
│   ├── event-service/
│   ├── ticket-service/
│   └── payment-service/
├── frontend/                       # React + Vite + TS (Bloco C)
├── infra/
│   ├── postgres/init/              # SQL que cria os 4 databases
│   └── rabbitmq/                   # definitions.json + rabbitmq.conf
├── docs/
│   ├── adr/                        # Architecture Decision Records
│   ├── api/                        # OpenAPI 3 por serviço
│   └── diagrams/                   # PNGs dos diagramas C4 / broker
└── .github/workflows/              # CI (build + test)
```

---

## Validação local

### `common-lib` (módulo compartilhado)

```bash
# Da raiz:
mvn -pl shared/common-lib -am test
```

Cobertura mínima atual: `JwtUtilTest` — round-trip de token, rejeição de adulteração, secret mínimo 32 bytes.

### docker-compose

```bash
docker compose config       # valida sintaxe sem subir nada
docker compose up -d        # sobe Postgres + RabbitMQ
docker compose ps           # todos com (healthy)?
docker compose logs -f rabbitmq | findstr "started"
```

### Postgres — confirmar os 4 databases

```bash
docker compose exec postgres psql -U ticketeira -d postgres -c "\l"
```

Deve listar `user_db`, `event_db`, `ticket_db`, `payment_db`.

### RabbitMQ — confirmar topologia

Acessar http://localhost:15672 → aba **Queues** → devem aparecer:

- `pedido.criado` + `pedido.criado.dlq`
- `pagamento.aprovado` + `pagamento.aprovado.dlq`
- `evento.finalizado` + `evento.finalizado.dlq`

Aba **Exchanges** → `ticketeira.events` (topic) e `ticketeira.dlx` (topic).

---

## Roadmap

| Sprint | Escopo | Status |
|---|---|---|
| **Sprint 0 — Bloco A** | Monorepo, parent POM, docker-compose, infra, common-lib | ✅ em curso |
| Sprint 0 — Bloco B | Gateway + 4 microsserviços (esqueleto + auth JWT) | ⬜ |
| Sprint 0 — Bloco C | Frontend Vite + docs/ADRs/OpenAPI + CI GitHub Actions | ⬜ |
| Sprint 1+ | Implementação dos RFs (eventos, inscrições, pagamentos, check-in) | ⬜ |

Requisitos completos: ver `docs/requisitos.md` (a popular) ou o documento original da disciplina.

---

## Convenções

- **Branch principal:** `main`.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/) — `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`.
- **Idioma:** código em inglês (classes, métodos), comentários e docs em português.
- **Encoding:** UTF-8, LF, definido em `.editorconfig`.

---

## Licença

Projeto acadêmico — sem licença de distribuição definida.
