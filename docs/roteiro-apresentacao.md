# Roteiro de Apresentação — PegaTicket

> Roteiro de demo ao vivo + falas, uma seção por sprint. Plataforma de eventos/ingressos em microsserviços (Eng. de Software II). **Produção:** front `https://frontend-theta-sooty-61.vercel.app` · gateway `https://api-gateway-production-c4b4.up.railway.app`.

---

## ⚙️ Antes de começar (5 min antes da banca)

1. **Aqueça os serviços** (evita cold-start na demo): abra o front, faça login e navegue por Eventos uma vez. Isso "acorda" gateway, user, event e ticket-service. *(Ou deixe esta janela aberta.)*
2. Tenha **3 abas/papéis** prontos para alternar rápido: **participante**, **promotor verificado**, **admin** (`admin@pegaticket.local` / `Admin@123`).
3. Deixe um **evento gratuito publicado com vagas** pronto para a demo de inscrição (a parte mais visual).
4. Frase de abertura: *"PegaTicket é uma plataforma de eventos com ingresso por QR. O diferencial técnico é a arquitetura em microsserviços e o tratamento de concorrência na abertura de vendas — vou mostrar os três incrementos."*

---

## 🟦 Sprint 1 — Identidade & Autorização

**Tema:** quem é quem na plataforma e quem pode o quê. · **Duração:** ~4 min.

### Objetivo (1 frase para a banca)
> "Cadastro com papéis, autenticação stateless por JWT no gateway, e um fluxo de verificação de promotor com aprovação do admin e e-mail."

### Passo a passo (demo)
1. **Registro de participante** — `Crie agora` → aba *Participante* → nome/e-mail/senha → cria conta e **já entra** (auto-login). Mostre a **home role-aware**: badge "Participante", nav "Meus ingressos".
2. **Registro de promotor** — sair → `Crie agora` → aba *Promotor* → mostre o **perfil rico** (CPF, telefone, e-mail de contato, endereço, redes). Envie. *"O promotor entra como participante comum até ser aprovado — papel base do sistema."*
3. **Admin aprova** — entre como admin → nav **"Admin"** → tela **Gerenciamento de Usuários** (tabela: papel, status, ações). Aprove o promotor pendente.
4. **Promotor verificado** — entre como o promotor → home mostra **"Promotor verificado"** e o CTA "Criar evento" aparece.

### Destaques técnicos (falar)
- **JWT stateless validado no gateway** (Spring Cloud Gateway): o gateway injeta `X-User-Id/Email/Verified/Papel`; os serviços confiam nesses headers (anti-spoofing — o gateway sobrescreve o que vier do cliente).
- **PARTICIPANTE como papel base**; promotor é um upgrade verificado. Aprovação/rejeição **dispara e-mail** (template aprovado/rejeitado com motivo).
- **Tela admin** de controle (ativar/inativar, aprovar/rejeitar) — endpoint protegido por papel.

### Pergunta provável
- *"Como impede um usuário de forjar o papel?"* → O serviço **não** lê papel do cliente; só o gateway injeta `X-User-Papel` a partir do JWT que ele mesmo valida; o filtro sobrescreve qualquer header vindo de fora.

---

## 🟩 Sprint 2 — Eventos

**Tema:** o promotor publica; o participante descobre. · **Duração:** ~4 min.

### Objetivo
> "O event-service vira um serviço real: CRUD de eventos com máquina de estados, ownership, e uma listagem pública com filtros."

### Passo a passo (demo)
1. **Criar evento** (como promotor verificado) — "Criar evento" → wizard (nome/descrição → datas/local/capacidade → tipo). Crie um **gratuito**.
2. **Publicar** — em "Meus eventos", publique. *"RASCUNHO → PUBLICADO é uma transição de máquina de estados; transição inválida vira 409."*
3. **Descobrir** (como participante) — "Eventos": **busca por título/local**, **filtro por tipo**, **filtro por data**, paginação.
4. **Detalhe** — abra o evento: datas no **fuso do usuário (BRT)**, capacidade, vagas, preço.

### Destaques técnicos (falar)
- **Máquina de estados** (RASCUNHO/PUBLICADO/CANCELADO/REALIZADO) e **ownership**: um promotor não enxerga/edita evento de outro — não-dono recebe **404** (não vaza existência).
- **Timezone correto:** banco em UTC, conversão com *offset* na borda (aprendizado: `datetime-local` sem offset quebrava o backend → corrigido).
- **Banco por serviço:** o event-service tem seu próprio Postgres; sem FK cross-service.

### Pergunta provável
- *"E se dois promotores editarem o mesmo evento?"* → Ownership garante que só o dono edita; concorrência pesada de capacidade é o tema da Sprint 3.

---

## 🟪 Sprint 3 — Inscrição & Ingresso QR (o destaque técnico)

**Tema:** abertura de vendas concorrente, sem overbooking. · **Duração:** ~5 min.

### Objetivo
> "Participante se inscreve em evento gratuito e recebe um ingresso único com QR. O coração é **concorrência**: capacidade atômica e sem dupla inscrição, numa mini-saga entre dois serviços."

### Passo a passo (demo)
1. **Inscrever-se** (como participante) — detalhe do evento → **"Inscrever-se"** → aparece o **ingresso com QR** na hora. *"Inscrição confirmada, ingresso gerado."*
2. **Meus ingressos** — nav "Meus ingressos": card do evento + **QR renderizado no front** (a partir do código único; nunca pedimos imagem ao backend).
3. **Dupla inscrição** — tente se inscrever de novo no mesmo evento → **"Você já está inscrito"** (409). *"A constraint de unicidade garante 1 inscrição por usuário/evento, mesmo em corrida."*
4. **Histórico** — "Minhas inscrições": histórico paginado com nome do evento e status.

### Destaques técnicos (o "ouro" para a banca)
- **Mini-saga síncrona cross-service** (ticket → event): *validar evento → reservar vaga → criar inscrição+ingresso → compensar se falhar*. Sem transação distribuída.
- **Sem overbooking:** decremento **atômico** no Postgres — `UPDATE eventos SET vagas = vagas - 1 WHERE id=? AND status=PUBLICADO AND vagas > 0`, conferindo `rowsAffected`. O *row lock* serializa concorrentes; **zero janela** de check-then-act.
- **Sem dupla inscrição:** `UNIQUE(usuario_id, evento_id)` — a corrida real é resolvida no INSERT pela constraint, com **compensação** (libera a vaga) e 409 tipado.
- **Canal interno seguro (ADR-T08):** os endpoints `/internal/**` (reservar/liberar vaga) **não** são roteados pelo gateway e exigem `X-Internal-Token` (comparação constante-no-tempo); não leem header de usuário.
- **Prova de qualidade:** o "não-overbooking" foi validado em **três camadas** — smoke real com **20 requisições paralelas na última vaga (5/5, vagas nunca negativo)**, **Testcontainers em Postgres real no CI** (`VagaConcorrenciaTest`/`InscricaoConcorrenciaTest`), e o **fluxo real em produção**.

### Pergunta provável
- *"E se a reserva no event-service der certo mas a criação do ingresso falhar?"* → **Compensação**: a saga libera a vaga (`liberar-vaga`); se a compensação também falhar, loga `[RECONCILIACAO]` para tratamento manual — a vaga nunca fica silenciosamente presa.
- *"Por que síncrono e não fila?"* → Gratuito é caminho-feliz curto; o **caminho pago** (escrow via fila AMQP, idempotência, DLQ) é a Sprint 4.

---

## 🏁 Encerramento (~2 min)

- **Arquitetura:** API Gateway + 4 microsserviços (user/event/ticket/payment) + banco-por-serviço + RabbitMQ; front React/TS; deploy Railway (back) + Vercel (front).
- **Qualidade/engenharia:** pipeline SDD com gates, **commits atômicos por estória**, code review adversarial, e a lição transversal **"H2 ≠ Postgres"** — validamos sempre em Postgres real, o que pegou bugs que o CI com H2 escondia.
- **Robustez:** o front se recupera de *cold-start* de infra (auto-retry de leituras), e o fluxo crítico (concorrência) tem teste automatizado em Postgres real no CI.
- Frase de fecho: *"Em três sprints saímos de esqueletos para uma plataforma de eventos funcional em produção, com o problema difícil — concorrência na venda — resolvido e provado."*

---

### Plano B (se a internet/Railway falhar na hora)
- Rode local: `docker compose --profile backend --profile frontend up -d --build` → front em `localhost:5173`.
- Ou mostre os **testes de concorrência** rodando: `./mvnw -pl services/event-service -am test` (com Docker) prova o gate sem depender da rede.
