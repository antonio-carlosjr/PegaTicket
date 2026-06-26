# Sprint 3 — Especificação de Testes (TDD)

> Autor: Arquiteto. **Testes ANTES do código** — o Tester implementa estes specs vermelhos, o Backend faz passar. Stack: JUnit 5 + AssertJ + Spring Boot Test (back) · Vitest + Testing Library (front). Cobertura: `InscricaoService` ≥ 80%; happy + ≥1 erro no front.
>
> ⚠️ **NOTA CRÍTICA DE INFRA DE TESTE (leia primeiro):** o teste de concorrência de **última vaga** **NÃO** pode rodar em **H2 in-process** e dar garantia real. O H2 não reproduz fielmente o **row lock** do Postgres no `UPDATE ... WHERE vagas > 0` sob concorrência — pode passar (falso verde) ou comportar-se diferente. O gate de concorrência **exige Postgres real**: via **Testcontainers** (preferido — `@Testcontainers` + `PostgreSQLContainer`, isolado no `verify`) **ou** um **smoke de carga via Docker Compose** (subir a stack e martelar `POST /api/tickets/inscricoes` com K clientes). Os demais testes (unitários de service com Mockito, validação, mapeamento) seguem em H2/Mockito normalmente. **Sem o teste em Postgres real, o DoD de concorrência NÃO está cumprido.**

---

## A. event-service — reserva/liberação de vaga (decremento atômico)

### A1. `EventRepository.decrementarVaga` (integração, **Postgres via Testcontainers**)
- ✅ evento PUBLICADO com `vagas=1` → `decrementarVaga` retorna **1** (rowsAffected); `vagas_disponiveis` = 0.
- ✅ evento PUBLICADO com `vagas=0` → retorna **0**; `vagas_disponiveis` permanece 0 (nunca negativo).
- ✅ evento RASCUNHO (status != PUBLICADO) → retorna **0** (não decrementa).
- ✅ evento CANCELADO → retorna **0**.
- ✅ evento inexistente (`id` que não existe) → retorna **0**.

### A2. `EventRepository.incrementarVaga` (integração, Postgres)
- ✅ evento PUBLICADO com `vagas=0, capacidade=10` → retorna **1**; `vagas=1`.
- ✅ evento PUBLICADO com `vagas=10, capacidade=10` (no teto) → retorna **0**; `vagas=10` (não excede a capacidade).
- ✅ evento RASCUNHO → retorna **0** (não incrementa).

### A3. `EventService.reservarVaga` (distingue 409/422/404 no caminho frio)
- ✅ rowsAffected=1 → retorna `ReservaResponse` com vagas decrementadas; **não** faz `findById` adicional (caminho quente).
- ✅ rowsAffected=0 + evento inexistente → lança `NotFoundException` (404 `EVENTO_NAO_ENCONTRADO`).
- ✅ rowsAffected=0 + evento PUBLICADO vagas=0 → `BusinessException("EVENTO_ESGOTADO", 409)`.
- ✅ rowsAffected=0 + evento RASCUNHO → `BusinessException("EVENTO_NAO_PUBLICADO", 422)`.

### A4. 🔴 **CONCORRÊNCIA — ÚLTIMA VAGA (GATE INEGOCIÁVEL — Postgres real)**
Cenário: evento PUBLICADO com `vagas_disponiveis = 1`, **K = 50 threads** chamam `reservarVaga(eventoId)` simultaneamente (barrier/`CountDownLatch` ou `ExecutorService` + `invokeAll`; coletar resultados com algo equivalente a `Promise.allSettled`).
- ✅ **exatamente 1** chamada tem sucesso (rowsAffected=1 / 200).
- ✅ **K-1 (49)** chamadas falham com **409 `EVENTO_ESGOTADO`**.
- ✅ `vagas_disponiveis` final = **0**, **nunca negativo** (assert `>= 0` e `== 0`).
- ✅ rodar **N vezes** (ex.: repetir o cenário 5–10x) para reduzir flakiness — a corrida tem de dar o mesmo resultado sempre.

### A5. CONCORRÊNCIA — capacidade N (Postgres real)
Cenário: evento PUBLICADO `vagas = capacidade = 20`, **K = 100 threads** reservam.
- ✅ exatamente **20** sucessos, **80** × 409.
- ✅ `vagas_disponiveis` final = **0**.

### A6. Autorização interna (`X-Internal-Token`)
- ✅ `POST /internal/events/{id}/reservar-vaga` **sem** `X-Internal-Token` → **403** `ACESSO_INTERNO_NEGADO`.
- ✅ com token **errado** → **403**.
- ✅ com token **correto** → processa (200/409/422 conforme estado).

---

## B. ticket-service — `InscricaoService.inscrever` (mini-saga)

> Unitários com Mockito (`EventClient` e repositórios mockados) para a lógica da saga; integração (H2) para mapeamento/persistência; **um** teste de saga ponta-a-ponta + concorrência em **Postgres real**.

### B1. Caminho-feliz (unit + integração)
- ✅ evento GRATUITO + PUBLICADO + tem vaga + usuário não inscrito → cria `Inscricao(ATIVA)` + `Ingresso(ATIVO, codigoUnico não-nulo)`; retorna **201** com `ingresso.codigoUnico` preenchido.
- ✅ ordem da saga respeitada: `EventClient.validar` → (pré-check exists) → `EventClient.reservarVaga` → tx local. (verificar com `InOrder` do Mockito.)
- ✅ `codigoUnico` é um UUID válido (formato), único.

### B2. Bloqueios de estado/tipo (US-030.4/5, US-031)
- ✅ evento **PAGO** → **422 `EVENTO_PAGO_NAO_SUPORTADO`**; **não** chama `reservarVaga` (nada debitado).
- ✅ evento **não PUBLICADO** (RASCUNHO/CANCELADO) → **422 `EVENTO_NAO_PUBLICADO`**; não reserva.
- ✅ evento **inexistente** → **404 `EVENTO_NAO_ENCONTRADO`**; não reserva.
- ✅ event-service **fora/timeout** na validação → **503 `EVENTO_INDISPONIVEL`**; não reserva.
- ✅ **esgotado** (reservarVaga devolve 409) → **409 `EVENTO_ESGOTADO`**; nenhuma inscrição criada.

### B3. Dupla inscrição (sequencial) — US-031.2
- ✅ usuário já inscrito → pré-check `exists` ou a constraint barra → **409 `JA_INSCRITO`**; **não** cria segunda inscrição; se chegou a reservar, **compensa** (libera a vaga).
- ✅ a segunda tentativa **não** consome vaga (assert: `liberarVaga` chamado, ou `reservarVaga` nem chamado se o pré-check pegou).

### B4. 🔴 **CONCORRÊNCIA — DUPLA INSCRIÇÃO PARALELA (Postgres real)** — US-031.5
Cenário: **mesmo** usuário dispara **2 (ou K) requisições simultâneas** de inscrição no mesmo evento (vaga disponível).
- ✅ **exatamente 1** sucesso (201); a(s) outra(s) → **409 `JA_INSCRITO`**.
- ✅ existe **exatamente 1** linha em `inscricoes` para (usuario, evento) e **1** ingresso.
- ✅ `vagas_disponiveis` decrementado **exatamente 1** (a tentativa perdedora reservou e **compensou**, OU foi barrada antes de reservar — em ambos os casos o saldo final reflete 1 inscrição). **Assert do saldo final é o que importa.**

### B5. 🔴 **COMPENSAÇÃO (passo 3 falha → vaga restaurada)** — risco do PO
Cenário: forçar falha na tx local **após** a reserva (ex.: mock do `inscricaoRepository.save` lança `DataIntegrityViolationException`, ou simular violação da UNIQUE).
- ✅ `EventClient.liberarVaga(eventoId)` **é chamado** (compensação).
- ✅ resultado ao cliente: 409 (se foi UNIQUE/dup) ou 500/503 (outra falha) — nenhuma inscrição persistida.
- ✅ (integração Postgres) após o cenário, `vagas_disponiveis` voltou ao valor anterior (restaurada).
- ✅ se a **compensação também falhar** (mock `liberarVaga` lança) → o erro é **logado** (verificar via `ListAppender`/captura de log) e o cliente recebe erro; o teste documenta que a vaga fica "presa" (conservador, não overbooking).

### B6. Ingresso único por inscrição — US-032.4
- ✅ tentar emitir 2 ingressos para a mesma `inscricao_id` → o segundo falha (`DataIntegrityViolationException` por `UNIQUE(inscricao_id)`). (integração)
- ✅ inscrição + ingresso na **mesma transação**: forçar falha na emissão do ingresso → a inscrição **também** não persiste (rollback atômico). (integração)

### B7. `GET /tickets/me` (meus ingressos) — US-033
- ✅ usuário com 2 ingressos → 200 com 2 itens (`codigoUnico`, `statusIngresso`, `eventoId`).
- ✅ usuário sem ingressos → 200 com `[]`.
- ✅ sem N+1: a query é única (join `ingressos⨝inscricoes`) — verificar contagem de queries (ex.: `@DataJpaTest` + Hibernate statistics, ou inspeção do SQL).
- ✅ só retorna ingressos **do próprio** `X-User-Id` (não vaza de outro usuário).

### B8. `GET /tickets/inscricoes/me` (histórico) — US-033.3
- ✅ paginado, `sort=inscritoEm,desc` (mais recente primeiro).
- ✅ só inscrições do próprio usuário.
- ✅ `size` capado em 100.

### B9. Auth/borda
- ✅ `POST /tickets/inscricoes` sem `X-User-Id` → **401**.
- ✅ body sem `eventoId` → **400** (Bean Validation).
- ✅ `eventoId` não-numérico no body → **400** (não 500).

---

## C. Roteamento / segurança de borda (ADR-T08) — integração de gateway

> Pode ser smoke (stack Docker) ou teste de roteamento do gateway. **Gate de segurança.**
- ✅ `POST /api/internal/events/1/reservar-vaga` **via gateway** → **404** (sem rota `/api/internal/**`; nunca chega ao event-service).
- ✅ `POST /api/events/1/reservar-vaga` via gateway → **404** (endpoint não existe no event-service; internos vivem em `/internal/...`).
- ✅ chamada **direta** ao event-service `POST /internal/events/1/reservar-vaga` sem `X-Internal-Token` → **403** (defesa de profundidade).

---

## D. Frontend (Vitest + Testing Library)

### D1. Inscrever (detalhe do evento)
- ✅ evento GRATUITO+PUBLICADO → botão "Inscrever-se" visível; clicar chama `POST /api/tickets/inscricoes {eventoId}`; sucesso → mostra ingresso com **QR renderizado** (de `codigoUnico`) + toast.
- ✅ evento PAGO → botão "Disponível em breve" (ou desabilitado), **sem** POST.
- ✅ erro **409 `JA_INSCRITO`** → mensagem "Você já está inscrito neste evento".
- ✅ erro **409 `EVENTO_ESGOTADO`** → mensagem "Evento esgotado".
- ✅ erro **422** → mensagem "Evento não disponível para inscrição".

### D2. Meus ingressos
- ✅ lista com ingressos renderiza QR de cada `codigoUnico` + status.
- ✅ estado vazio → mensagem amigável + CTA "explorar eventos".

### D3. Histórico de inscrições
- ✅ lista paginada, mais recente primeiro, com status da inscrição.

---

## Matriz de cobertura → critérios do PO

| Critério PO | Teste |
|---|---|
| US-030: inscreve gratuito → ingresso QR (201) | B1, D1 |
| US-030.4/5: PAGO/não-publicado bloqueados (422) | B2, D1 |
| US-031.2: dupla inscrição → 409 | B3 |
| US-031.3: (N+1)ª → 409 esgotado | A5, B2 |
| US-031.4: **última vaga concorrente → 1 sucesso, K-1×409, vagas=0 nunca negativo** | **A4, B4 (Postgres real)** |
| US-031.5: dupla inscrição paralela → 1 sucesso, 1×409 | **B4** |
| US-031.6: event-service fora → 503, nada debitado | B2 |
| US-032.4: retry não gera 2 ingressos | B6 |
| US-032.5: `codigo_unico` único | B1, B6 |
| US-033: meus ingressos + histórico | B7, B8, D2, D3 |
| ADR-T08: internos não expostos | C |

---

## Cobertura mínima (gate)

- `InscricaoService` (crítico): **≥ 80%** (ideal 90% — é o orquestrador da saga).
- `EventService.reservarVaga/liberarVaga`: caminhos quente + frio (409/422/404) cobertos.
- **Concorrência (A4, A5, B4, B5) em Postgres real — gate inegociável do DoD.** H2 NÃO conta para o gate de concorrência.
- Front: happy + ≥1 erro por tela.
- `./mvnw -B -ntp verify` verde (inclui os testes de concorrência).
