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

## Setup rápido

```powershell
git clone <repo-url> ticketeira
cd ticketeira

copy .env.example .env       # PowerShell  (ou: cp .env.example .env)
# Edite .env e troque JWT_SECRET por algo aleatório >= 32 chars
# Em caso de conflito de portas (5432/8080), ver docs/setup.md

# tudo de uma vez (infra + 5 backends + frontend):
docker compose --profile backend --profile frontend up -d --build
```

Perfis disponíveis:

| Comando | O que sobe |
|---|---|
| `docker compose up -d` | só infra (Postgres + RabbitMQ) |
| `docker compose --profile backend up -d --build` | infra + Gateway + 4 microsserviços |
| `docker compose --profile frontend up -d --build` | infra + Vite dev server |
| `docker compose --profile backend --profile frontend up -d --build` | tudo |

URLs após `up`:

| Recurso | URL |
|---|---|
| Frontend (Vite + HMR) | http://localhost:5173 |
| **API Gateway** | http://localhost:8080 |
| Gateway health | http://localhost:8080/actuator/health |
| RabbitMQ Management UI | http://localhost:15672 (`ticketeira`/`ticketeira`) |
| Swagger UI por serviço | http://localhost:8081..4/swagger-ui.html |

A topologia AMQP (3 filas + 3 DLQs + 2 exchanges) é carregada automaticamente de [`infra/rabbitmq/definitions.json`](infra/rabbitmq/definitions.json). Os 4 databases isolados são criados via [`infra/postgres/init/01-create-databases.sql`](infra/postgres/init/01-create-databases.sql).

## Smoke test (curl)

```bash
G=http://localhost:8080
curl $G/actuator/health
# {"status":"UP"}

curl -X POST $G/api/auth/register -H "Content-Type: application/json" \
  -d '{"nome":"Ana","email":"ana@example.com","senha":"senha123"}'

TOKEN=$(curl -s -X POST $G/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","senha":"senha123"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])")

curl $G/api/users/me -H "Authorization: Bearer $TOKEN"
```

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
| Sprint 0 — Bloco A | Monorepo, parent POM, docker-compose, infra, common-lib | ✅ |
| Sprint 0 — Bloco B | Gateway + 4 microsserviços + auth JWT funcional | ✅ |
| Sprint 0 — Bloco C | Frontend Vite/React/TS + docs/ADRs/OpenAPI + CI GitHub Actions | ✅ |
| Sprint 1 | Implementação dos RFs (CRUD eventos, inscrições, pagamento simulado, check-in) | ⬜ |

Decisões arquiteturais em [`docs/adr/`](docs/adr). Contratos REST em [`docs/api/`](docs/api). Setup detalhado em [`docs/setup.md`](docs/setup.md).

---

## Convenções

- **Branch principal:** `main`.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/) — `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`.
- **Idioma:** código em inglês (classes, métodos), comentários e docs em português.
- **Encoding:** UTF-8, LF, definido em `.editorconfig`.

---

## Licença

Projeto acadêmico — sem licença de distribuição definida.
