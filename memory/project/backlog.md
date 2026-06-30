# Backlog — Ticketeira

> Mantido pelo PO. Histórias movem BACKLOG → SPRINT-<n> → DONE. IDs são estáveis.
> Roadmap-fonte: [`architectural-plan.md`](architectural-plan.md) §8.

## Legenda
`BACKLOG` · `SPRINT-<n>` (em andamento) · `DONE` · `BLOCKED`

---

## Épico A — Eventos (event-service) · RF02, RF08
| ID | História | Estado |
|---|---|---|
| US-020 | Como promotor verificado, quero criar um evento (gratuito ou pago) para abrir inscrições | DONE |
| US-021 | Como promotor, quero editar/publicar/cancelar meu evento para gerir a oferta | DONE |
| US-022 | Como participante, quero listar/buscar eventos publicados para escolher onde ir | DONE |
| US-023 | Como participante, quero ver o detalhe de um evento (vagas, preço, data) | DONE |
| US-024 | Como participante, quero avaliar um evento que participei (nota 1-5) | SPRINT-5 |
| US-025 | Como promotor, quero ver a reputação (média de avaliações) do meu evento | SPRINT-5 |

## Épico B — Inscrições & Ingressos (ticket-service) · RF03, RF04, RF09, RF10
| ID | História | Estado |
|---|---|---|
| US-030 | Como participante, quero me inscrever num evento gratuito e receber meu ingresso | DONE |
| US-031 | Como participante, quero me inscrever num evento (com controle de capacidade e sem dupla inscrição) | DONE |
| US-032 | Como participante, quero receber um ingresso único com QR após confirmação | DONE |
| US-033 | Como participante, quero ver "meus ingressos" e histórico de inscrições | DONE |
| US-034 | Como promotor, quero validar o ingresso (check-in por QR) na porta do evento | SPRINT-5 |
| US-035 | Como participante, quero cancelar minha inscrição conforme a política | SPRINT-5 |

**Follow-ups técnicos da Sprint 3 (code-review, não bloqueiam):**
- `TECH-S3-01` — `EventService.reservar/liberarVaga`: trocar `findById` do hot path por `UPDATE ... RETURNING`. *(CR-S3-02/03)*
- `TECH-S3-02` — `MeusIngressos`: dedupe por `eventoId` antes do fan-out + endpoint batch quando houver 3ª ocorrência. *(CR-S3-07)*
- `TECH-S3-03` — `EventoDetalhe`: refetch de `vagasDisponiveis` pós-inscrição; retry sem `window.location.reload()`. *(CR-S3-08/10)*
- `TECH-S3-04` — Prod (Railway): `INTERNAL_TOKEN` sobrescreve o default `dev-internal-secret`.

## Épico C — Pagamentos & Escrow (payment-service) · RF05, RF06
| ID | História | Estado |
|---|---|---|
| US-040 | Como participante, quero pagar um evento pago (gateway simulado) com retenção em escrow | DONE |
| US-041 | Como sistema, quero emitir o ingresso só após `pagamento.aprovado` (saga de inscrição paga) | DONE |
| US-042 | Como participante, quero ser reembolsado se o evento for cancelado | SPRINT-5 |
| US-043 | Como promotor, quero receber o repasse (menos taxa de 10%) após o evento finalizado | SPRINT-5 |

## Épico D — Identidade (user-service) · dívidas conhecidas
| ID | História | Estado |
|---|---|---|
| US-050 | Como admin, quero aprovar/rejeitar promotores pendentes (tela + endpoint protegido) | DONE |
| US-051 | Como sistema, quero que o papel trafegue no token/header para autorização real (fecha dívida ADR) | DONE |
| US-052 | Como promotor, quero me cadastrar com perfil completo (CPF, telefone, e-mail de contato, endereço, redes sociais) para ser avaliado pelo admin | DONE |
| US-053 | Como admin, quero ativar/inativar usuários para controlar o acesso à plataforma | DONE |
| US-054 | Como promotor avaliado, quero receber e-mail de aprovação/rejeição (com motivo) e, se rejeitado, seguir usando como participante e poder reenviar a solicitação | DONE |

## Épico E — Plataforma · RNF09
| ID | História | Estado |
|---|---|---|
| US-060 | Como time, quero consumidores RabbitMQ idempotentes (`processed_events`) ligados | DONE |
| US-061 | Como time, quero testes de carga no abre-vendas (concorrência de inscrição) | SPRINT-5 |
| US-062 | Como time, quero observabilidade básica (health/readiness, métricas, logs estruturados) | BACKLOG |
| US-063 | Como time, quero fechar dívidas pré-banca (whitelist do gateway, seed admin env-driven, follow-ups TECH-S3) | BACKLOG |

> O PO seleciona um subconjunto coerente por sprint (~2 semanas), priorizando o caminho feliz ponta-a-ponta antes de bordas.
