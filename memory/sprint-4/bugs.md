# Sprint 4 — Bugs

> Loop de bug da Fase 6. Owner corrige com teste de regressão → DevOps `fix:` → re-valida.

## P0 / P1 (bloqueiam aceite)
_Nenhum encontrado na suíte executável localmente (unit + regressão GRATUITO + frontend)._

## Aguardando validação no CI (Testcontainers — não roda local por limitação de Docker/JVM)
Não são bugs conhecidos; são invariantes **ainda não exercitados** localmente. Se algum falhar no CI (`/validar-sprint 4`), vira P0 e entra no loop.
- A2 concorrência última vaga PAGO · A3.b idempotência de `pagamento.aprovado` · B1.b idempotência de `pedido.criado` · B2.* confirmação/escrow · A4 TTL · B5 auth admin.

## Follow-ups técnicos (não bloqueiam o S4 — registrar em backlog/decisions)
- **TECH-S4-01** — `pagamentos` não persiste `evento_id`/`promotor_id`; `PagamentoAprovadoEvent.eventoId` sai `null` (o consumidor do ticket age por `inscricaoId`, então S4 ok). **S5 (repasse) precisa** dessas colunas → migration `payment V3` + persistir do `pedido.criado`.
- **TECH-S4-02** — Frontend: tela **admin de pagamentos** (`GET /api/payments`, escrow auditável — US-040.3) não construída (endpoint backend existe; auditável via API/Swagger). Avaliar UI mínima.
- **TECH-S4-03** — `IngressoPendenteCard` (Meus ingressos) não mostra o prazo restante real (falta `inscritoEm` no `MeuIngressoResponse`); o checkout já avisa "Pague em até 30 min".
