# Sprint 5 · Trilha 5B — Relatório de Regressão (Passo 3 do validar-sprint)

> Rodado após o code review (opus) e a correção CR-5B-01/05. Confirma que a 5B não quebrou nada de S1–5A e que a suíte local está verde. Integração concorrente (Testcontainers) roda no **CI** (Docker inacessível pela JVM no Windows local — padrão S4/5A).

## CI local — VERDE
- `./mvnw -B -ntp verify` → **BUILD SUCCESS** (reactor inteiro, 7 módulos).
- `cd frontend && npm ci && npm run build && npm run test:run` → build OK + **97/97**, `tsc` limpo.

## Totais por módulo (surefire)
| Módulo | Tests run | Verdes | Skipped (Testcontainers) | Falhas |
|---|---|---|---|---|
| api-gateway | 1 | 1 | 0 | 0 |
| user-service | 28 | 28 | 0 | 0 |
| event-service | 121 | 110 | 11 | 0 |
| ticket-service | 104 | 82 | 22 | 0 |
| payment-service | 53 | 21 | 32 | 0 |
| **backend total** | **307** | **242** | **65** | **0** |
| frontend | 97 | 97 | — | 0 |

> ticket subiu de 101 → **104** (novo `InscricaoCanceladaPublisherTest`, 3 casos, do code review CR-5B-01).

## Regressão S1–5A — intacta
- **user-service** (S1/S2 auth/roles/promotor): 28/28 verde.
- **event-service** (S2/S3 criação/publicação + 5A encerrar/cancelar): 110 verdes; a nova aridade de `EventoInternoResponse`/`EventResumo` (5B) não quebrou fixtures — `EventServiceTest`/`EventoPublisherAfterCommitTest` verdes.
- **ticket-service** (S3 inscrição + S4 saga pagamento + 5A cancelamento por evento): 82 verdes; 9 fixtures S4 ajustadas para a aridade do `EventResumo`, saga `pedido.criado`/`pagamento.aprovado` intacta.
- **payment-service** (S4 escrow/saga + 5A repasse/reembolso massa): 21 verdes; reembolso individual (5B) reusa `Reembolso.criar`/`reembolsar()` sem tocar os caminhos da 5A.

## Concorrência — confirmação pendente de CI
Os invariantes concorrentes têm teste Testcontainers escrito (contam como "passa" só no CI):
`CheckinConcorrenciaTest` · `CancelamentoConcorrenciaTest` · `InscricaoCanceladaListenerIntegrationTest` (reembolso individual idempotente) · `ReembolsoIndividualVsMassaConcorrenciaTest` · `InscricaoCanceladaAfterCommitTest`.

## Nota de honestidade (code review CR-5B-02)
A corrida **check-in vs. cancelamento do mesmo ingresso** (atores distintos) é **last-writer-wins** no `ingressos.status` — não serializada a 409. Não quebra invariante crítica (duplo check-in guardado por `UNIQUE(ingresso_id)`; reembolso único depende de `pagamentos.status` sob lock). `architecture.md` alinhada ao comportamento real; hardening previsto na 5C.

## Veredicto
**Regressão VERDE. Sem P0/P1 remanescentes.** Pronto para abrir o PR (Passo 4). A confirmação dos invariantes concorrentes vem do CI do GitHub Actions.
