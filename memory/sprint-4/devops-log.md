# Sprint 4 — DevOps log

Branch: `feat/sprint-4-pagamento-escrow` (base: `main` @ ef04e94)
Tema: Pagamento + escrow + saga de inscrição paga (US-040, US-041, US-060).

## Fase 0 — setup
- [x] Branch criada a partir de `main` atualizado.
- [x] `memory/sprint-4/` já existe (spec + po-planning do planejamento).
- [x] `devops-log.md` aberto.
- Working tree: `docs/poster/` e `frontend/.gitignore` permanecem **não rastreados** (fora do escopo do sprint; não commitar aqui).

## Convenções
- Conventional Commits, **sem `Co-Authored-By`** (ADR-P03).
- `git add` por caminho (nunca `-A`), para não capturar o pôster nem `.gitignore` soltos.
- Commits atômicos por unidade coesa, padronizados pelo DevOps.

## Commits do sprint
Fluxo: `docs(planejamento)` → `docs(arquitetura)` → `docs(PO valida)` → `test(suite vermelha)` →
`feat(event/payment/ticket/frontend)` → `docs(test-report/bugs)` → `docs(aceite)` →
`fix(frontend tsc)` → `fix(ticket/payment review CR-S4-01..04)` → `docs(code-review)` →
`docs(regression/pr-body)` → **fixes de CI** (`RabbitConfig autoconfigure`, `test-postgres sem exclude`,
`escrow scale + TestcontainersBase singleton`, `PagamentoResponse string`, `purge de filas`).

## Validação (validar-sprint)
- CI local: `./mvnw verify` SUCCESS + frontend build/test 74/74.
- Revisor (opus): 1 P0 + 3 P1 corrigidos (`code-review.md`). 0 P0/P1 em aberto.
- **CI do PR (GitHub Actions, com Docker): VERDE** — suíte Testcontainers (Postgres + RabbitMQ) executada:
  concorrência última-vaga PAGO (A2), idempotência de reentrega (A3.b/B1.b), escrow, listener, confirmar idempotente.
- **PR aberto:** https://github.com/antonio-carlosjr/PegaTicket/pull/19 — **NÃO mergeado** (decisão humana).

## Bugs que só o CI pegou (Testcontainers não roda no Windows local)
1. `RabbitConfig` exigia `ConnectionFactory`/usava `@ConditionalOnBean` frágil → contexto não subia. Fix: delegar ao autoconfigure.
2. `application-test-postgres.yml` (ticket) excluía `RabbitAutoConfiguration` → teste de listener sem `RabbitTemplate`. Fix: remover exclude (Postgres-only excluem via `@DynamicPropertySource`).
3. `TestcontainersBase` (payment) com `@Container static` compartilhado por 3 classes → container parado entre classes (HikariPool total=0). Fix: padrão singleton.
4. `valorBruto` sem escala 2 + JSON number perdia o zero. Fix: `setScale(2)` + `@JsonFormat(STRING)`.
5. Filas não purgadas entre testes (contexto compartilhado) → `confirmar_2x` recebia sobra/null. Fix: `purgeQueue` no `@BeforeEach`.
