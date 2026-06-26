# Sprint 2 — Backend Log (event-service)

> Autor: Backend Engineer. Data: 2026-06-26. Sprint: 2 (US-020..023).

---

## Escopo implementado

- **US-020**: Promotor cria evento → `POST /events` (status RASCUNHO, promotorId = X-User-Id).
- **US-021**: Promotor edita/publica/cancela o próprio evento (máquina de estados + ownership).
- **US-022**: Participante lista eventos PUBLICADOS com filtros + paginação.
- **US-023**: Participante vê detalhe; RASCUNHO alheio → 404.

---

## Mapeamento entidade ⇿ schema (confirmado contra V1 + V2)

| Campo Java          | Coluna              | Tipo SQL         | Anotação chave                             |
|---------------------|---------------------|------------------|--------------------------------------------|
| `id`                | `id`                | BIGSERIAL PK     | `@Id @GeneratedValue(IDENTITY)`            |
| `titulo`            | `titulo`            | VARCHAR(160) NN  | `@Column(nullable=false, length=160)`      |
| `descricao`         | `descricao`         | TEXT             | `@Column(columnDefinition="TEXT")`         |
| `dataInicio`        | `data_inicio`       | TIMESTAMPTZ NN   | `@Column(name="data_inicio", nullable=false)` |
| `dataFim`           | `data_fim`          | TIMESTAMPTZ NN   | `@Column(name="data_fim", nullable=false)` |
| `local`             | `local`             | VARCHAR(200) NN  | `@Column(nullable=false, length=200)`      |
| `tipo`              | `tipo`              | VARCHAR(20) NN   | `@Enumerated(STRING) @Column(nullable=false, length=20)` |
| `status`            | `status`            | VARCHAR(20) NN   | `@Enumerated(STRING) @Column(nullable=false, length=20)` |
| `capacidade`        | `capacidade`        | INTEGER NN       | `@Column(nullable=false)`                  |
| `preco`             | `preco`             | NUMERIC(12,2)    | `@Column(precision=12, scale=2)`           |
| `prazoReembolsoDias`| `prazo_reembolso_dias` | INTEGER       | `@Column(name="prazo_reembolso_dias")`     |
| `promotorId`        | `promotor_id`       | BIGINT NN        | `@Column(name="promotor_id", nullable=false)` |
| `criadoEm`          | `criado_em`         | TIMESTAMPTZ NN   | `@Column(name="criado_em", nullable=false)` |
| `atualizadoEm`      | `atualizado_em`     | TIMESTAMPTZ NN   | `@Column(name="atualizado_em", nullable=false)` |
| `vagasDisponiveis`  | `vagas_disponiveis` | INTEGER (null)   | `@Column(name="vagas_disponiveis")`        |
| `imagemUrl`         | `imagem_url`        | VARCHAR(300)     | `@Column(name="imagem_url", length=300)`   |

**Nota crítica (`descricao TEXT`):** usou `columnDefinition="TEXT"` sem `length` — conforme lição da Sprint 1 (o `ddl-auto: validate` reclamaria de `varchar(255)` contra `TEXT`).

---

## Decisões

### 1. Concorrência — esta sprint
**Estratégia:** nenhum lock implementado (ADR-T07). O campo `vagasDisponiveis` é apenas inicializado no `publicar()` (`= capacidade`). O decremento atômico (`UPDATE ... WHERE vagas_disponiveis > 0`) fica para Sprint 3, quando inscrições criarem contenda real. Nesta sprint não há mutação concorrente sobre o campo.

### 2. Máquina de estados na entidade
Transições (`publicar()`, `cancelar()`, `atualizarDados()`) residem em `Evento`, não no service. Invariante não depende do controller. `BusinessException(msg, 409)` lançada internamente; `GlobalExceptionHandler` traduz para JSON.

### 3. Ownership → 404 (não 403)
Promotor B acessando recurso do promotor A recebe `NotFoundException` (404), idêntico a "inexistente". Não vaza existência (critério US-021.3, US-023.2). Papel errado é 403 no controller, **antes** de tocar o banco (critério US-020.3).

### 4. Query de listagem — `CAST(:q AS string)`
Replicada a técnica do `UsuarioRepository.findComFiltros` (Sprint 1). Sem o CAST, `q=null` em Postgres lança `function lower(bytea) does not exist`. H2 não reproduz; bug só aparece em Postgres real. O mesmo padrão foi aplicado aos parâmetros de data (`CAST(:de AS timestamp)`).

### 5. `hibernate.jdbc.time_zone: UTC` — gap corrigido
O `application.yml` do event-service não tinha esta config (o user-service tem). Adicionado conforme GAP identificado pelo Arquiteto. Sem isso, `TIMESTAMPTZ` "anda" o horário dependendo do fuso default da JVM.

### 6. Sem Spring Security
O `pom.xml` não inclui `spring-boot-starter-security`. Autorização feita via `@RequestHeader` + checagem manual (`requirePromotor`, `requireUserId`). O gateway é o único guardião de JWT.

### 7. `GET /events/meus` — rota literal antes de `/{id}`
Declarado explicitamente antes de `GET /events/{id}` no controller para evitar ambiguidade. Spring MVC prioriza rota literal, mas a ordem explícita documenta a intenção.

### 8. Cap de `size` em 100
`GET /events` limita `size` a `Math.min(size, 100)` no controller — defesa contra payload gigante.

### 9. `@AssertTrue` — erros de validação cross-field
`isPrecoCoerente()` e `isPeriodoValido()` nos records de request lançam `ConstraintViolationException` que o `GlobalExceptionHandler` captura via `MethodArgumentNotValidException`. O handler inclui `getGlobalErrors()` para pegar esses erros de nível de classe além dos de campo.

---

## Migration V2 — `V2__eventos_aux.sql`
- `vagas_disponiveis INTEGER` nullable + `CHECK >= 0` (prepara Sprint 3).
- `imagem_url VARCHAR(300)` opcional.
- `idx_eventos_publicados ON eventos(status) WHERE status = 'PUBLICADO'` — índice parcial para a query mais quente.

---

## Resultado de testes

```
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Distribuição:
- `EventoTest` (unitário da entidade): 10 testes
- `EventServiceTest` (unitário com mocks): 15 testes
- `EventControllerIntegrationTest` (MockMvc + H2): 28 testes
- `EventServiceApplicationTests` (context load): 1 teste

Cobertura do `EventService`: estimada > 90% (todos os branches de ownership, transições e detalhe cobertos).

---

## Nota sobre smoke em Postgres

Os testes H2 cobrem contrato e lógica. A query `CAST(:q AS string)` com filtro null **não reproduz o bug em H2** — validação em Postgres real é obrigatória antes do merge para produção (ver `tests-spec.md` §nota crítica). Smoke manual recomendado: subir via `docker compose --profile backend up` e rodar `GET /api/events` (sem `q`) contra Postgres.

---

## Arquivos criados/alterados

### Novos
- `src/main/resources/db/migration/V2__eventos_aux.sql`
- `src/main/java/com/ticketeira/event/domain/TipoEvento.java`
- `src/main/java/com/ticketeira/event/domain/StatusEvento.java`
- `src/main/java/com/ticketeira/event/domain/Evento.java`
- `src/main/java/com/ticketeira/event/repository/EventRepository.java`
- `src/main/java/com/ticketeira/event/service/EventService.java`
- `src/main/java/com/ticketeira/event/dto/EventoCreateRequest.java`
- `src/main/java/com/ticketeira/event/dto/EventoUpdateRequest.java`
- `src/main/java/com/ticketeira/event/dto/EventoResponse.java`
- `src/main/java/com/ticketeira/event/dto/EventoResumoResponse.java`
- `src/main/java/com/ticketeira/event/exception/GlobalExceptionHandler.java`
- `src/test/java/com/ticketeira/event/domain/EventoTest.java`
- `src/test/java/com/ticketeira/event/service/EventServiceTest.java`
- `src/test/java/com/ticketeira/event/controller/EventControllerIntegrationTest.java`

### Alterados
- `src/main/java/com/ticketeira/event/controller/EventController.java` — stub substituído
- `src/main/resources/application.yml` — adicionado `hibernate.jdbc.time_zone: UTC`
