# Deploy: Railway (backend) + Vercel (frontend)

Guia para subir PegaTicket em produção: 5 microsserviços + Postgres + RabbitMQ na **Railway**, frontend na **Vercel**, e-mail via **Resend**.

---

## Sumário

1. [Resend (e-mail)](#1-resend-e-mail)
2. [Railway — projeto e Postgres](#2-railway--projeto-e-postgres)
3. [Railway — RabbitMQ](#3-railway--rabbitmq)
4. [Railway — microsserviços](#4-railway--microsservicos)
5. [Railway — API Gateway](#5-railway--api-gateway)
6. [Vercel — frontend](#6-vercel--frontend)
7. [Smoke test final](#7-smoke-test-final)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Resend (e-mail)

1. Conta em https://resend.com (free, sem cartão).
2. **Domains → Add Domain**: adicione um domínio que você controla (ex.: `seudominio.com`). Para teste sem domínio, pule e use `onboarding@resend.dev` (mas só consegue enviar pro e-mail cadastrado).
3. Verifique os registros DNS (TXT/MX/DKIM) que a Resend pede.
4. **API Keys → Create API Key** com escopo `Sending access`. Guarde o valor `re_...`.
5. Variáveis que vamos usar:
   - `SMTP_HOST=smtp.resend.com`
   - `SMTP_PORT=587`
   - `SMTP_USER=resend`
   - `SMTP_PASS=<api_key>`
   - `MAIL_FROM=noreply@seudominio.com` (ou `onboarding@resend.dev` se não verificou domínio)

---

## 2. Railway — projeto e Postgres

1. Conta em https://railway.com.
2. **+ New Project → Deploy from GitHub repo**: selecione `antonio-carlosjr/PegaTicket` e a branch `main`.
3. No projeto criado, clique **+ Create → Database → Add PostgreSQL**.
4. Aguarde o Postgres ficar verde. Anote (na aba **Variables** do plugin) o `DATABASE_URL`, `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`.
5. **Criar os 4 databases lógicos** (o plugin entrega só 1). Opções:
   - **Pela web**: aba **Data → Query** no plugin, cole o conteúdo de [`postgres-init.sql`](postgres-init.sql) e Run.
   - **Pela CLI**: `railway link`, depois `railway connect postgres < docs/deploy/postgres-init.sql`.

---

## 3. Railway — RabbitMQ

1. **+ Create → Empty Service** → renomeie para `rabbitmq`.
2. Em **Settings → Source**: escolha **Docker Image** e cole `rabbitmq:3.13-management`.
3. **Variables**:
   - `RABBITMQ_DEFAULT_USER=ticketeira`
   - `RABBITMQ_DEFAULT_PASS=<gere um forte>`
4. **Settings → Networking → Generate Private Domain** (vai ficar `rabbitmq.railway.internal`). Não precisa expor publicamente.
5. (Opcional) Para carregar a topologia (exchanges/queues/DLQ): mount do `definitions.json` é mais chato em Docker Image puro. Recomendo deixar **cada serviço declarar a topologia idempotentemente** via `RabbitConfig.java` (Sprint 1 do projeto).

---

## 4. Railway — microsservicos

Repita os passos abaixo para **cada um** dos 4 serviços: `user-service`, `event-service`, `ticket-service`, `payment-service`.

1. **+ Create → GitHub Repo** → escolha o repo `PegaTicket`. Renomeie o serviço criado para o nome do microsserviço (ex.: `user-service`).
2. **Settings → Source → Root Directory**: deixe vazio (`/`).
3. **Settings → Source → Watch Paths**: `services/<nome>/**`, `shared/**`, `pom.xml`.
4. **⚠ Settings → Build → Builder**: troque de **Nixpacks/Auto** para **Dockerfile**. Sem isso, Railway tenta auto-build com Nixpacks e falha (`Error: Unable to access jarfile target/*jar`).
5. **Settings → Build → Dockerfile Path**: `services/<nome>/Dockerfile`.
   - Alternativa: adicione `RAILWAY_DOCKERFILE_PATH=services/<nome>/Dockerfile` em **Variables** (mesmo efeito, mais rápido de aplicar).
6. **Settings → Networking → Generate Private Domain**: gera `<nome>.railway.internal`. Não habilite domínio público.
7. **Variables**:

   Comum a todos:
   ```
   SPRING_PROFILES_ACTIVE=prod
   DB_HOST=${{Postgres.PGHOST}}
   DB_PORT=${{Postgres.PGPORT}}
   DB_USER=${{Postgres.PGUSER}}
   DB_PASS=${{Postgres.PGPASSWORD}}
   RABBIT_HOST=rabbitmq.railway.internal
   RABBIT_PORT=5672
   RABBIT_USER=${{rabbitmq.RABBITMQ_DEFAULT_USER}}
   RABBIT_PASS=${{rabbitmq.RABBITMQ_DEFAULT_PASS}}
   ```

   Por serviço (sobreescreva `DB_NAME`):
   | Serviço | `DB_NAME` |
   |---|---|
   | user-service | `user_db` |
   | event-service | `event_db` |
   | ticket-service | `ticket_db` |
   | payment-service | `payment_db` |

   **Inscrição (Sprint 3) — token interno entre `ticket` e `event` (ADR-T08):**
   | Serviço | Variáveis adicionais |
   |---|---|
   | event-service | `INTERNAL_TOKEN=<gere: openssl rand -hex 24>` |
   | ticket-service | `EVENT_SERVICE_URL=http://event-service.railway.internal:8082` **e** `INTERNAL_TOKEN=<MESMO valor>` |

   > ⚠️ Sem `EVENT_SERVICE_URL`, o ticket-service cai no default `localhost:8082` (aponta pra si mesmo) → toda inscrição dá **503** (BUG-S3-01). O `INTERNAL_TOKEN` deve ser **idêntico** nos dois serviços (autoriza os endpoints `/internal/**`); divergente → 403→503. Em prod, sobrescreva o default de dev `dev-internal-secret`.

   **Apenas no user-service** (Resend + JWT + URLs):
   ```
   JWT_SECRET=<gerar com: openssl rand -base64 48>
   JWT_EXPIRATION_MS=3600000
   SMTP_HOST=smtp.resend.com
   SMTP_PORT=587
   SMTP_USER=resend
   SMTP_PASS=<api_key da Resend>
   MAIL_FROM=noreply@seudominio.com
   FRONTEND_BASE_URL=https://pegaticket.vercel.app
   PASSWORD_RESET_TTL_MIN=60
   ```

   > A mesma `FRONTEND_BASE_URL` e usada tanto no link de reset
   > (`{base}/reset-password?token=...`) quanto no botao "Explorar eventos" do
   > email de boas-vindas.

8. **Settings → Deploy → Health Check Path**: `/actuator/health`.

---

## 5. Railway — API Gateway

1. Mesmo fluxo do passo 4, mas com `Dockerfile Path = services/api-gateway/Dockerfile`.
2. **Settings → Networking**: gere domínio **público** (`pegaticket-gateway.up.railway.app`). É o único que precisa ser acessível externamente.
3. **Variables**:
   ```
   SPRING_PROFILES_ACTIVE=prod
   JWT_SECRET=<MESMO secret do user-service>
   JWT_EXPIRATION_MS=3600000

   USER_SERVICE_URL=http://user-service.railway.internal:${{user-service.PORT}}
   EVENT_SERVICE_URL=http://event-service.railway.internal:${{event-service.PORT}}
   TICKET_SERVICE_URL=http://ticket-service.railway.internal:${{ticket-service.PORT}}
   PAYMENT_SERVICE_URL=http://payment-service.railway.internal:${{payment-service.PORT}}

   FRONTEND_ORIGIN=https://pegaticket.vercel.app
   ```

> ⚠️ `JWT_SECRET` precisa ser **idêntico** entre gateway e user-service. O gateway valida o token que o user-service emite.

---

## 6. Vercel — frontend

1. Conta em https://vercel.com.
2. **Add New → Project → Import** o repo `PegaTicket`.
3. **Root Directory**: `frontend`.
4. Vercel detecta o `vercel.json` (build via Vite, SPA rewrite). Não precisa mexer.
5. **Environment Variables**:
   ```
   VITE_API_URL=https://pegaticket-gateway.up.railway.app
   ```
   (URL do gateway na Railway, sem barra final)
6. **Deploy**. Após o build, a URL será `https://<projeto>.vercel.app`. Anote.
7. Volte na Railway → gateway → atualize `FRONTEND_ORIGIN` com essa URL exata.
8. Volte na Railway → user-service → atualize `FRONTEND_BASE_URL=https://<projeto>.vercel.app`.

---

## 7. Smoke test final

```bash
G=https://pegaticket-gateway.up.railway.app

# 1. Health
curl $G/actuator/health
# {"status":"UP"}

# 2. Cadastro
curl -X POST $G/api/auth/register -H "Content-Type: application/json" \
  -d '{"nome":"Teste","email":"voce@gmail.com","senha":"senhaForte123"}'

# 3. Login
TOKEN=$(curl -s -X POST $G/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"voce@gmail.com","senha":"senhaForte123"}' \
  | jq -r .token)

# 4. Perfil
curl $G/api/users/me -H "Authorization: Bearer $TOKEN"

# 5. Recuperação de senha (deve chegar e-mail real na sua caixa via Resend)
curl -X POST $G/api/auth/forgot-password -H "Content-Type: application/json" \
  -d '{"email":"voce@gmail.com"}'
```

E no browser: abrir `https://<projeto>.vercel.app`, fazer cadastro Promotor com CPF/telefone, login, dashboard com badge.

---

## 8. Troubleshooting

### `Error: Unable to access jarfile target/*jar` (Crashed no deploy)
Railway esta usando **Nixpacks** em vez do nosso Dockerfile. Sintoma classico de monorepo sem config de build.

**Fix:** Settings → Build → Builder = **Dockerfile** + Dockerfile Path = `services/<nome>/Dockerfile`. Ou adicione `RAILWAY_DOCKERFILE_PATH=services/<nome>/Dockerfile` em Variables. Redeploy.

### `entityManagerFactory failed: connection refused`
O serviço subiu antes do Postgres ficar saudável. Soluções:
- Aguarde 60s e o redeploy automático passa.
- Em **Settings → Deploy** habilite "Restart policy: On failure" (já é default).

### `Could not resolve user-service.railway.internal`
Os serviços precisam estar no mesmo **Project** Railway. Cheque na sidebar do projeto.

### Resend dá `Domain not verified`
Use `MAIL_FROM=onboarding@resend.dev` temporariamente. Só funciona com destinatário = e-mail cadastrado na conta Resend.

### Flyway falha com `database "user_db" does not exist`
O script `postgres-init.sql` não foi executado. Conecte no Postgres e rode-o.

### CORS bloqueia frontend
Cheque `FRONTEND_ORIGIN` no gateway. Deve ser **exatamente** a URL Vercel (com `https://`, sem barra final).

### Logs em tempo real
Cada serviço Railway tem aba **Deploy Logs** + **HTTP Logs**. Use durante o smoke test.

---

## Custo estimado

| Item | ~ Custo/mês |
|---|---|
| Postgres plugin | $5 |
| 5 microsserviços + RabbitMQ (~256MB cada) | ~$3-4 cada → $18-24 |
| Vercel (frontend) | $0 |
| Resend (até 3.000 e-mails/mês) | $0 |
| **Total** | **~$25-30/mês** |

Free credit Railway de $5 cobre uns 5 dias de demo. Para apresentação acadêmica, basta subir antes da banca e derrubar depois.
