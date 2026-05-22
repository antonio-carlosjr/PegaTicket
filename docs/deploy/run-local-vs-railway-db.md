# Rodar os serviços local apontando para o Postgres da Railway

Quando você quer desenvolver/depurar um serviço na sua máquina mas usando o
**banco que já está na Railway** (mesmos dados de produção), sem subir um
Postgres local.

## 1. Conexão pública do Postgres da Railway

O Postgres da Railway é acessível por um **TCP proxy público**:

| Campo | Valor |
|---|---|
| Host | `kodama.proxy.rlwy.net` *(confirme em Railway → Postgres → Settings → Networking → TCP Proxy)* |
| Porta | `14441` *(idem)* |
| Usuário | `ticketeira` |
| Senha | *(a `POSTGRES_PASSWORD` do serviço Postgres na Railway)* |
| Databases | `user_db`, `event_db`, `ticket_db`, `payment_db` |
| SSL | use `sslmode=require` (conexão pela internet pública) |

> O host/porta do TCP proxy podem mudar se o proxy for recriado. Os valores
> ficam em **Railway → serviço Postgres → Variables** (`RAILWAY_TCP_PROXY_DOMAIN`,
> `RAILWAY_TCP_PROXY_PORT`).

## 2. Opção A — stack local em Docker apontando pro banco da Railway (recomendado)

Você não precisa de JDK/Maven local: o `docker-compose` builda e roda tudo.

No seu `.env` (gitignored), adicione:

```env
RAILWAY_DB_HOST=kodama.proxy.rlwy.net
RAILWAY_DB_PORT=14441
RAILWAY_DB_PASS=<senha-do-postgres-railway>
```

Suba com o override:

```bash
docker compose -f docker-compose.yml -f docker-compose.railway-db.yml --profile backend up --build
```

O que acontece:
- Os 4 serviços de dados (user/event/ticket/payment) conectam no Postgres da Railway (`sslmode=require`).
- O gateway local continua roteando pros serviços locais — só o **banco** está na Railway.
- O Postgres local ainda sobe (dependência do compose) mas fica **sem uso**.

Front local apontando pro gateway local: `VITE_API_URL=http://localhost:8080`.

## 3. Opção B — um único serviço via container (sem subir o resto)

Para mexer só em um serviço, usando a imagem já buildada:

```bash
# exemplo: user-service contra o user_db da Railway
docker run --rm -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=kodama.proxy.rlwy.net -e DB_PORT=14441 -e DB_NAME=user_db \
  -e DB_USER=ticketeira -e DB_PASS='<senha>' -e DB_SSLMODE=require \
  -e JWT_SECRET='<jwt-secret-prod>' -e PORT=8081 \
  ticketeira-user-service
```

Para os outros, troque `DB_NAME` (`event_db`/`ticket_db`/`payment_db`),
`PORT` (`8082`/`8083`/`8084`) e a imagem (`ticketeira-event-service`, etc.).

## 4. Opção C — via Maven/IDE (se você instalar JDK 21 + Maven)

```bash
DB_HOST=kodama.proxy.rlwy.net DB_PORT=14441 DB_NAME=user_db \
DB_USER=ticketeira DB_PASS='<senha>' DB_SSLMODE=require \
JWT_SECRET='<jwt-secret-prod>' \
mvn -pl services/user-service -am spring-boot:run
```

## ⚠️ Cuidados

- **Mesmo banco que produção:** rodando local contra o Railway, você mexe nos
  **dados reais**. Para testes destrutivos, prefira um Postgres local
  (`docker compose --profile backend up`, sem o override).
- **Flyway:** as migrations já foram aplicadas no Railway; ao subir local o
  Flyway só valida (no-op). Se você criar uma migration nova, ela roda no banco
  da Railway.
- **Segurança:** o TCP proxy é público. Nunca commite a senha; mantenha em `.env`.
  Se não for mais usar, remova o TCP proxy na Railway para fechar a exposição.
