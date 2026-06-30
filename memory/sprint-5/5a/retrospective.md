# Sprint 5 · Trilha 5A — Retrospectiva

**Tema:** Repasse + reembolso por evento cancelado (US-043, US-042 parte 5A). Branch `feat/sprint-5-financeiro`. PR [#20](https://github.com/antonio-carlosjr/PegaTicket/pull/20).

## O que entregou
- Fechou a saga financeira do escrow: repasse (−10%) pós-evento REALIZADO e reembolso em massa por evento cancelado.
- **event-service ganhou mensageria pela 1ª vez** (RabbitConfig + EventoPublisher) — endpoint `encerrar`, reset de vagas.
- Fan-out `evento.cancelado` (payment + ticket), TECH-S4-01 fechado (`evento_id`/`promotor_id` em `pagamentos`).

## O que foi bem
- **Os aprendizados do S4 cortaram o custo do CI de 5 ciclos para 1.** Os agentes já aplicaram RabbitConfig delegado, perfil de teste sem exclude, purge de filas, `@JsonFormat` de dinheiro — tudo de primeira.
- Revisor (opus) confirmou os 2 pontos de maior risco como corretos (refactor `cancelar()` em todos os call sites; fiação AMQP do event-service) — 0 P0/P1.
- Faseamento 5A primeiro funcionou: escopo coeso, financeiro fechado antes da experiência (5B).

## O que doeu (1 ciclo de CI)
- **Recorrência do bug de `TestcontainersBase` do S4:** o ticket ainda usava `@Container static`. Em S4 só 1 classe o estendia (passou); a 5A adicionou a 2ª (`EventoCanceladoListenerIntegrationTest`) → o extension parou o broker entre classes → listener não consumiu → **5× 30s timeout no CI**. Fix: padrão **singleton** (já aplicado ao payment no S4). **Lição:** ao corrigir um padrão de teste, aplicar a TODOS os serviços que o usam, não só onde doeu. → promovido a `coding-standards §Testes`.

## Dívidas / follow-ups (P2/P3 do code review → owner)
- **CR-5A-01** converter JSON sem `JavaTimeModule` explícito em event/payment (funciona por auto-detecção; padronizar).
- **CR-5A-02** `@Profile("!test")` na config/listener novos do ticket (inconsistente com o padrão payment — funciona).
- **CR-5A-03** `Inscricao.cancelarPorEvento()`/`Ingresso.cancelar()` código morto em prod (listener usa queries em massa) — provavelmente usados na 5B.
- **CR-5A-04** índice parcial de reembolso fora do try/catch (poison message teórico em reprocesso manual).
- `pagamentos.evento_id` NULLABLE: repasse/reembolso só cobrem pagamentos pós-V3.

## Regras promovidas (`coding-standards.md`)
- Bloco Testcontainers: **base singleton**, **purge de filas no @BeforeEach**, perfil com broker sem exclude, RabbitConfig delegado ao autoconfigure (consolidando S4 + 5A).

## Métricas
- 14 commits atômicos, sem `Co-Authored-By`. Reactor `mvn verify` + frontend 80/80 verdes; **CI do PR verde em 1 ciclo** (Testcontainers PG+Rabbit); 0 P0/P1 no review.
