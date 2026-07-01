# Code review acumulado — Sprint 5 · Trilha 5B (experiência)

> Aprendizados que viraram regra em `rules/coding-standards.md` / dívida em `backlog.md`. PR [#21](https://github.com/antonio-carlosjr/PegaTicket/pull/21), CI verde em **1 ciclo** (Testcontainers PG+Rabbit).

## Promovido a regra (`coding-standards.md`)
- **§Mensageria — Publisher com `RabbitTemplate` opcional loga a perda em `WARN`, não `DEBUG`.** O padrão "injeção opcional + no-op se null" (nascido para subir contexto H2 sem mockar) reapareceu 3× (`PagamentoAprovadoPublisher` S4, `PedidoCriadoPublisher`/`InscricaoCanceladaPublisher` 5B). Em produção `RabbitTemplate==null` = autoconfig do broker falhou = evento de domínio (saga/reembolso) perdido em silêncio. WARN com `inscricaoId/eventId`. *(CR-5B-01, P1)*
- **§Concorrência — Mutação de `status` disputada por 2 caminhos usa `UPDATE...WHERE status=<esperado>` (rowsAffected), não dirty-check do JPA.** O `save()` de entidade carregada sem lock gera `UPDATE...WHERE id=?` → last-writer-wins. Se a `architecture.md` declara "transição condicional serializa", o Revisor confirma que a query é de fato condicional. *(CR-5B-02, generaliza ADR-T07/T15)*

## Revisor (opus) — 0 P0/P1 (1 P1 aplicado); confirmou correto
- **Idempotência AMQP:** `InscricaoCanceladaListener` grava `processed_events` na mesma tx + ACK no-op (pagamento ausente/PENDENTE/já REEMBOLSADO → `false` sem lançar) → sem poison message (CR-S4-01 respeitado).
- **afterCommit real:** `EventClient.getEvento` (I/O de rede) roda **antes** do `TransactionTemplate` → nenhum lock de banco durante HTTP (CR-S4-02 respeitado). Publish só pós-commit; rollback (409) não publica.
- **Corrida reembolso individual vs. massa:** ambos `PESSIMISTIC_WRITE` + transição `reembolsar()` condicional `status='CONFIRMADO'` → exatamente 1 vence. Chaves de idempotência não colidem (motivos/`eventId` distintos) — a garantia vem da transição sob lock, não da chave.
- **Check-in duplo:** barreira atômica = `UNIQUE(ingresso_id)` + `saveAndFlush(Checkin)`; `Ingresso.realizarCheckin()` lança 409 no caminho sequencial (distinto do `utilizar()` no-op da 5A).
- **Segurança:** token interno via `MessageDigest.isEqual` (não `equals`); `TicketClient` outbound (1ª mensageria síncrona do event) com timeouts 2s/3s, fail-closed 503, sem SSRF (URL de config + path fixo), token nunca logado.
- **Reputação sem N+1:** `agregarReputacao` = 1 query `SELECT new ReputacaoResponse(AVG, COUNT)`; `participou` = 2 `EXISTS` indexados.

## Follow-ups P2/P3 (→ backlog TECH-S5B, 5C)
- CR-5B-02 corrida check-in/cancel = last-writer-wins no `ingressos.status` (doc alinhada; hardening 5C). → `TECH-S5B-01`
- CR-5B-03 construtor de compat do `PagamentoService` deixa `reembolsoRepository=null` (NPE latente test-only). → `TECH-S5B-02`
- CR-5B-04 handler genérico `DataIntegrityViolationException` → `"JA_INSCRITO"` hardcoded. → `TECH-S5B-03`
- CR-5B-01 follow-up: `PagamentoAprovadoPublisher` (S4) ainda em DEBUG. → `TECH-S5B-04`
- CR-5B-05 typo `Frazo`→`Prazo` (aplicado). CR-5B-06 `realizarCheckin()` CANCELADO→`INGRESSO_JA_UTILIZADO` (ramo inalcançável, defesa).
