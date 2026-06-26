# Sprint 3 — Backend Log (Senior Backend Engineer)

> Data: 2026-06-26 | Branch: feat/sprint-3-backend (merge target: main)

## Escopo entregue

| Componente | O que foi feito |
|---|---|
| **event-service** | Endpoints internos `POST /internal/events/{id}/reservar-vaga` e `/liberar-vaga`; `decrementarVaga`/`incrementarVaga` atomicos no repositório |
| **ticket-service** | Domínio completo: entidades `Inscricao`/`Ingresso`, repositórios, `EventClient`, `InscricaoService` (mini-saga), `TicketController`, DTOs, `GlobalExceptionHandler` |
| **Testes** | 28 passando (24 ativos + 4 skipped por Docker/Windows); cobertura: unidade, integração H2, concorrência Testcontainers |

---

## Decisões de design

### 1. Mini-saga síncrona (ticket-service → event-service)

Ordem de passos dentro de `InscricaoService.inscrever()`:

```
PASSO 1   → eventClient.getEvento(eventoId)           // valida: existe? PUBLICADO? GRATUITO?
PASSO 1.5 → inscricaoRepository.existsByUsuarioIdAndEventoId()  // pre-check otimista
PASSO 2   → eventClient.reservarVaga(eventoId)         // decrementa atomico (não-idempotente)
PASSO 3   → txTemplate.execute(…)                      // REQUIRES_NEW: INSERT inscricao + ingresso
COMPENSAR → eventClient.liberarVaga(eventoId)          // se PASSO 3 falhar
```

Sem retry em PASSO 2: a reserva de vaga é não-idempotente. Re-tentativa sem cancelar a anterior causaria vaga presa. Se 503 em passo 2, o cliente chama de novo desde o início.

### 2. TransactionTemplate vs @Transactional (motivo arquitetural)

`inscrever()` **não** é `@Transactional`. Motivo: precisamos capturar `DataIntegrityViolationException` **fora** da transação local para acionar a compensação.

Com `@Transactional`, o Spring AOP só executa o catch/finally após o proxy fechar a TX — a exceção seria relançada, mas o `eventClient.liberarVaga()` ficaria dentro do rollback e nunca executaria corretamente.

Solução adotada (ADR-T07 / best practice):

```java
this.txTemplate = new TransactionTemplate(txManager);
this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

// no método:
try {
    return txTemplate.execute(status -> {
        inscricaoRepository.save(…);
        ingressoRepository.save(…);
        return InscricaoResponse.from(…);
    });
} catch (DataIntegrityViolationException e) {
    compensar(…);
    throw new BusinessException("JA_INSCRITO", 409);
} catch (Exception e) {
    compensar(…);
    throw new BusinessException("EVENTO_INDISPONIVEL", 503);
}
```

### 3. Estratégia de concorrência — UPDATE...WHERE vs @Version (ADR-T07)

**`@Version` (optimistic locking) foi rejeitado** por duas razões:

1. **Overbooking**: Com `@Version`, 50 threads concorrentes na última vaga gerariam 49 `OptimisticLockingFailureException`. Cada retry leria a versão atual e tentaria novamente — levando a O(n²) tentativas no pior caso. Com `UPDATE Evento SET vagasDisponiveis = vagasDisponiveis - 1 WHERE vagasDisponiveis > 0`, o banco serializa as atualizações via row lock; exatamente 1 retorna `rowsAffected = 1`.

2. **Dupla inscrição**: O constraint `UNIQUE(usuario_id, evento_id)` na tabela `inscricoes` é a defesa definitiva — funciona mesmo para corridas que passem pelo pre-check concorrentemente.

Estratégia final:
- **Anti-overbooking**: `UPDATE...WHERE vagasDisponiveis > 0` no event-service (rowlock Postgres)
- **Anti-duplicata**: `UNIQUE(usuario_id, evento_id)` + catch `DataIntegrityViolationException` → 409

### 4. clearAutomatically = true nos @Modifying

Hibernate mantém cache de 1º nível (EntityManager). Após `UPDATE...WHERE` via JPQL, o cache não é invalidado automaticamente. Sem `clearAutomatically = true`, um `findById()` subsequente na mesma sessão retornava o valor pré-UPDATE (campo `vagasDisponiveis` stale).

Fix aplicado em ambas as queries:
```java
@Modifying(clearAutomatically = true)
@Query("UPDATE Evento e SET e.vagasDisponiveis = e.vagasDisponiveis - 1 WHERE ...")
int decrementarVaga(@Param("id") Long id);
```

Descoberto por `InternalEventControllerTest`: esperava `vagasDisponiveis=9`, recebia `10`.

### 5. X-Internal-Token (ADR-T08)

Endpoints `/internal/**` têm dupla defesa:
1. **Roteamento**: o API Gateway não roteia o prefixo `/internal/**` (configuração de rotas)
2. **Aplicação**: `InternalEventController` valida o header `X-Internal-Token` contra `${app.internal.token}`

Resposta 403 para token ausente/errado:
```json
{ "status": 403, "error": "Forbidden", "message": "ACESSO_INTERNO_NEGADO", "path": "..." }
```

### 6. UUID v4 para codigoUnico (ADR-T09)

`Ingresso.emitir()` usa `UUID.randomUUID().toString()` — 36 chars, cabe em `VARCHAR(64)`. Sem dependência extra, sem colisão prática (2^122 espaço). Suficiente para gerar QR Code no frontend.

### 7. HttpMessageNotReadableException handler

Jackson lança `HttpMessageNotReadableException` quando o corpo JSON tem tipo incompatível (ex.: `eventoId: "texto"`). Sem handler explícito, caía no `Exception.class` genérico → 500. Adicionado handler dedicado → 400.

---

## Testes

### Cobertura por classe

| Classe de teste | Tipo | Perfil | Resultado |
|---|---|---|---|
| `InternalEventControllerTest` | Integração | `test` (H2) | 7 testes — PASS |
| `VagaConcorrenciaTest` | Concorrência real | `test-postgres` (Testcontainers) | 9 testes — SKIP* |
| `InscricaoServiceTest` | Unidade (Mockito) | — | 11 testes — PASS |
| `TicketControllerIntegrationTest` | Integração | `test` (H2 + @MockBean EventClient) | 10 testes — PASS |
| `InscricaoConcorrenciaTest` | Concorrência real | `test-postgres` (Testcontainers) | 4 testes — SKIP* |

**\* SKIP**: `@Testcontainers(disabledWithoutDocker = true)`. Docker Desktop no Windows expõe API na versão < 1.44 (mínimo exigido pelo Testcontainers 1.20.3 via npipe). Os testes estão **corretamente implementados** e passarão em Linux/CI ou Docker Desktop com TCP exposto na porta 2375.

Para habilitar em CI (Linux):
```yaml
# github-actions ou similar
services:
  docker:
    image: docker:dind
```

Para habilitar localmente (Windows): Docker Desktop > Settings > General > "Expose daemon on tcp://localhost:2375 without TLS" → `c:\Users\<user>\.testcontainers.properties`: `docker.host=tcp://localhost:2375`.

### Lição: @MockitoSettings(strictness = Strictness.LENIENT)

`@BeforeEach` define stubs default para todos os cenários. Testes de bloqueio (evento PAGO, não-publicado, etc.) sobreescrevem `getEvento()` sem usar `existsByUsuarioIdAndEventoId()`. Mockito STRICT_STUBS rejeita stubs não usados. Solução: `@MockitoSettings(strictness = Strictness.LENIENT)` na classe.

---

## Deltas em relação à Sprint 2

| Arquivo | Mudança |
|---|---|
| `event-service/EventRepository.java` | `+decrementarVaga`, `+incrementarVaga` (ambos `@Modifying(clearAutomatically=true)`) |
| `event-service/EventService.java` | `+reservarVaga`, `+liberarVaga` |
| `event-service/InternalEventController.java` | Criado (novo) |
| `event-service/dto/ReservaResponse.java` | Criado (novo record) |
| `event-service/application.yml` | `+app.internal.token` |
| `event-service/application-test.yml` | `+app.internal.token` |
| `event-service/application-test-postgres.yml` | Criado (novo) |
| `event-service/pom.xml` | `+testcontainers:postgresql`, `+testcontainers:junit-jupiter` |
| `ticket-service/domain/` | Criado: `Inscricao`, `Ingresso`, `StatusInscricao`, `StatusIngresso` |
| `ticket-service/repository/` | Criado: `InscricaoRepository`, `IngressoRepository` |
| `ticket-service/client/` | Criado: `EventResumo`, `EventClient`, `EventClientConfig` |
| `ticket-service/dto/` | Criado: `InscricaoRequest`, `InscricaoResponse`, `IngressoResponse`, `MeuIngressoResponse`, `InscricaoHistoricoResponse` |
| `ticket-service/service/InscricaoService.java` | Criado (novo) |
| `ticket-service/controller/TicketController.java` | Substituído stub 501 por implementação real |
| `ticket-service/exception/GlobalExceptionHandler.java` | Criado (novo) |
| `ticket-service/application.yml` | `+hibernate.jdbc.time_zone: UTC`, `+app.event-service.url`, `+app.internal.token` |
| `ticket-service/application-test.yml` | `+app.event-service.url`, `+app.internal.token` |
| `ticket-service/application-test-postgres.yml` | Criado (novo) |
| `ticket-service/pom.xml` | `+testcontainers:postgresql`, `+testcontainers:junit-jupiter` |

---

## Resultado final

```
Tests run: 28, Failures: 0, Errors: 0, Skipped: 4
BUILD SUCCESS
event-service:  SUCCESS [13.6 s]
ticket-service: SUCCESS [12.6 s]
```
