# Sprint 5 · Trilha 5A — Bugs

## P0 / P1 (bloqueiam aceite)
_Nenhum na suíte executável localmente (unit + regressão event/payment/ticket + frontend 80/80)._

## Aguardando validação no CI (Testcontainers — não roda local)
Invariantes ainda não exercitados aqui; se algum falhar no `/validar-sprint 5a` vira P0 e entra no loop:
- A3/A4 publicação afterCommit · B2 repasse idempotente · C1 reembolso em massa idempotente · C2 corrida repasse-vs-reembolso · D1/D2 cancelamento por evento no ticket.
- **Atenção especial** (lição S4): a 1ª mensageria do event-service e o fan-out `evento.cancelado`/`evento.cancelado.ticket` são os pontos mais prováveis de exigir ajuste no CI.

## Notas / follow-ups (não bloqueiam)
- **Refactor reportado:** o agente alinhou a assinatura `EventService.cancelar(eventoId, promotorId)` (antes `(promotorId, eventoId)`) para o novo teste; atualizou os call sites (controller + testes S2/S3). Comportamento externo idêntico — revisar no code review do validar-sprint.
- `pagamentos.evento_id` é NULLABLE: repasse/reembolso só cobrem pagamentos pós-V3 (os de teste/demo são criados pós-V3). Documentado no ADR-T13.
