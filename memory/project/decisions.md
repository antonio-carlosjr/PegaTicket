# Decisões (ADRs) — Pipeline SDD + Ticketeira

> Log de decisões do **time de agentes** e decisões de produto/arquitetura tomadas durante os sprints.
> ADRs de arquitetura **originais** do projeto vivem em [`docs/adr/`](../../docs/adr/) (0001 microsserviços, 0002 db-per-service, 0003 JWT, 0004 RabbitMQ, 0005 monorepo) — referencie-os, não duplique.
> Formato: ADR-Pxx (processo) / ADR-Txx (técnica). Status: Proposta | Aceita | Substituída.

---

## ADR-P01 — Pipeline Spec-Driven com time de agentes
**Status:** Aceita. O desenvolvimento usa 7 agentes (DevOps, PO, Arquiteto, Backend, Frontend, Tester, Revisor) coordenados pelo orquestrador, comunicando via `memory/sprint-<n>/`. Fluxo em [`workflows/pipeline.md`](../../workflows/pipeline.md).

## ADR-P02 — Regra de modelos
**Status:** Aceita. Arquiteto e Revisor → `opus` (decisões/críticas). PO, DevOps, Backend, Frontend, Tester → `sonnet`. **Nunca `haiku`.**

## ADR-P03 — Commits atômicos, Conventional, sem co-autoria
**Status:** Aceita. Cada unidade coesa = 1 commit (`tipo(escopo): assunto`). **Não** usar trailer `Co-Authored-By` (preferência do dono). DevOps padroniza. Detalhe em [`coding-standards.md`](../../rules/coding-standards.md) §4.

## ADR-P04 — Memória in-repo como blackboard
**Status:** Aceita. Artefatos do pipeline vivem em `memory/` (versionado), não na memória automática do Claude. Pull, não push.

## ADR-P05 — Gates de aprovação pausam o pipeline
**Status:** Aceita. `/desenvolver-sprint` para e pede aprovação humana em: PO planning, validação da arquitetura, e aceite final.

## ADR-P06 — Expansão de escopo do Sprint 1
**Status:** Aceita. Inclusão de **US-052** (perfil completo de promotor: CPF, telefone, e-mail de contato, endereço, redes sociais) e **US-053** (ativar/inativar usuários), aprovada pelo dono do produto (Sprint 1 / 2026-06-07). Justificativa: US-052 é pré-requisito para que o admin (US-050) possa avaliar o promotor com informações suficientes; US-053 completa o ciclo de governança de acesso sem o qual a tela admin seria incompleta. Não são scope creep: ambas estão contidas no Épico D e no roadmap RF01. Referência: `memory/sprint-1/00-sprint-spec.md` §2.

## ADR-P07 — Modelo de papel base (role base para todos)
**Status:** Aceita (Sprint 1, pedido do dono). **PARTICIPANTE é a role base de todo usuário autenticado.** Um candidato a promotor é criado/mantido como PARTICIPANTE + uma solicitação `PerfilVerificado(PENDENTE)` — já usa a plataforma como usuário comum. A **aprovação** promove papel→PROMOTOR (e `verificado=true`); a **rejeição** mantém PARTICIPANTE + `motivo_rejeicao` e permite **reenviar**. Aprovação e rejeição **disparam e-mail** (rejeição inclui o motivo). `Usuario.novoPromotorPendente` passa a criar PARTICIPANTE. Inclui **US-054**. Ref: `memory/sprint-1/00-sprint-spec.md` §5.1.

## ADR-P08 — Escopo do Sprint 2 (Eventos)
**Status:** Aceita. Épico A US-020/021/022/023: event-service vira real (criar/editar/publicar/cancelar + listagem/detalhe). Avaliações (US-024/025) adiadas para a Sprint 5. Ref: `memory/sprint-2/00-sprint-spec.md`.

## ADR-P09 — Escopo do Sprint 3 (Inscrição gratuita + ingresso QR)
**Status:** Aceita. Épico B US-030/031/032/033: caminho-feliz **sem dinheiro**, com controle de concorrência (capacidade + dupla inscrição) e ingresso com QR. Caminho pago, check-in e cancelamento ficam para Sprints 4/5. Ref: `memory/sprint-3/00-sprint-spec.md`.

## ADR-P10 — Escopo do Sprint 4 (Pagamento + escrow + saga de inscrição paga)
**Status:** Aceita (gate de planejamento, 2026-06-30, escolha do dono). Épico C US-040 (pagar evento PAGO via gateway **simulado** com retenção em **escrow**) + US-041 (emitir ingresso **só após** `pagamento.aprovado` — saga assíncrona) + Épico E US-060 (consumidores RabbitMQ idempotentes via `processed_events`, fechando a dívida ADR-T04). **Reembolso (US-042) e repasse −10% (US-043) ficam para a Sprint 5** — o escrow aqui apenas retém (`pagamentos.status=CONFIRMADO`), não estorna nem libera. Justificativa: é o feature financeiro central (RF05) e o primeiro a exercitar de fato a arquitetura orientada a eventos (RabbitMQ estava só declarado). Observação: o roadmap §8 do `architectural-plan.md` foi **re-sequenciado** pelos ADRs P08/P09 — "Sprint 4 = carga/observabilidade" deixou de ser o próximo passo; US-061/RNF09 seguem no backlog. Ref: `memory/sprint-4/00-sprint-spec.md`.

## ADR-P11 — Escopo do Sprint 5 ("sprint de fechamento": saga restante + ciclo de vida + qualidade)
**Status:** **Aceita, em execução FASEADA** (gate do dono, 2026-06-30): **5A → 5B → 5C** em ciclos separados de `desenvolver-sprint`, em vez de um mega-sprint único. Ordem confirmada pela dependência: 5A (financeiro) destravada pelo S4 mergeado; 5B (cancelamento pago) depende do reembolso da 5A. **Trilha 5A (financeiro) — MERGEADA** (PR #20, US-042/US-043 DONE): repasse + reembolso por evento cancelado + `evento.finalizado`/`evento.cancelado` + transições de status + TECH-S4-01 fechado. **Trilha 5B (experiência) em desenvolvimento** na branch `feat/sprint-5-experiencia` (US-034 check-in QR + US-035 cancelar inscrição c/ política + reembolso individual `CANCELAMENTO_PARTICIPANTE` reusando o mecanismo da 5A + US-024 avaliar + US-025 reputação). US-062/US-063 (propostos) seguem pendentes para o gate da 5C. Pedido do dono: "toda a saga restante". Escopo em 3 trilhas: **5A financeiro** (US-043 repasse −10% pós-evento REALIZADO + US-042 reembolso por cancelamento, ligando as filas `evento.finalizado` e a nova `evento.cancelado`); **5B experiência** (US-034 check-in QR + US-035 cancelamento c/ política + US-024 avaliar + US-025 reputação); **5C qualidade** (US-061 testes de carga + propostos US-062 observabilidade e US-063 hardening de dívidas ADR-T03/T05 + TECH-S3). **US-060 NÃO entra — já é do Sprint 4** (ADR-P10); o S5 reusa o padrão `processed_events`/`afterCommit`. **Aviso:** escopo ≈2× um sprint normal (7 histórias confirmadas em 4 serviços); recomendado **fasear 5A→5B→5C** ou aprovar como mega-sprint único — decisão do dono no gate. US-062/US-063 são adições do orquestrador ("pode adicionar o que faltar"), pendentes de confirmação. Depende do Sprint 4 mergeado. Ref: `memory/sprint-5/00-sprint-spec.md`.

---

## Dívidas técnicas conhecidas (viram histórias/ADR quando endereçadas)

## ADR-T01 — Papel (role) não vai no JWT
**Status:** Proposta (dívida). Hoje o token carrega só `sub/email/verificado`; o gateway injeta `X-User-Id/Email/Verified`, **sem papel**. Autorização por papel (ex.: ADMIN) não é possível só com o header atual. **Decisão a tomar:** incluir `papel` como claim no JWT + header `X-User-Papel` no gateway (US-051). Até lá, endpoints ADMIN ficam marcados como dívida e o Revisor sinaliza.

## ADR-T02 — `PUT /users/{id}/verify` sem proteção
**Status:** Proposta (dívida). Endpoint flippa `verificado` sem checar ADMIN (`// TODO`), e não atualiza `perfis_verificados.status` (os métodos `aprovar()/rejeitar()` são código morto). **Decisão:** US-050 implementa a tela + endpoint protegido que usa `aprovar()/rejeitar()` e exige papel ADMIN (depende de ADR-T01).

## ADR-T03 — Whitelist do gateway por prefixo
**Status:** Proposta (dívida). `JwtAuthGlobalFilter` usa `startsWith`, casando prefixos demais (ex.: `/api/auth/register-x`). **Decisão:** trocar por match exato no próximo toque no gateway.

## ADR-T04 — Consumidores RabbitMQ não implementados
**Status:** **Aceita / Implementada na Sprint 4 (US-060).** Topologia já declarada (`definitions.json`). A S4 liga os consumidores: `pedido.criado` (payment-service) e `pagamento.aprovado` (ticket-service), **idempotentes** via `processed_events(event_id UUID)` (PK = chave de dedup do payload), com produtor publicando em `afterCommit` (`TransactionSynchronization`). O padrão concreto está fechado no **ADR-T11**. Ref: `memory/sprint-4/{architecture,api-contracts,data-model,tests-spec}.md`.

## ADR-T05 — Seed de admin dev-only
**Status:** Proposta (dívida, Sprint 1). A migration V3 semeia 1 admin (`admin@pegaticket.local`) com hash de senha **em claro no repo**, só para dev/demo. **Decisão:** tornar a credencial env-driven / processo de bootstrap seguro antes de produção (remover o seed ou parametrizar). Ref: `memory/sprint-1/00-sprint-spec.md` §4.

## ADR-T07 — Reserva atômica de vaga (anti-overbooking)
**Status:** **Aceita** (Sprint 3 — Arquiteto). `eventos.vagas_disponiveis` é preparado na Sprint 2 (inicializado = capacidade ao publicar) e consumido na Sprint 3.

**Decisão definitiva (Sprint 3):**
- **Reservar:** `@Modifying @Query` JPQL `UPDATE Evento e SET e.vagasDisponiveis = e.vagasDisponiveis - 1 WHERE e.id = :id AND e.status = PUBLICADO AND e.vagasDisponiveis > 0`, retornando `int rowsAffected`. **1** = reservou; **0** = esgotado ou não-publicado (o service distingue 409/422/404 com um `findById` **só** no caminho frio rowsAffected=0). A cláusula `WHERE vagas > 0` adquire row lock no Postgres → serializa as concorrentes, sem janela entre checar e decrementar. **Sem retry, O(1) por inscrição.**
- **Liberar (compensação):** `UPDATE ... SET vagas = vagas + 1 WHERE id=:id AND status=PUBLICADO AND vagas < capacidade` (incremento **limitado pela capacidade**; no-op idempotente no teto). Não perfeitamente idempotente por reserva individual (sem token de reserva); o teto `< capacidade` é a salvaguarda.
- **Rejeitado `@Version` (optimistic):** no abre-vendas vira tempestade de retries O(n²) numa única linha quente. O `UPDATE...WHERE` atômico é a escolha correta para alta contenção.
- **Defesa em profundidade:** `CHECK (vagas_disponiveis >= 0)` (V2) recusa negativo mesmo em bug.
- **Gate de teste:** concorrência de última vaga (K threads → 1 sucesso, K-1×409, vagas=0) **em Postgres real** (Testcontainers/smoke Docker) — H2 não reproduz o row lock. Detalhe em `memory/sprint-3/architecture.md` §Estratégias críticas e `tests-spec.md` §A4.

## ADR-T08 — Autorização inter-serviço (ticket→event)
**Status:** **Aceita** (Sprint 3 — Arquiteto).

**Achado que muda a decisão:** o gateway tem a rota `events: Path=/api/events/** → event-service` com `StripPrefix=1`. Se os internos ficassem sob `/events/{id}/reservar-vaga`, **qualquer participante autenticado** poderia chamar `POST /api/events/{id}/reservar-vaga` e zerar `vagas_disponiveis` (DoS de vagas). O wildcard **roteia** — "não exposto no gateway" não era verdade automaticamente.

**Decisão definitiva (duas camadas):**
1. **Roteamento:** os internos vivem em prefixo dedicado **`/internal/events/{id}/reservar-vaga`** e `/liberar-vaga`. O gateway **não tem** rota `/api/internal/**` → tentativa externa = **404 no gateway** (nunca chega ao event-service). Como não ficam sob `/events/...`, nem o wildcard `/api/events/**` os alcança.
2. **Autorização:** os internos exigem `X-Internal-Token == ${INTERNAL_SHARED_SECRET}` (env, gitignored, placeholder em `.env.example`). Ausente/errado → **403**. O ticket-service injeta o header no `EventClient`. O gateway **não** injeta nem repassa `X-Internal-Token`.
- **Sem mTLS** no MVP (over-engineering acadêmico); fica como dívida.
- **Gate de teste:** `/api/internal/events/1/reservar-vaga` via gateway → 404; chamada direta sem token → 403 (`tests-spec.md` §C). Detalhe em `architecture.md` §Autorização inter-serviço.

## ADR-T09 — `codigo_unico` do ingresso (QR)
**Status:** **Aceita** (Sprint 3 — Arquiteto).

**Decisão definitiva: UUID v4** (`java.util.UUID.randomUUID()`, gerado no backend, persistido em `codigo_unico VARCHAR(64)`). **HMAC-assinado rejeitado** para o escopo atual.

Justificativa:
- 122 bits de entropia (CSPRNG) → não-forjável na prática; sem padrão sequencial a explorar.
- O **check-in da Sprint 5 será lookup server-side** (`SELECT WHERE codigo_unico=? AND status=ATIVO` + evento correto), **não** verificação criptográfica offline. Para lookup no banco, UUID basta — HMAC só agregaria valor se a validação fosse offline/anti-adulteração sem banco (não é requisito conhecido).
- HMAC agora = abstração precoce (gestão/rotação de chave, formato de payload).
- `UNIQUE(codigo_unico)` é a última linha de defesa contra colisão.
- **Impacto Sprint 5:** check-in faz lookup por `codigo_unico`, marca `ingressos.status=UTILIZADO` + cria `checkins` (`UNIQUE(ingresso_id)` impede duplo check-in). Migrar para HMAC no futuro **invalidaria QRs já emitidos** → por isso a decisão UUID é registrada como definitiva para o escopo atual.

**QR:** o **frontend** renderiza a imagem a partir da string `codigo_unico` (lib JS nova — `qrcode.react` recomendada, justificar em `frontend-log.md`). **Backend NÃO gera imagem.**

## ADR-T10 — Saga de inscrição paga (estados, política de vaga reservada, modelo user-confirm)
**Status:** **Aceita** (Sprint 4 — Arquiteto).

**Contexto:** inscrição em evento PAGO não pode emitir ingresso antes do pagamento, mas a vaga precisa ser reservada **antes** de pagar (anti-overbooking, ADR-T07). Isso cria o risco R1: pagamento abandonado deixa a vaga presa indefinidamente.

**Decisão definitiva:**
- **Saga assíncrona orientada a eventos** (RabbitMQ), não síncrona. Estados de `inscricoes.status`: `PENDENTE_PAGAMENTO` (vaga reservada, **sem ingresso**) → `ATIVA` (após `pagamento.aprovado`, ingresso emitido) ou `EXPIRADA` (TTL). GRATUITO continua `ATIVA` imediata (S3 intacto).
- **Ordem:** validar (PUBLICADO, PAGO, lê `preco`) → pre-check dup → `reservarVaga` (UPDATE atômico, ADR-T07) → tx local cria `Inscricao(PENDENTE_PAGAMENTO)` → `afterCommit` publica `pedido.criado`. Payment consome, cria `Pagamento(PENDENTE)` + computa escrow. Usuário confirma → payment publica `pagamento.aprovado` → ticket emite `Ingresso` + `Inscricao→ATIVA`.
- **Modelo de pagamento: confirmação iniciada pelo usuário** (`POST /payments/{inscricaoId}/confirmar`, 1 toque, gateway **SIMULADO** aprova) — não auto-aprovação; mais realista e demonstrável na banca.
- **Política da vaga reservada (R1): TTL via job agendado.** `ExpiracaoReservaJob` (`@Scheduled`) expira `PENDENTE_PAGAMENTO` mais velhas que `app.reserva.ttl-min` (default 30 min) → `EXPIRADA` + `liberarVaga` (compensação ADR-T07). Idempotente (índice parcial `idx_inscricoes_pendentes`). **Escolhido sobre "gap documentado"** porque a vaga presa fere o critério do PO (capacidade real do evento) e o custo é baixo (1 job + 1 query indexada); para o escopo acadêmico é a mitigação correta sem over-engineering (sem token de reserva, sem state machine externa).
- **Gap aceitável residual (R6):** confirmação que chega **após** a expiração retorna `INSCRICAO_EXPIRADA` (409) sem emitir ingresso — documentado como aceitável.
- **Escrow:** o escrow aqui apenas **retém** (`pagamentos.status=CONFIRMADO`); `valor_taxa=round(bruto*0.10,2)`, `valor_repasse=bruto−taxa` são computados e **não liberados**. Reembolso/repasse = Sprint 5 (ADR-P10/P11).

## ADR-T11 — Idempotência de consumidores AMQP (`processed_events` + `afterCommit`)
**Status:** **Aceita** (Sprint 4 — Arquiteto). Concretiza o ADR-T04.

**Decisão definitiva:**
- **`event_id` (UUID) gerado na ORIGEM** (produtor, `UUID.randomUUID()`) e carregado no payload (`PedidoCriadoEvent`, `PagamentoAprovadoEvent`). É a chave de idempotência.
- **Tabela `processed_events(event_id UUID PK, routing_key, processado_em)` por serviço consumidor** (ticket + payment). O consumidor, **na mesma transação do efeito**, faz `INSERT processed_events(event_id)`: se a PK colidir (2ª entrega at-least-once), a tx desfaz e o consumidor faz **ACK** (no-op, sem efeito duplo).
- **Produtor publica só em `afterCommit`** (`TransactionSynchronizationManager.registerSynchronization`) — se a tx local fizer rollback, o evento nunca sai (testado forçando rollback).
- **Defesa em profundidade (exactly-once-effect):** as constraints UNIQUE existentes `ingressos.inscricao_id` e `pagamentos.inscricao_id` são a rede final, cobrindo até reprocessamento manual com `event_id` diferente.
- **Confirmar pagamento 2×:** transição `PENDENTE→CONFIRMADO` idempotente (no-op se já CONFIRMADO) → publica `pagamento.aprovado` **1×** apenas. Lock pessimista no `findByInscricaoId` serializa confirmações concorrentes.
- **Gate de teste:** reentrega de `pagamento.aprovado` → 1 ingresso; reentrega de `pedido.criado` → 1 pagamento; rollback não publica — **em RabbitMQ + Postgres reais** (Testcontainers, `disabledWithoutDocker=true`). Ref: `memory/sprint-4/tests-spec.md` §A3/A5/B1/B4.

## ADR-T12 — Gatilho de repasse/reembolso por transicao de status do evento (Sprint 5A)
**Status:** **Aceita** (Sprint 5A — Arquiteto). Fecha o gatilho de US-043/US-042 e a divida ADR-T04 (3a/4a fila AMQP).

**Contexto:** o repasse (−10%) e o reembolso em massa precisam de um **gatilho** que sinalize "o evento acabou" / "o evento foi cancelado". A spec deixou em aberto: **job agendado** (`data_fim < now → REALIZADO`) vs. **endpoint do promotor**. O event-service nunca teve RabbitMQ.

**Decisao definitiva:**
- **Gatilho REALIZADO = endpoint `POST /events/{id}/encerrar`** (PUBLICADO→REALIZADO), **com job como evolucao futura documentada** (mesmo padrao do `ExpiracaoReservaJob`/S4). Escolhido por ser **demonstravel na banca** (Marina clica "Encerrar" e ve o repasse no extrato em segundos), deterministico nos testes (sem relogio), e simetrico ao `POST /events/{id}/cancelar` ja existente. `Evento.realizar()` ganha guard (`PUBLICADO→REALIZADO`; demais estados → `TRANSICAO_INVALIDA`/`EVENTO_JA_REALIZADO` 409), espelhando `publicar()`/`cancelar()`. Auth = PROMOTOR + ownership (mesmo `carregarComOwnership`); `X-User-Papel` ja injetado pelo gateway e lido pelo `EventController` → **sem divida nova de auth**.
- **Gatilho CANCELADO = `POST /events/{id}/cancelar`** (ja existe; guard ja impede 2x). Passa a publicar `evento.cancelado` em afterCommit; contrato REST inalterado.
- **event-service ganha sua primeira RabbitConfig + EventoPublisher** (so produz). Padrao S4 obrigatorio: **delegar RabbitTemplate/ConnectionFactory ao autoconfigure** (so declarar `Jackson2JsonMessageConverter`+`JavaTimeModule` + exchanges/filas/bindings); NAO `@ConditionalOnBean(ConnectionFactory.class)`; publish em `afterCommit` (rollback nao publica). `eventId` (UUID) gerado na origem = chave de idempotencia (ADR-T11).
- **2 eventos AMQP:** `evento.finalizado` (fila ja em definitions.json; consumidor=payment) e **`evento.cancelado` (fila NOVA; consumidores=payment E ticket)**. Fan-out: como payment e ticket sao consumidores **independentes**, cada um tem **sua propria fila** ligada a routing key `evento.cancelado` — `evento.cancelado` (payment) e `evento.cancelado.ticket` (ticket), cada uma com DLQ. (1 fila compartilhada = competing consumers → so um receberia.)
- **Vagas no cancelamento:** o status CANCELADO no event-service ja impede novas reservas (`reservarVaga` so atua em PUBLICADO), entao o ticket **nao chama liberar-vaga** no fan-out (evita I/O cross-service inutil). Recomendacao: `Evento.cancelar()` reseta `vagasDisponiveis = capacidade` (mutacao local) para o banco refletir literalmente o criterio do PO — **PO valida na Fase 3**.

## ADR-T13 — Saga de repasse e reembolso (escrow → REPASSADO/REEMBOLSADO) + persistencia evento_id/promotor_id (TECH-S4-01) (Sprint 5A)
**Status:** **Aceita** (Sprint 5A — Arquiteto). Fecha **TECH-S4-01**.

**Decisao definitiva:**
- **TECH-S4-01:** `pagamentos += evento_id BIGINT, promotor_id BIGINT` (migration payment **V3**, NULLABLE p/ legado), **populados no `criarPendente`** a partir do `pedido.criado` — que **ja carrega `eventoId`/`promotorId`** no payload desde o S4 (nenhum produtor muda; so o `Pagamento`/factory passam a grava-los). +`repassado_em`/`reembolsado_em` (auditoria) + `idx_pagamentos_evento`. Necessario para o repasse/reembolso filtrarem `WHERE evento_id=? AND status='CONFIRMADO'`.
- **Repasse (US-043):** consumidor `evento.finalizado` idempotente (`processed_events`) → **1 UPDATE condicional em massa** `SET status=REPASSADO, repassado_em=now WHERE evento_id=:e AND status='CONFIRMADO'`. `valor_repasse` ja computado no S4 (bruto−10%) — so muda status/carimbo. Pagamento nao-CONFIRMADO nao e tocado (US-043 crit.4). Reentrega → PK colide → ACK no-op.
- **Reembolso em massa (US-042):** consumidor `evento.cancelado` (payment) idempotente → `SELECT ... WHERE evento_id=:e AND status='CONFIRMADO' FOR UPDATE` + loop: `reembolsar()` (`→REEMBOLSADO`) + `INSERT reembolsos(motivo='EVENTO_CANCELADO', status='PROCESSADO', valor=valor_bruto)`. `Reembolso` passa de read-only a **escrito** (ganha factory `criar`). Reembolso e simulado/imediato (sem gateway real). Consumidor ticket (`evento.cancelado.ticket`): UPDATE condicional `inscricoes ATIVA|PENDENTE_PAGAMENTO→CANCELADA` + `ingressos ATIVO→CANCELADO` (ingresso UTILIZADO preservado).
- **Corrida repasse-vs-reembolso no mesmo pagamento:** ambas as transicoes sao **condicionais em `status='CONFIRMADO'`**; o row lock do Postgres (UPDATE.../SELECT FOR UPDATE) serializa → **exatamente um vence**, o outro e no-op (0 linhas). Garante que cada pagamento termina em **um** de {REPASSADO, REEMBOLSADO}, nunca os dois.
- **Idempotencia:** `processed_events(event_id UUID PK)` reusado em payment e ticket (ambos ja tem a tabela; event-service nao consome → nao ganha a tabela). Defesa extra **opcional**: `UNIQUE(pagamento_id) WHERE motivo='EVENTO_CANCELADO'` em `reembolsos` (anti-duplicata mesmo com eventId diferente) — **PO valida**.
- **Reembolso reusavel para 5B:** o mecanismo (`Reembolso.criar` + `reembolsar()`) e generico (motivo parametrizado); 5B (US-035, `CANCELAMENTO_PARTICIPANTE`) reusa sem reescrever. **Nesta trilha so o caminho `EVENTO_CANCELADO` e implementado.**
- **Migrations:** payment `V3__repasse_reembolso.sql`; event `V3__realizado_cancelado.sql` (`realizado_em`/`cancelado_em`); ticket **sem migration** (status CANCELADA/CANCELADO + processed_events ja existem). infra: `evento.cancelado` (+`.ticket`) + DLQs em `definitions.json`.
- **Gate de teste (PG+Rabbit reais, Testcontainers):** `evento.finalizado` reentregue → repasse 1x; reembolso em massa + idempotencia; corrida repasse-vs-reembolso → 1 vencedor; `evento.cancelado` cancela inscricoes/ingressos (ticket). Ref: `memory/sprint-5/5a/{architecture,api-contracts,data-model,tests-spec}.md`.

## ADR-T14 — Autorizacao do check-in: papel PROMOTOR + ownership do evento (Sprint 5B)
**Status:** **Aceita** (Sprint 5B — Arquiteto). Fecha US-034.

**Contexto:** o check-in por QR (`POST /tickets/checkin {codigo_unico}`) deve ser feito **so pelo promotor dono** do evento — papel PROMOTOR sozinho nao basta (qualquer promotor validaria qualquer QR; R4 da spec).

**Decisao definitiva:**
- **Auth de duas checagens:** `X-User-Papel == PROMOTOR` (403 `Acesso restrito a promotores.` senao) **+ ownership** do evento. O ticket-service faz lookup `Ingresso by codigo_unico` -> `inscricao_id` -> `evento_id`, e descobre o **dono** via `EventClient.getEvento(eventoId)` (canal interno ADR-T08 ja existente) comparando `evento.promotorId() == userId`. Diferente -> **403** `CHECKIN_EVENTO_ALHEIO`. `EventResumo.promotorId()` **ja existe** — nenhum campo novo para a ownership.
- **Transicao:** `Ingresso.realizarCheckin()` (NOVO, distinto do `utilizar()` idempotente da 5A): `ATIVO -> UTILIZADO`; ja UTILIZADO -> **lanca 409** `INGRESSO_JA_UTILIZADO` (criterio US-034.2, nao no-op). `codigo_unico` inexistente/CANCELADO -> **404** `INGRESSO_NAO_ENCONTRADO`.
- **Concorrencia (US-034.6):** barreira atomica final = `UNIQUE(ingresso_id)` em `checkins` (ja existe V1) — 2 devices simultaneos: 1 grava `checkins`+UTILIZADO, o outro colide -> 409. Sem duplicata. Gate de teste em **Postgres real** (H2 nao reproduz o row lock — mesmo racional ADR-T07).
- **Lookup, nao cripto:** consistente com ADR-T09 (UUID v4; check-in = lookup server-side). **Sem divida nova de auth** (`X-User-Papel` ja injetado pelo gateway desde a 5A). **Sem migration** (`checkins` ja existe; `Checkin` entity mapeia tabela existente). Ref: `memory/sprint-5/5b/{architecture,api-contracts,data-model,tests-spec}.md`.

## ADR-T15 — Cancelamento da inscricao + reembolso individual via AMQP + politica de prazo no ticket (Sprint 5B)
**Status:** **Aceita** (Sprint 5B — Arquiteto). Fecha US-035 + caminho individual de US-042 (`CANCELAMENTO_PARTICIPANTE`).

**Contexto:** participante cancela a propria inscricao (`DELETE /tickets/inscricoes/{id}`). Se PAGO e dentro do prazo, dispara reembolso individual **reusando o mecanismo da 5A** (sem duplicar). Fora do prazo -> bloqueia (PO-D2). Onde checar o prazo? Como disparar o reembolso?

**Decisao definitiva:**
- **Gatilho do reembolso individual = evento AMQP `inscricao.cancelada`** (NAO chamada sincrona). O ticket publica em `afterCommit` `inscricao.cancelada { eventId(UUID), inscricaoId, usuarioId, eventoId, ocorridoEm }`; o payment consome (idempotente via `processed_events`) e reembolsa **o pagamento daquela inscricao** reusando `findByInscricaoIdForUpdate(inscricaoId)` (lock pessimista; `pagamentos.inscricao_id` e UNIQUE -> 1 pagamento) + `Pagamento.reembolsar()` (CONFIRMADO->REEMBOLSADO, condicional) + `Reembolso.criar(...,'CANCELAMENTO_PARTICIPANTE')` (o factory ja e generico desde a 5A). **Justificativa vs. sincrono (token interno):** consistencia com a 5A (uma unica forma de estornar), desacoplamento/resiliencia (payment fora do ar nao falha o cancelamento — at-least-once + DLQ), idempotencia gratuita (`processed_events`). O extrato e eventualmente consistente, como na 5A — o criterio do PO nao exige sincronia.
- **Topologia:** consumidor unico (payment) -> **1 fila** `inscricao.cancelada` (+DLQ) — **sem fan-out** (diferente de `evento.cancelado` que tem `.ticket`). Nova fila/binding/DLQ em `definitions.json` + payment RabbitConfig; ticket so produz.
- **Politica de prazo checada NO TICKET** (antes de cancelar, so para PAGO): dentro do prazo sse `now <= data_inicio - prazo_reembolso_dias`. Fora -> **422** `PRAZO_CANCELAMENTO_ENCERRADO` (PO-D2: bloqueia; inscricao permanece ATIVA; nenhum reembolso). GRATUITO: sempre cancela, sem reembolso (US-035.1). **Dados faltavam:** `EventoInternoResponse`/`EventResumo` passam a expor `dataInicio` + `prazoReembolsoDias` (sem migration — colunas ja existem em `eventos`). Quebra a aridade do record `EventResumo` -> fixtures atualizadas.
- **Concorrencia (US-035.5):** cancelamento voluntario via `UPDATE inscricoes SET status=CANCELADA WHERE id=? AND status IN (ATIVA,PENDENTE_PAGAMENTO)` (rowsAffected: 1=sucesso, 0=409 `INSCRICAO_JA_CANCELADA`). Distinto do `cancelarPorEvento()` no-op da 5A (consumidor AMQP). 2 cancelamentos simultaneos -> 1 vence, vaga liberada 1x (so o vencedor chama `liberarVaga`, ADR-T07). `inscricao` alheia -> 403 `CANCELAMENTO_DE_OUTRO`.
- **Corrida individual-vs-massa no mesmo pagamento:** ambos `reembolsar()` condicional sob lock -> 1 vence (invariante: 1 unico estorno por pagamento). **Sem migration** (`reembolsos.motivo` CHECK ja aceita `CANCELAMENTO_PARTICIPANTE` V1; `processed_events` V2). **ACK no-op** (CR-S4-01) p/ pagamento ausente (gratuito) ou nao-CONFIRMADO. Ref: `memory/sprint-5/5b/*`.

## ADR-T16 — Elegibilidade de avaliacao cross-service + reputacao agregada (Sprint 5B)
**Status:** **Aceita** (Sprint 5B — Arquiteto). Fecha US-024 + US-025.

**Contexto:** o event-service nao conhece inscricoes/check-in (dominio do ticket), mas precisa autorizar quem avalia (PO-D1: ingresso `UTILIZADO` **ou** inscricao `ATIVA` em evento `REALIZADO`). E precisa expor a reputacao (media+total) no detalhe.

**Decisao definitiva:**
- **Elegibilidade: event-service chama o ticket-service** por canal interno `GET /internal/tickets/participou?usuarioId=&eventoId=` -> `{participou: boolean}`, com `X-Internal-Token` (ADR-T08; 403 senao; gateway nao roteia `/api/internal/**`). **Justificativa vs. inverter (ticket consulta avaliacoes):** a avaliacao e do dominio do event (tabela `avaliacoes` ja vive la, `UNIQUE(evento,usuario)`); o event e quem decide 201/403/409, entao **pergunta** ao ticket "participou?". Surge a 1a chamada **outbound** event->ticket (`TicketClient` + `TicketClientConfig`, espelhando `EventClient`/`EventClientConfig` do ticket: RestClient, timeouts 2s/3s, `defaultHeader X-Internal-Token`, `app.ticket-service.url`).
- **Regra dividida (PO-D1):** `elegivel = evento.status==REALIZADO (event, pre-filtro local barato) AND ticket.participou (ticket: EXISTS ingresso UTILIZADO OU inscricao ATIVA p/ usuario+evento)`. Inscricao CANCELADA/EXPIRADA -> nao conta. Ingresso UTILIZADO so habilita avaliacao **apos** o evento virar REALIZADO (pre-filtro). Nao-elegivel -> **403** `AVALIACAO_NAO_ELEGIVEL`; 2a avaliacao -> **409** `AVALIACAO_DUPLICADA` (UNIQUE); nota fora 1-5 -> **400** (`@Min/@Max`). `TicketClient` falha **fechada** (503 `TICKET_INDISPONIVEL`, nunca 500).
- **Reputacao (US-025):** query agregada unica `SELECT new ReputacaoResponse(AVG(a.nota), COUNT(a)) FROM Avaliacao a WHERE a.eventoId=:id` (usa `idx_avaliacoes_evento` V1; **sem N+1**, O(1)). Exposta em `EventoResponse.reputacao = {media:Double|null, total:long}` (null/0 sem avaliacoes), calculada em `EventService.detalhe` **sem cache** (US-025.2). Qualquer autenticado le (US-025.3).
- **Sem migration** (`avaliacoes` ja existe V1 com UNIQUE+CHECK; `Avaliacao` entity nova mapeia tabela existente). Ref: `memory/sprint-5/5b/*`.

---

> Toda nova decisão estrutural tomada por um agente durante um sprint é registrada aqui (com referência ao sprint), e recorrências de code review viram regra em `coding-standards.md`.
