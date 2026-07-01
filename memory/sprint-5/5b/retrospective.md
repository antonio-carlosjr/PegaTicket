# Sprint 5 · Trilha 5B — Retrospectiva

**Tema:** Experiência do participante — check-in QR (US-034), cancelamento + reembolso individual (US-035), avaliação (US-024) + reputação (US-025). Branch `feat/sprint-5-experiencia`. PR [#21](https://github.com/antonio-carlosjr/PegaTicket/pull/21).

## O que entregou
- **Marina valida o QR na porta** (check-in, ownership do evento, `UNIQUE(ingresso_id)` contra duplo scan); **Bruno cancela e recebe reembolso individual** (`CANCELAMENTO_PARTICIPANTE`, política de prazo 422); **avaliação 1-5 + reputação** agregada ao vivo.
- **1ª mensageria outbound síncrona do event-service** (`TicketClient` para o canal interno do ticket, fail-closed 503), fechando a elegibilidade de avaliação sem novo evento AMQP.
- Nova fila `inscricao.cancelada` (ticket→payment) reusando o mecanismo de reembolso da 5A. Sem migration (tabelas já existentes).

## O que foi bem
- **CI verde em 1 ciclo** (Testcontainers PG+Rabbit, `build+test Java 21` em 3m18s). O pré-carregamento das lições S4/5A nos prompts dos agentes cortou o custo de CI de 5 (S4) → 1 (5A) → **0 ciclos de retrabalho no CI** (5B: os bugs apareceram e foram mortos na verificação **local**, antes do push).
- **4 defeitos caçados na Fase 5 local** (antes do commit da implementação): publishers com RabbitTemplate obrigatório derrubando 38 contextos H2; `CancelamentoControllerTest` `@Transactional` vs. service REQUIRES_NEW (seed não-commitado invisível → 409); handler faltando para `MissingServletRequestParameter` (500→400). Nenhum chegou ao CI.
- **Revisor (opus) confirmou (não presumiu)** que os padrões S4/5A estão de fato aplicados aqui — idempotência, afterCommit real, lock pessimista na corrida individual-vs-massa, token constante-no-tempo, `TicketClient` sem SSRF/vazamento. 0 P0/P1.

## O que doeu (menor)
- **Doc↔código descolou de novo (2º caso após S4):** `architecture.md` afirmou que check-in vs. cancelamento do mesmo ingresso era serializado por `WHERE status='ATIVO'`+row lock, mas o código faz dirty-check (last-writer-wins). Não quebra invariante crítica (duplo check-in guardado por `UNIQUE`; dinheiro por `pagamentos.status`), mas a doc mentia. **Lição:** afirmação de garantia de concorrência na arquitetura tem que casar 1:1 com a query implementada — o Revisor passou a cruzar cada linha da tabela §Estratégias com o método. → `coding-standards §Concorrência`.
- Padrão "publisher opcional" reapareceu 3× logando perda em DEBUG — invisível em prod. Promovido a WARN. → `coding-standards §Mensageria`.

## Regras promovidas (`coding-standards.md`)
- **§Mensageria:** publisher com `RabbitTemplate` opcional loga a perda de evento em **WARN** (com chave de negócio), nunca DEBUG.
- **§Concorrência:** mutação de `status` disputada por 2 caminhos usa `UPDATE...WHERE status=?` (rowsAffected), não dirty-check; doc de arquitetura casa 1:1 com a query.

## Dívidas / follow-ups (P2/P3 → backlog TECH-S5B, 5C)
- `TECH-S5B-01` corrida check-in/cancel → UPDATE condicional. · `TECH-S5B-02` construtor compat `PagamentoService`. · `TECH-S5B-03` handler `JA_INSCRITO` hardcoded. · `TECH-S5B-04` `PagamentoAprovadoPublisher` DEBUG→WARN.
- `PA-04` (`INTERNAL_TOKEN` em produção / US-063) segue como dívida documentada para a 5C.

## Métricas
- 12 commits atômicos (4 feat + test-first vermelho + docs), sem `Co-Authored-By`. Backend 307 testes / frontend 97/97 verdes local; **CI do PR verde em 1 ciclo**; 0 P0/P1 no review (1 P1 corrigido com teste). US-024/025/034/035 → DONE.
