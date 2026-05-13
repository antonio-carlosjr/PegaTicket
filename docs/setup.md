# Guia de setup local — Ticketeira

## Pré-requisitos

| Tool | Versão | Obrigatório? |
|---|---|---|
| Docker Desktop | ≥ 4.30 (Compose v2) | sim |
| Git | qualquer recente | sim |
| JDK 21 (Temurin) | 21.x | não — só para rodar Maven fora do container |
| Maven | 3.9+ | não |
| Node.js | 20 LTS | não — frontend roda em container |

Tudo abaixo assume **Docker Desktop iniciado e rodando**.

---

## Subida em 1 comando

```powershell
git clone <repo> ticketeira
cd ticketeira
copy .env.example .env       # PowerShell
# cp .env.example .env       # bash

# subir tudo: infra + backend + frontend
docker compose --profile backend --profile frontend up -d --build
```

Os perfis controlam o que sobe:

| Comando | Sobe |
|---|---|
| `docker compose up -d` | só `postgres` + `rabbitmq` |
| `docker compose --profile backend up -d --build` | infra + gateway + 4 microsserviços |
| `docker compose --profile frontend up -d --build` | infra + Vite dev server |
| `docker compose --profile backend --profile frontend up -d --build` | tudo |

---

## Portas (padrão `.env.example`)

| Serviço | Host | Container |
|---|---|---|
| Frontend (Vite) | 5173 | 5173 |
| API Gateway | 8080 | 8080 |
| User Service | 8081 | 8081 |
| Event Service | 8082 | 8082 |
| Ticket Service | 8083 | 8083 |
| Payment Service | 8084 | 8084 |
| Postgres | 5432 | 5432 |
| RabbitMQ AMQP | 5672 | 5672 |
| RabbitMQ Mgmt UI | 15672 | 15672 |

### Conflitos comuns no Windows

- **5432:** instalação local do Postgres ocupa essa porta. Mude `POSTGRES_PORT=15432` no `.env`.
- **8080:** muitos apps Java/Node usam essa. Mude `GATEWAY_PORT=18080`.
- **5173:** se já tiver outro Vite rodando, mude `FRONTEND_PORT`.

Se mudar `GATEWAY_PORT`, **lembre de atualizar `VITE_API_URL`** no `.env` para apontar para a nova porta.

---

## URLs úteis

| Recurso | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Gateway health | http://localhost:8080/actuator/health |
| RabbitMQ Mgmt UI | http://localhost:15672 (ticketeira/ticketeira) |
| OpenAPI user-service | http://localhost:8081/swagger-ui.html |
| OpenAPI event-service | http://localhost:8082/swagger-ui.html |
| OpenAPI ticket-service | http://localhost:8083/swagger-ui.html |
| OpenAPI payment-service | http://localhost:8084/swagger-ui.html |

---

## Smoke test pelo terminal

```bash
G=http://localhost:8080

curl -sS $G/actuator/health
# {"status":"UP"}

curl -X POST $G/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"nome":"Ana","email":"ana@example.com","senha":"senha123"}'

curl -X POST $G/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","senha":"senha123"}'
# {"token":"eyJ...","tokenType":"Bearer","expiresInMs":3600000,...}

TOKEN="eyJ..."
curl $G/api/users/me -H "Authorization: Bearer $TOKEN"
```

---

## Troubleshooting

### `Cannot connect to the Docker daemon`

Docker Desktop não está iniciado. Abrir o app e esperar o ícone ficar verde.

### Serviço fica em `unhealthy`

```bash
docker compose logs <nome-do-servico> --tail 50
```

Causas comuns:
- Postgres ainda subindo na primeira execução (init script demora ~5s).
- RabbitMQ falhando ao parsear `definitions.json` — JSON malformado.
- Conflito de porta (ver acima).

### Reset completo (perde dados)

```bash
docker compose --profile backend --profile frontend down -v
docker volume rm ticketeira-pg-data ticketeira-rabbit-data 2>$null
docker compose --profile backend --profile frontend up -d --build
```

### Build Maven lento

Primeira execução baixa ~300MB de deps. As próximas usam o cache do volume Docker `ticketeira-m2`. Para acelerar build local (fora do container):

```bash
mvn -o verify    # offline, exige run online prévio
```

### Postgres não cria os 4 databases

O init script (`infra/postgres/init/01-create-databases.sql`) só roda **na primeira subida do volume**. Se subiu antes sem os 4 DBs:

```bash
docker compose down -v   # apaga volume
docker compose up -d
```

### Frontend não consegue alcançar Gateway

1. Verificar `VITE_API_URL` no `.env` aponta para a porta certa do Gateway.
2. Confirmar `localhost:5173` está na whitelist CORS do Gateway (`services/api-gateway/src/main/resources/application.yml`).
3. Hard refresh no browser (Ctrl+F5) — Vite cacheia a env var.

---

## Comandos úteis

```bash
# logs em tempo real de um serviço
docker compose logs -f user-service

# conectar no Postgres
docker compose exec postgres psql -U ticketeira -d user_db

# listar filas RabbitMQ
docker compose exec rabbitmq rabbitmqctl list_queues name messages

# rodar todos os testes Maven (sem instalar JDK)
docker run --rm -v "${PWD}:/work" -v "ticketeira-m2:/root/.m2" -w /work \
  maven:3.9-eclipse-temurin-21 mvn -B -ntp test
```
