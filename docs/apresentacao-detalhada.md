# Roteiro de Apresentação Detalhado — PegaTicket

> Apresentação completa para a banca: visão do produto, **parte técnica a fundo por sprint**, demo guiada (passo a passo + falas), engenharia/qualidade e perguntas prováveis com respostas.
> Versão enxuta (só demo): [`roteiro-apresentacao.md`](roteiro-apresentacao.md). · Usuários de teste: [`usuarios-e-jornadas-teste.md`](usuarios-e-jornadas-teste.md).
>
> **Produção:** front `https://frontend-theta-sooty-61.vercel.app` · gateway `https://api-gateway-production-c4b4.up.railway.app`.

---

## 0. Antes de começar (checklist + tempos)

- **Aquecer** os serviços 5 min antes (logar e navegar uma vez) — evita o cold-start de ~8 s na hora.
- Ter 3 abas/papéis prontos: **participante**, **promotor verificado**, **admin**.
- Tempo sugerido: **Abertura 3' · S1 4' · S2 4' · S3 6' · Extras 2' · Engenharia/Deploy 3' · Fechamento 2'** (~24 min + perguntas).

### Divisão sugerida (grupo de 5)
| Pessoa | Bloco |
|---|---|
| 1 | Abertura + arquitetura geral |
| 2 | Sprint 1 (Identidade & Autorização) |
| 3 | Sprint 2 (Eventos) |
| 4 | Sprint 3 (Inscrição & Ingresso QR) — o destaque técnico |
| 5 | Engenharia/Qualidade + Deploy + Fechamento |

---

## 1. Abertura — o produto e a arquitetura (≈3 min)

**Fala de abertura:**
> "O PegaTicket é uma plataforma de eventos com ingresso por QR code. O usuário descobre eventos, se inscreve e recebe um ingresso único; o promotor publica eventos; o admin governa a plataforma. O diferencial de engenharia é a **arquitetura em microsserviços** e o **tratamento de concorrência na abertura de vendas** — o problema clássico de *overbooking*."

**Slide de arquitetura (desenhar/mostrar):**

```
                       [ Frontend React/TS — Vercel ]
                                   │  HTTPS (JWT no header)
                                   ▼
                    ┌──────────────────────────────┐
                    │  API GATEWAY (Spring Cloud)   │  valida o JWT,
                    │  filtro global injeta         │  injeta X-User-Id/Email/
                    │  X-User-* ; roteia /api/**     │  Verified/Papel
                    └──────────────────────────────┘
        ┌───────────────┬──────────────┬───────────────┬──────────────┐
        ▼               ▼              ▼               ▼              ▼
   user-service    event-service   ticket-service  payment-service  (RabbitMQ)
     user_db         event_db        ticket_db       payment_db    topologia
   (Postgres)       (Postgres)      (Postgres)      (Postgres)      em código
```

**Pontos a falar:**
- **API Gateway** é a única porta pública; valida o token e injeta a identidade nos headers.
- **4 microsserviços** independentes, **um banco por serviço** (4 DBs lógicos no Postgres) — sem FK entre bancos; integração via REST/mensageria.
- **Stack:** Java 21 / Spring Boot 3.3 (monorepo Maven) · React 18 + TypeScript + Vite + Tailwind · Postgres · RabbitMQ · deploy Railway (back) + Vercel (front) + Resend (e-mail).
- **3 sprints:** Identidade → Eventos → Inscrição/QR. Cada uma transforma um "esqueleto" num serviço real.

---

## 2. Sprint 1 — Identidade & Autorização (≈4 min)

### 2.1 Objetivo
> "Saber quem é cada um e o que pode fazer: cadastro com papéis, autenticação stateless por JWT, e um fluxo de verificação de promotor com aprovação do admin e e-mail."

### 2.2 Parte técnica (a fundo)
- **Autenticação stateless com JWT** (HS, biblioteca jjwt). O token carrega *claims*: `sub` (id), `email`, `papel`, `verificado`. **Quem valida o token é o gateway**, num **filtro global** (ordem -100, antes do roteamento). Ele extrai os claims e **injeta headers** `X-User-Id`, `X-User-Email`, `X-User-Papel`, `X-User-Verified`.
- **Os microsserviços NÃO revalidam o JWT** — confiam nos headers do gateway (config `STATELESS`, `permitAll`). Isso mantém os serviços simples e sem estado de sessão.
- **Anti-spoofing:** o filtro **sobrescreve** qualquer `X-User-*` que o cliente tente enviar — então não dá pra forjar o papel pela requisição. *(Bug pego e corrigido na revisão: um `RemoveRequestHeader` rodava depois do filtro e apagava o header injetado → 500; removido.)*
- **Senhas:** sempre `BCrypt` (`BCryptPasswordEncoder`); o hash nunca é logado/serializado.
- **Papéis:** `PARTICIPANTE` é o **papel base** (todos começam assim). `PROMOTOR` é um *upgrade* verificado; `ADMIN` governa.
- **Verificação de promotor:** ao se cadastrar como promotor, cria-se um `PerfilVerificado` com status **PENDENTE** (o usuário continua PARTICIPANTE até a aprovação). O admin **aprova** (→ papel PROMOTOR, verificado, perfil VERIFICADO) ou **rejeita com motivo**. **Fonte autoritativa do "pendente" é o status do perfil**, não o papel.
- **Reset de senha:** token de uso único **com hash no banco** + TTL; o link vai por e-mail. (Login e forgot-password devolvem resposta **genérica** — anti-enumeração de e-mails.)
- **Meu perfil** (entregue além do escopo): cada papel edita seus dados (promotor: perfil rico com **máscara** de CPF/telefone e **CEP via ViaCEP**) e **troca a senha** ali direto — exigindo a senha atual (validada por BCrypt).

### 2.3 Demo (passo a passo)
1. Cadastrar **participante** → entra direto (auto-login) → home com badge "Participante".
2. Cadastrar **promotor** → mostrar o perfil rico (CPF/telefone com máscara, **CEP preenchendo o endereço — ViaCEP**) → vira pendente.
3. Logar como **admin** → **Gerenciar usuários** → o promotor aparece com badge **"Promotor pendente"** → **Aprovar**.
4. Logar como o promotor → agora **"Promotor verificado"**, com "Criar evento".
5. (Opcional) **Meu perfil** → trocar a senha.

### 2.4 Perguntas prováveis
- *"Como impede forjar o papel?"* → O serviço não lê papel do cliente; só o gateway injeta `X-User-Papel` a partir do JWT que ele mesmo valida, e o filtro sobrescreve qualquer header externo.
- *"Por que JWT e não sessão?"* → Stateless escala horizontalmente sem sticky session; cada serviço é independente.

---

## 3. Sprint 2 — Eventos (≈4 min)

### 3.1 Objetivo
> "O event-service vira um serviço real: CRUD de eventos com **máquina de estados**, **ownership**, e uma listagem pública com filtros."

### 3.2 Parte técnica (a fundo)
- **`@Entity Evento`** mapeada 1:1 ao schema (Flyway versiona o banco; `ddl-auto: validate` — Hibernate nunca gera o schema). Enums `TipoEvento` (GRATUITO/PAGO) e `StatusEvento`.
- **Máquina de estados:** `RASCUNHO → PUBLICADO → CANCELADO/REALIZADO`. Transição inválida → **409 tipado**. As regras vivem na **entidade** (métodos de domínio `publicar()`, `cancelar()`), não no controller.
- **Ownership:** um promotor só vê/edita os próprios eventos. Não-dono recebe **404** (não vaza existência) em vez de 403.
- **Autorização por papel na borda:** criar evento exige `X-User-Papel == PROMOTOR` (verificado antes de tocar o banco).
- **Listagem pública** com filtros `q` (título/local), `tipo`, `de`, `ate`, paginação. Detalhe técnico: a query usa `CAST(:q AS string)` para **evitar o bug do Postgres `lower(bytea) does not exist`** quando o filtro é null.
- **Timezone:** banco em **UTC** (`hibernate.jdbc.time_zone: UTC`), `OffsetDateTime` nas bordas. *(Aprendizado: `<input datetime-local>` gera valor sem offset e o backend rejeitava → 500; o front converte para ISO **com offset** antes de enviar — em formulários E filtros.)*
- **Banco por serviço:** `event_db` próprio; referências a usuário são `BIGINT` simples (sem FK cross-service).

### 3.3 Demo
1. Como **promotor verificado** → **Criar evento** (wizard: nome/descrição → datas/local/capacidade → tipo gratuito) → **Publicar**.
2. Como **participante** → **Eventos**: busca por título, filtro por tipo, filtro por data, paginação.
3. **Detalhe**: datas no fuso do usuário (BRT), capacidade, vagas, preço.

### 3.4 Perguntas prováveis
- *"E se dois promotores mexerem no mesmo evento?"* → Ownership garante que só o dono edita; concorrência pesada de capacidade é a Sprint 3.
- *"Como garante o schema?"* → Flyway (migrations versionadas) + `ddl-auto: validate`; a entidade só **valida** contra o schema, não o cria.

---

## 4. Sprint 3 — Inscrição & Ingresso QR (≈6 min) — **o destaque técnico**

### 4.1 Objetivo
> "Participante se inscreve em evento gratuito e recebe um ingresso único com QR. O coração é **concorrência**: capacidade atômica e sem dupla inscrição, numa mini-saga entre dois serviços."

### 4.2 Parte técnica (a fundo — é aqui que se ganha a banca)

**(a) Mini-saga síncrona cross-service** (ticket-service → event-service), **sem transação distribuída**:
```
1. validar evento   → GET /internal/events/{id}      (existe? PUBLICADO? GRATUITO?)
2. reservar vaga    → POST /internal/events/{id}/reservar-vaga   (decremento atômico)
3. tx local         → INSERT inscricao + INSERT ingresso          (atômico)
4. compensar se (3) → POST /internal/events/{id}/liberar-vaga     (devolve a vaga)
```

**(b) Sem overbooking — decremento atômico:**
```sql
UPDATE eventos SET vagas_disponiveis = vagas_disponiveis - 1
 WHERE id = ? AND status = 'PUBLICADO' AND vagas_disponiveis > 0
```
- A **cláusula `WHERE` é a checagem** e o **`UPDATE` é a ação**, numa única instrução → **sem janela** check-then-act. O `rowsAffected` diz se reservou (1) ou não (0). O **row lock** do Postgres serializa os concorrentes na mesma linha. Há ainda um `CHECK (vagas_disponiveis >= 0)` como defesa em profundidade.

**(c) Sem dupla inscrição:** `UNIQUE(usuario_id, evento_id)`. Há um pré-check (otimização), mas a **corrida real** (dois POSTs do mesmo usuário) é resolvida no **INSERT** pela constraint → `DataIntegrityViolationException` → **409 `JA_INSCRITO`** (e compensa a vaga).

**(d) Compensação:** `liberar-vaga` é **idempotente** e limitada pela capacidade (não estoura o teto). Se a compensação também falhar, loga `[RECONCILIACAO]` para tratamento manual — a vaga nunca fica silenciosamente presa.

**(e) Detalhe fino de transação:** `inscrever()` **não** é `@Transactional`. A tx local (passo 3) é criada via `TransactionTemplate (REQUIRES_NEW)`, **fora** do escopo do `catch` da compensação. Motivo: a `DataIntegrityViolationException` só materializa no *flush/commit*; encerrar a tx **antes** do catch permite a compensação rodar fora de um contexto em rollback.

**(f) Segurança do canal interno (ADR-T08):** os endpoints `/internal/**` **não são roteados pelo gateway** (defesa de roteamento) e exigem **`X-Internal-Token`** (segredo compartilhado, comparação **constante-no-tempo** com `MessageDigest.isEqual`). Eles **não** leem `X-User-*` — a validação do evento também passa por `/internal/events/{id}` (e não pelo detalhe público, que exige usuário).

**(g) Ingresso & QR:** `codigo_unico` = **UUID v4**, com `UNIQUE`. O **QR é renderizado no front** (`qrcode.react`) a partir do código — o backend **nunca** gera imagem.

**(h) Prova de qualidade — concorrência validada em 3 camadas:**
1. **Smoke real:** 20 requisições paralelas na última vaga → **exatamente 5 sucessos / 15 esgotado**, `vagas` nunca negativo.
2. **Testcontainers (Postgres real) no CI Linux:** `VagaConcorrenciaTest` (até 50–100 threads) e `InscricaoConcorrenciaTest` — verdes.
3. **Fluxo real em produção** (inscrição → 201 + ingresso QR; dupla → 409).

### 4.3 Demo
1. Como **participante** → detalhe de um evento gratuito → **Inscrever-se** → o **ingresso com QR** aparece na hora.
2. **Meus ingressos** → card com o **QR** renderizado.
3. **Inscrição duplicada** → "Você já está inscrito" (409).
4. **Minhas inscrições** → histórico paginado.

### 4.4 Perguntas prováveis (e respostas)
- *"E se a reserva der certo mas a criação do ingresso falhar?"* → **Compensação** libera a vaga; se a compensação falhar, loga `[RECONCILIACAO]`. Nunca prende vaga em silêncio.
- *"Por que síncrono e não fila?"* → Gratuito é caminho-feliz curto. O **caminho pago** (escrow via fila AMQP, idempotência, DLQ) é a Sprint 4.
- *"Como sabem que não há overbooking de verdade?"* → Não é "achismo": 20 requisições paralelas + Testcontainers em Postgres real no CI provam exatamente N sucessos.
- *"Por que `UPDATE ... WHERE` e não `SELECT` depois `UPDATE`?"* → O `SELECT`+`UPDATE` tem janela de corrida; o `UPDATE` condicional único elimina a janela com o row lock.

---

## 5. Funcionalidades extras entregues (≈2 min)

- **Meu perfil** (todos os papéis): editar dados + trocar senha; promotor edita o perfil rico com máscara.
- **Integração ViaCEP** no cadastro/perfil: ao digitar o CEP, preenche e **trava** o endereço; se não achar, libera pra preencher manual.
- **Home do admin** com painel de controle (cards de usuários/pendentes/verificados clicáveis + status da plataforma).
- **Resiliência a cold-start:** o front re-tenta automaticamente leituras (GET) em falha transitória de infra — a navegação não quebra quando um serviço está "acordando".

---

## 6. Engenharia & Qualidade (≈2 min) — *parte técnica transversal*

- **Processo SDD (Spec-Driven Development):** time de agentes com *gates* (planejamento → arquitetura → TDD → review), **commits atômicos por estória** (`tipo(US-id): assunto`), e **code review adversarial**.
- **A lição transversal "H2 ≠ Postgres":** os testes rápidos usam H2, que **mascara bugs de runtime** (tipos `CHAR(2)` vs `varchar`, `lower(bytea)`, etc.). Por isso **validamos sempre contra Postgres real** (smoke + Testcontainers) — isso pegou bugs que o CI com H2 escondia.
- **CI (GitHub Actions):** `mvn verify` no reactor inteiro em `ubuntu-latest` (com Docker → **os testes de concorrência rodam de verdade**) + build/lint do front.
- **Bugs reais pegos na validação** (exemplos): hash do admin que não batia; header injetado sendo apagado; saga dando 503 por *wiring* de serviço faltando; rota inexistente virando 500 (corrigido p/ 404). Todos documentados.

## 7. Deploy & Operação (≈1 min)

- **Railway** (backend): 5 serviços (gateway + 4) + Postgres + RabbitMQ, build por **Dockerfile**, serviços conversam por rede interna (`*.railway.internal`); só o gateway é público.
- **Vercel** (frontend): build Vite, SPA.
- **Resend** (e-mail): SMTP porta 2587. **Disparos ativos:** boas-vindas, aprovação/rejeição de promotor, reset de senha.
- **Observação honesta:** os serviços têm *App Sleeping* (economia) → cold-start de ~8 s na 1ª requisição; mitigado no front com auto-retry de leituras. Para a banca: aquecer antes.

## 8. Fechamento (≈2 min)

> "Em três sprints saímos de esqueletos para uma plataforma de eventos **funcional em produção**, com o problema difícil — **concorrência na venda** — resolvido e **provado em três camadas**. A arquitetura em microsserviços, a autorização no gateway e a disciplina de validar em Postgres real são os pilares de engenharia que sustentam isso."

---

## Apêndice A — Stack e decisões (ADRs)

| Tema | Decisão |
|---|---|
| Autorização | JWT validado no gateway; serviços confiam em `X-User-*` (stateless) |
| Banco | 1 banco lógico por serviço; sem FK cross-service; Flyway + `ddl-auto: validate` |
| Concorrência de vaga (ADR-T07) | `UPDATE ... WHERE vagas>0` + `rowsAffected`; `UNIQUE(usuario,evento)` |
| Canal interno (ADR-T08) | `/internal/**` fora do gateway + `X-Internal-Token` constante-no-tempo |
| Código do ingresso (ADR-T09) | UUID v4 (`codigo_unico`), QR renderizado no front |
| Mensageria | Topologia declarada **em código** (RabbitMQ não monta `definitions.json` na Railway) |

## Apêndice B — Perguntas técnicas difíceis (cola de respostas)

- **"Transação distribuída?"** → Não. Mini-saga síncrona com compensação; o atômico fica dentro de cada serviço (um `UPDATE` no event, dois `INSERT` no ticket).
- **"Consistência eventual?"** → No caminho gratuito é praticamente imediata; a única janela é falha entre reservar e a tx local, coberta por compensação + reconciliação.
- **"E o ponto único de falha do gateway?"** → O gateway é stateless e replicável; em produção sobe atrás de um balanceador. Cada serviço também é independente.
- **"Por que monorepo?"** → Um reactor Maven, versões e dependências compartilhadas (`common-lib`), build/CI únicos; cada serviço ainda deploya isolado.
- **"Segurança do token interno em texto plano?"** → No MVP é segredo compartilhado + isolamento de rede (ADR-T08); evolui para mTLS/secret manager. A comparação já é constante-no-tempo.
- **"Como garante 1 ingresso por inscrição?"** → `UNIQUE(inscricao_id)` no ingresso + emissão dentro da mesma tx local da inscrição.
