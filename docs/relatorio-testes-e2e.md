# Relatório de Testes E2E + Estado de Produção — PegaTicket

> Campanha de testes ponta-a-ponta executada **na aplicação em produção** (não em ambiente local), dirigindo o navegador real do usuário via Claude in Chrome, complementada por testes de API (curl) e pela suíte automatizada.

---

## 1. Sumário executivo

- **As 3 sprints funcionam ponta a ponta em produção.** Todos os fluxos principais de Identidade (S1), Eventos (S2) e Inscrição/Ingresso QR (S3) foram exercidos com sucesso.
- **Nenhum bug de lógica de negócio** foi encontrado. O único elemento que chegou a impedir um fluxo era **infraestrutura, não código**: *cold-start* dos serviços na Railway (App Sleeping).
- Foram identificadas e **corrigidas + deployadas** 6 melhorias de robustez/UX.
- Backend (Railway) e frontend (Vercel) validados em produção; concorrência (gate da S3) validada em três camadas: smoke manual (14/14), Testcontainers no CI Linux (11/11 + 6/6) e fluxo real em prod.

---

## 2. Ambiente testado

| Item | Valor |
|---|---|
| Frontend (prod) | `https://frontend-theta-sooty-61.vercel.app` (Vercel) |
| Gateway (prod) | `https://api-gateway-production-c4b4.up.railway.app` (Railway) |
| Backend | 6 serviços + Postgres + RabbitMQ na Railway |
| Navegador | Browser 1 (Windows, local) via extensão Claude in Chrome |
| Admin seed | `admin@pegaticket.local` / `Admin@123` |

---

## 3. Metodologia

1. **UI dirigida no navegador real** (Claude in Chrome): navegação, preenchimento de formulários, cliques, leitura da árvore de acessibilidade e da rede (status HTTP das chamadas).
2. **Camada de API** (curl no gateway): para confirmar causas-raiz (ex.: distinguir erro de UI de erro de backend) e reproduzir cenários de forma determinística.
3. **Suíte automatizada** (Vitest + reactor Maven) para regressão das correções.

---

## 4. Resultados por sprint

| Sprint | Fluxo testado | Resultado |
|---|---|---|
| **1 — Identidade** | Registro de participante | ✅ |
| **1** | Login + sessão (JWT, role-aware) | ✅ |
| **1** | Home participante / promotor / admin (UI adapta por papel) | ✅ |
| **1** | Registro de promotor (perfil rico) → cria conta PARTICIPANTE pendente | ✅ |
| **1** | Admin → aprovação de promotor → vira "Promotor verificado" | ✅ |
| **1** | Tela admin de **controle de usuários** (lista, papel, status, ações) | ✅ (acesso na nav) |
| **2 — Eventos** | Lista pública + filtros (busca / tipo / data) + paginação | ✅ |
| **2** | Detalhe do evento (fuso BRT, capacidade, vagas, preço) | ✅ |
| **2** | "Meus eventos" (promotor) com ações Ver/Cancelar | ✅ |
| **2** | Formulário de criação de evento (wizard) | ✅ |
| **2** | Criar → publicar evento (máquina de estados, ownership) | ✅ (via API + UI) |
| **3 — Inscrição** | Inscrever-se em evento gratuito → **ingresso 201** | ✅ |
| **3** | Ingresso único com **QR code** renderizado no front | ✅ |
| **3** | Dupla inscrição → **409 JA_INSCRITO** | ✅ |
| **3** | "Meus ingressos" (QR) | ✅ |
| **3** | "Minhas inscrições" (histórico paginado) | ✅ |
| **3** | Decremento de vaga (3 → 1 após inscrições) | ✅ |

---

## 5. Achados

### 5.1 Bloqueador (infra, não código): cold-start / Railway App Sleeping  · **mitigado**

- **Sintoma:** ao abrir a lista de eventos pela 1ª vez aparecia "Não foi possível carregar os eventos"; a inscrição às vezes dava 500/503. Em **todos** os casos, **a 2ª tentativa funcionava**.
- **Causa-raiz:** os serviços Railway **dormem quando ociosos** (App Sleeping). A 1ª requisição após inatividade espera ~8 s o *startup*; nesse intervalo o gateway responde 500/503. Os logs confirmam (`Started → Stopping Container → restart`, **sem crash/OOM**). Com os serviços **quentes**, todos os fluxos funcionam (provado: inscrição → 201 + ingresso).
- **Mitigação implementada (deployada):** interceptor no `frontend/src/api/client.ts` re-tenta **apenas GETs** (idempotentes) em 502/503/504/timeout com *backoff* ~9 s — o usuário vê o *spinner* e os dados carregam, em vez do erro. POST não é re-tentado (não-idempotente — a inscrição reserva vaga).
- **Fix definitivo (decisão do dono — tem trade-off de custo):** desabilitar **App Sleeping** no dashboard Railway (cada serviço → Settings → Serverless/Sleep) deixa tudo sempre quente, mas mantém os serviços rodando 24/7 (↑ custo — o App Sleeping era a economia do projeto acadêmico). Alternativa para a banca: **aquecer os serviços** com algumas requisições logo antes.

### 5.2 Melhorias menores (todas corrigidas + deployadas)

| # | Achado | Correção |
|---|---|---|
| 1 | Botões do hero da home ("Criar evento"/"Explorar eventos") **não navegavam** (botões mortos) | Adicionado `onClick` → /eventos/novo e /eventos |
| 2 | "Minhas inscrições" exibia **"Evento #1"** (ID cru) | Passa a exibir o **nome do evento** (dedup por id, fetch em background não-bloqueante) |
| 3 | Após registro, caía no **/login** (sem auto-login) | **Auto-login** pós-registro, com fallback ao /login se falhar |
| 4 | Card "Eventos ativos 0 / Nenhum evento ainda" era **stub** | **Contagens reais** (eventos do promotor / ingressos+inscrições do participante) |
| 5 | "1 vaga **disponíveis**" (plural incorreto) | Concorda singular/plural → "1 vaga **disponível**" |
| 6 | Sem teste para a resiliência a cold-start | **Teste** do interceptor (GET re-tenta em 503; POST não) |

> Itens 1–6 entregues nos PRs #5 e #6, deployados em `frontend-theta-sooty-61.vercel.app`.

---

## 6. Estado final de produção

- **Backend (Railway):** 6 serviços Online; saga de inscrição validada em prod (inscrição → 201 + ingresso QR; dupla → 409; vagas decrementam). Wiring interno S3 (`EVENT_SERVICE_URL` + `INTERNAL_TOKEN`) configurado.
- **Frontend (Vercel):** Sprint 3 no ar com as 6 melhorias; CORS gateway↔front OK; `VITE_API_URL` → gateway.
- **Qualidade:** `mvnw verify` verde; Vitest **62/62**; concorrência (gate inegociável da S3) validada em smoke real (14/14) **e** Testcontainers no CI Linux (`VagaConcorrenciaTest` 11/11 + `InscricaoConcorrenciaTest` 6/6).

---

## 7. Pendências / recomendações

1. **App Sleeping (decisão do dono):** desabilitar para uma demo à prova de cold-start, ou manter dormindo + aquecer antes da banca (o auto-retry de GET já cobre a navegação).
2. **Limpeza de dados de teste em prod** (opcional, antes da banca): usuários/eventos `*_s3_*`, "Smoke S3 Prod", "Maria QA".
3. Stubs intencionais sem fonte de dados ainda (rotulados): "Receita projetada" (promotor), "Avaliações" (participante), "Ingressos vendidos" — features de sprints futuras.
