# Sprint 2 — Retrospectiva

## O que deu certo
- Pipeline SDD rodou com **todos os gates**; a lição da Sprint 1 (**H2 ≠ Postgres**) foi aplicada **no design** (mapeamento `@Entity`⇿schema + query `CAST(:q AS string)`) → o event-service subiu **de primeira** no Postgres, **sem** os 4 bugs de runtime que assolaram a Sprint 1.
- **Smoke em Postgres real** como gate de aceite (não só `mvn verify`/H2) — desta vez não achou nada porque o design preveniu.
- **Revisor (opus)** pegou **1 P1 real** (filtro de data → 500) que o smoke não tocou → valor claro do code review adversarial.
- Commits atômicos no padrão `tipo(US-id)` mantidos do início ao fim.

## Desafios / o que pegou
- **Datas `datetime-local` sem offset:** a wizard de criação convertia, mas a **lista** não → **500** no filtro de data (US-022.2). Recorrência do padrão "front manda data que o back rejeita".
- **Param de query malformado → 500:** faltava handler de type-mismatch (mesma classe do "500 em vez de erro tipado" da Sprint 1).

## Regras promovidas (ver `memory/code-review/sprint-2.md` → `coding-standards.md`)
1. **Front:** TODA data de `datetime-local` → ISO **com offset** antes de enviar (formulários **E** filtros).
2. **Backend:** `GlobalExceptionHandler` trata `MethodArgumentTypeMismatchException` → **400**.

## Dívida assumida
- Transições de estado são read-modify-write **sem `@Version`** → sem corrupção nesta sprint; a **concorrência pesada** (reserva atômica de vaga) é da **Sprint 3** (ADR-T07).

## Para a Sprint 3 (Inscrição & Ingresso QR)
- É a sprint de **concorrência pesada** (abre-vendas): decremento atômico (`UPDATE eventos SET vagas_disponiveis = vagas_disponiveis - 1 WHERE id=? AND vagas_disponiveis > 0`), `UNIQUE(usuario_id, evento_id)`, **teste de última vaga concorrente**.
- Avaliar **testcontainers** (Postgres real nos testes de integração) pra fechar de vez o gap H2 — recomendação recorrente.
