# Sprint 2 — Arquitetura Técnica (Eventos)

> Autor: Arquiteto. Inputs: `00-sprint-spec.md`, `po-planning.md`, `architectural-plan.md` (§6 event_db, §7), `coding-standards.md`, `V1__init.sql`, `docs/api/event-service.yaml`. Gabarito de padrão: `user-service` (Sprint 1).
> Contratos detalhados → [`api-contracts.md`](api-contracts.md) · Migration + mapeamento → [`data-model.md`](data-model.md) · Specs de teste → [`tests-spec.md`](tests-spec.md).

---

## Histórias cobertas

- **US-020** — Promotor verificado cria evento (gratuito/pago) → status `RASCUNHO`, `promotor_id = X-User-Id`.
- **US-021** — Promotor edita / publica / cancela o **próprio** evento (máquina de estados + ownership).
- **US-022** — Participante lista/busca eventos **PUBLICADOS** (filtros `q`/`tipo`/`de`/`ate` + paginação).
- **US-023** — Participante vê detalhe de um evento publicado (RASCUNHO alheio → 404).

**Fora de escopo (intencional):** inscrição/ingresso (Sprint 3), pagamento (Sprint 4), avaliações/reputação (Sprint 5), check-in, upload de imagem (só URL), busca geo/avançada, consumidores RabbitMQ.

---

## Serviço(s) afetado(s)

- **`services/event-service`** — deixa de ser stub (501/lista vazia) e vira serviço real: `@Entity Evento`, `EventRepository`, `EventService`, `EventController`, DTOs (records), migration **V2**. Reusa `common-lib` (`BusinessException`, `NotFoundException`, `ErrorResponse`) e replica os padrões do user-service.
- **`api-gateway`** — rotas `/api/events/**` já existem; o gateway já injeta `X-User-Papel` (corrigido no fim do Sprint 1). **Nenhuma mudança de código** prevista; apenas confirmar que `X-User-Id`/`X-User-Papel` chegam ao event-service (smoke).
- **`frontend`** — telas de promotor (Meus eventos + wizard criar/editar) e participante (lista + detalhe). Detalhe no handoff do Frontend (este doc cobre o backend e os contratos).

---

## Modelo de dados (delta) → detalhe em [`data-model.md`](data-model.md)

### Entidade nova: `Evento` (tabela `eventos`, já criada em V1)

A `@Entity Evento` **mapeia 1:1** a tabela `eventos` do `V1__init.sql` + as 2 colunas novas do `V2`. **Não** mapearemos `avaliacoes` nesta sprint (Sprint 5).

### Enums (`@Enumerated(STRING)`, espelham os `CHECK` do schema)

```java
public enum TipoEvento { GRATUITO, PAGO }
public enum StatusEvento { RASCUNHO, PUBLICADO, REALIZADO, CANCELADO }
```

### Tabela de mapeamento coluna → campo (conferida contra V1 + V2)

> **Lição da Sprint 1 (bug `uf CHAR(2)` ≠ `varchar`):** o `ddl-auto: validate` compara tipo **e length**. Cada linha abaixo foi conferida contra o DDL real. Tipos/lengths divergentes quebram o boot no Postgres (o CI com H2 não pega).

| Coluna (SQL)            | Tipo SQL            | Campo Java (`Evento`)            | Tipo Java        | Anotação JPA |
|-------------------------|---------------------|---------------------------------|------------------|--------------|
| `id`                    | `BIGSERIAL` PK      | `id`                            | `Long`           | `@Id @GeneratedValue(IDENTITY)` |
| `titulo`                | `VARCHAR(160)` NN   | `titulo`                        | `String`         | `@Column(nullable=false, length=160)` |
| `descricao`             | `TEXT` (nullable)   | `descricao`                     | `String`         | `@Column(columnDefinition="TEXT")` |
| `data_inicio`           | `TIMESTAMPTZ` NN    | `dataInicio`                    | `OffsetDateTime` | `@Column(name="data_inicio", nullable=false)` |
| `data_fim`              | `TIMESTAMPTZ` NN    | `dataFim`                       | `OffsetDateTime` | `@Column(name="data_fim", nullable=false)` |
| `local`                 | `VARCHAR(200)` NN   | `local`                         | `String`         | `@Column(nullable=false, length=200)` |
| `tipo`                  | `VARCHAR(20)` NN    | `tipo`                          | `TipoEvento`     | `@Enumerated(STRING) @Column(nullable=false, length=20)` |
| `status`                | `VARCHAR(20)` NN    | `status`                        | `StatusEvento`   | `@Enumerated(STRING) @Column(nullable=false, length=20)` |
| `capacidade`            | `INTEGER` NN (>0)   | `capacidade`                    | `Integer`        | `@Column(nullable=false)` |
| `preco`                 | `NUMERIC(12,2)`     | `preco`                         | `BigDecimal`     | `@Column(precision=12, scale=2)` |
| `prazo_reembolso_dias`  | `INTEGER`           | `prazoReembolsoDias`            | `Integer`        | `@Column(name="prazo_reembolso_dias")` |
| `promotor_id`           | `BIGINT` NN         | `promotorId`                    | `Long`           | `@Column(name="promotor_id", nullable=false)` |
| `criado_em`             | `TIMESTAMPTZ` NN    | `criadoEm`                      | `OffsetDateTime` | `@Column(name="criado_em", nullable=false)` |
| `atualizado_em`         | `TIMESTAMPTZ` NN    | `atualizadoEm`                  | `OffsetDateTime` | `@Column(name="atualizado_em", nullable=false)` |
| `vagas_disponiveis` *(V2)* | `INTEGER` (null até publicar) | `vagasDisponiveis` | `Integer`     | `@Column(name="vagas_disponiveis")` |
| `imagem_url` *(V2)*     | `VARCHAR(300)`      | `imagemUrl`                     | `String`         | `@Column(name="imagem_url", length=300)` |

> Notas:
> - `preco` é **`BigDecimal`** (nunca `double`) — `NUMERIC(12,2)`.
> - `capacidade`/`prazoReembolsoDias` são **`Integer`** (objeto), pois `prazo` é nullable e queremos validação na borda, não NPE.
> - `criadoEm`/`atualizadoEm`: o V1 tem `DEFAULT NOW()`, mas como `ddl-auto: validate` não confia em default do banco para `NOT NULL` em insert via JPA, a entidade **seta ambos no construtor** (igual `Usuario.criadoEm`) e `atualizadoEm` no `@PreUpdate`. Não dependa do default do banco.

### Migration **V2** — `V2__eventos_aux.sql` (delta da spec)

```sql
ALTER TABLE eventos ADD COLUMN vagas_disponiveis INTEGER;
ALTER TABLE eventos ADD CONSTRAINT chk_vagas_nao_neg
  CHECK (vagas_disponiveis IS NULL OR vagas_disponiveis >= 0);
ALTER TABLE eventos ADD COLUMN imagem_url VARCHAR(300);
CREATE INDEX idx_eventos_publicados ON eventos(status) WHERE status = 'PUBLICADO';
```

Detalhe (reversibilidade, índice parcial) em [`data-model.md`](data-model.md).

---

## Endpoints novos/alterados → detalhe em [`api-contracts.md`](api-contracts.md)

| Método | Rota `/api` | Auth | Resumo |
|---|---|---|---|
| `POST` | `/events` | PROMOTOR | cria (status RASCUNHO) |
| `GET`  | `/events/meus` | PROMOTOR | meus eventos (qualquer status), paginado |
| `PUT`  | `/events/{id}` | PROMOTOR + owner | edita (regras por status) |
| `POST` | `/events/{id}/publicar` | PROMOTOR + owner | RASCUNHO→PUBLICADO, inicializa `vagas_disponiveis` |
| `POST` | `/events/{id}/cancelar` | PROMOTOR + owner | →CANCELADO |
| `GET`  | `/events` | autenticado | lista **PUBLICADOS** + filtros + paginação |
| `GET`  | `/events/{id}` | autenticado | detalhe (RASCUNHO só owner; senão 404) |

> **Ordem de rotas:** `GET /events/meus` é declarado **antes** de `GET /events/{id}` (ou `meus` casaria como `{id}`). Em Spring MVC a rota literal tem precedência sobre a variável, mas mantenha `meus` separado e explícito para evitar ambiguidade.

Os stubs atuais (`list()`, `get()`, `create()` retornando 501/lista vazia) são **substituídos** integralmente.

---

## Eventos de domínio (AMQP)

**Nenhum nesta sprint.** O cancelamento (Sprint 4+) disparará `evento.cancelado` para a saga de reembolso, e o publicar não emite nada. Topologia AMQP permanece declarada-mas-não-codada (ADR-T04). **Não** adicionar `RabbitTemplate`/`@RabbitListener` agora. O `RabbitAutoConfiguration` continua excluído no profile de teste (já está em `application-test.yml`).

---

## Componentes backend

Estrutura `com.ticketeira.event` (espelha o user-service, **2 camadas — controller fino + service**, sem camada de mapper dedicada):

```
event/
├── controller/EventController.java      # rotas, @Valid, lê X-User-Id / X-User-Papel via @RequestHeader
├── service/EventService.java            # regra: criação, transições de estado, ownership, @Transactional
├── repository/EventRepository.java      # Spring Data JPA + query de listagem à prova de Postgres
├── domain/Evento.java                   # @Entity (+ métodos de domínio: publicar(), cancelar(), atualizarDados())
├── domain/TipoEvento.java               # enum
├── domain/StatusEvento.java             # enum
├── dto/EventoCreateRequest.java         # record + Bean Validation
├── dto/EventoUpdateRequest.java         # record + Bean Validation
├── dto/EventoResponse.java              # record + from(Evento)  (detalhe completo)
├── dto/EventoResumoResponse.java        # record + from(Evento)  (item de lista — sem descrição longa)
└── exception/GlobalExceptionHandler.java # @RestControllerAdvice (cópia do padrão user-service)
```

**Reuso de `common-lib`:** `BusinessException(msg, status)`, `NotFoundException(msg)` (404), `ErrorResponse.of(...)`. O `GlobalExceptionHandler` é **replicado** do user-service (mesmo tratamento de `BusinessException`, `MethodArgumentNotValidException`→400, `DataIntegrityViolationException`→409, genérico→500). Não há `common-lib` com handler compartilhado hoje; replicar é o padrão atual do projeto (≤3 ocorrências, não extrair ainda).

**Validação de papel** (igual `AdminController.requireAdmin`):

```java
private void requirePromotor(String papel) {
    if (!"PROMOTOR".equals(papel)) {
        throw new BusinessException("Acesso restrito a promotores.", 403);
    }
}
```

**Regra de domínio dentro da entidade** (rich-ish, mas sem exagero): `Evento` expõe `publicar()`, `cancelar()`, `atualizarDados(...)` que validam a máquina de estados e lançam `BusinessException`. O `EventService` orquestra (carrega, checa ownership, chama o método de domínio, salva).

---

## Componentes frontend (resumo — detalhe no handoff)

- **api/events.ts** — funções tipadas: `criarEvento`, `listarMeusEventos`, `editarEvento`, `publicarEvento`, `cancelarEvento`, `listarEventos(filtros)`, `obterEvento(id)`. Tudo via `api/client.ts`.
- **Promotor:** página "Meus eventos" (lista + badge de status + ações Publicar/Cancelar) · wizard criar/editar (≤3 etapas: dados → data/local → tipo/preço/capacidade/imagem). Itens de menu só quando `papel === 'PROMOTOR'`.
- **Participante:** página "Eventos" (lista publicados + busca `q` + filtros `tipo`/`de`/`ate` + paginação) · página de detalhe (data/hora **no fuso do usuário**, local, preço ou "Gratuito", capacidade, imagem). **Sem** botão de inscrição (Sprint 3).
- **Estados de UI:** loading (skeleton), empty ("nenhum evento encontrado" + CTA), error (mensagem clara), success (toast `sonner`).
- **Schema Zod** espelha o Bean Validation do back (título obrigatório ≤160, datas, capacidade ≥1, PAGO→preço>0). Datas convertidas para **UTC/ISO-8601 com offset** ao enviar.

---

## Máquina de estados

```
            POST /events                publicar()                cancelar()
   (none) ─────────────► RASCUNHO ──────────────────► PUBLICADO ──────────────► CANCELADO
                            │                              │
                            │ cancelar()                   │ (Sprint 5: job → REALIZADO após data_fim)
                            └───────────────► CANCELADO     └──────────────► REALIZADO
```

| Transição                | Gatilho                       | Permitida? | Erro se inválida |
|--------------------------|-------------------------------|------------|------------------|
| `RASCUNHO → PUBLICADO`   | `POST /{id}/publicar`         | ✅ sim      | — |
| `RASCUNHO → CANCELADO`   | `POST /{id}/cancelar`         | ✅ sim      | — |
| `PUBLICADO → CANCELADO`  | `POST /{id}/cancelar`         | ✅ sim      | — |
| `PUBLICADO → PUBLICADO`  | publicar já publicado         | ❌ não      | **409** `EVENTO_JA_PUBLICADO` |
| `CANCELADO → PUBLICADO`  | publicar cancelado            | ❌ não      | **409** `TRANSICAO_INVALIDA` |
| `CANCELADO → CANCELADO`  | cancelar já cancelado         | ❌ não      | **409** `EVENTO_JA_CANCELADO` |
| `REALIZADO → *`          | qualquer transição            | ❌ não      | **409** `TRANSICAO_INVALIDA` |
| editar (`PUT`) qualquer  | só com `status == RASCUNHO`   | ⚠️ ver abaixo | **409** `EVENTO_NAO_EDITAVEL` se PUBLICADO/CANCELADO/REALIZADO |

**Decisão de edição (US-021 critério 1):** o `PUT /events/{id}` é permitido **somente em RASCUNHO**. Editar um evento PUBLICADO (mudar data/preço de algo que participantes já podem ver) é regra de negócio de outra sprint; aqui mantemos simples e seguro → editar publicado retorna **409 `EVENTO_NAO_EDITAVEL`**. (O critério do PO fala em editar o rascunho; publicar/cancelar são as ações de estado para o publicado.)

**Idempotência de publicar/cancelar:** **não** silenciosa. Publicar 2x → 409 explícito (decisão registrada: "não silencioso", conforme risco do PO). REALIZADO não é alvo de nenhuma ação manual nesta sprint (transição automática fica para Sprint 5).

Cada transição vive na **entidade** (`Evento.publicar()` lança `BusinessException("...", 409)` se `status != RASCUNHO`), garantindo que a invariante não dependa do controller.

---

## Autorização & ownership

**Modelo:** o serviço **não** valida JWT (gateway faz). Lê `X-User-Id` (Long) e `X-User-Papel` (String) via `@RequestHeader`. Se header obrigatório ausente → Spring devolve 400 por padrão; para os endpoints autenticados tratamos como **401** (header `X-User-Id` é a credencial). Endpoints de escrita exigem `X-User-Papel == PROMOTOR`.

| Operação | Regra de papel | Regra de ownership | Não autorizado |
|---|---|---|---|
| `POST /events` | `PROMOTOR` | — (vira owner) | **403** se não PROMOTOR |
| `GET /events/meus` | `PROMOTOR` | filtra `promotor_id = X-User-Id` | **403** se não PROMOTOR |
| `PUT /events/{id}` | `PROMOTOR` | `evento.promotor_id == X-User-Id` | **403** papel · **404** se não-owner |
| `POST /events/{id}/publicar` | `PROMOTOR` | idem | **403** papel · **404** se não-owner |
| `POST /events/{id}/cancelar` | `PROMOTOR` | idem | **403** papel · **404** se não-owner |
| `GET /events` | autenticado (qualquer papel) | só `PUBLICADO` | — |
| `GET /events/{id}` | autenticado | RASCUNHO/CANCELADO só owner | **404** se RASCUNHO alheio |

**Estratégia 403 vs 404 (não vazar existência):**

- **403** quando o problema é o **papel** (participante tentando `POST /events`): é informação pública que escrever exige PROMOTOR; respondemos 403 cedo, **antes** de tocar o banco (critério US-020.3: "sem que nenhum dado seja criado").
- **404** quando o problema é **ownership** (promotor B mexendo no evento do promotor A): respondemos **404**, idêntico a "evento inexistente", para **não revelar** que o id existe (critério US-021.3, US-023.2). Internamente o service carrega o evento, mas se `promotor_id != X-User-Id` lança `NotFoundException` — o promotor B não distingue "não existe" de "não é seu".

> Resumo: **403 = papel errado** (decisão pública); **404 = recurso que você não pode ver** (não enumerável). Isso é consistente com o critério do PO de "não vazar existência".

---

## Estratégias críticas

### 1. Concorrência & integridade de estado
A concorrência pesada (abre-vendas, decremento de vaga) é **da Sprint 3**. Aqui o risco é **integridade de estado** e **ownership**, não corrida por recurso:
- Transições inválidas barradas na entidade → `BusinessException(409)`.
- `vagas_disponiveis` inicializado no publicar (ver §5). O **decremento atômico** (`UPDATE ... WHERE vagas_disponiveis > 0`) **não** é implementado agora — apenas o campo é preparado (**ADR-T07**).
- `@Version` (optimistic lock) **não** é necessário nesta sprint (sem mutação concorrente real). Não adicionar — evitar complexidade precoce. Se em Sprint 3 a edição de capacidade concorrer com reservas, reavaliar.
- Defesa preparatória (spec §8): ao editar capacidade, **não reduzir abaixo de `capacidade - vagas_disponiveis`**. Como em RASCUNHO `vagas_disponiveis` é `null` (só inicializa no publicar) e editar só é permitido em RASCUNHO, esta regra é trivialmente satisfeita nesta sprint — documentada como invariante futura, não implementada como branch morto.

### 2. Timezone — UTC no banco, fuso na borda ⚠️ **GAP a corrigir**
`data_inicio`/`data_fim`/`criado_em`/`atualizado_em` são `TIMESTAMPTZ` ↔ `OffsetDateTime`.
**ACHADO CRÍTICO:** o `application.yml` do **event-service NÃO tem** `hibernate.jdbc.time_zone: UTC` (o user-service tem). Sem isso, o Hibernate grava no fuso default da JVM e a leitura "anda" o horário — exatamente o risco de timezone do PO.
- **Ação obrigatória para o Backend:** adicionar ao `services/event-service/src/main/resources/application.yml`:
  ```yaml
  spring:
    jpa:
      properties:
        hibernate:
          jdbc:
            time_zone: UTC
  ```
- Front envia ISO-8601 **com offset** (`2026-08-01T14:00:00-03:00`); back persiste em UTC; detalhe exibe no fuso do usuário. Smoke do PO: criar evento 14h BRT → detalhe mostra 14h BRT.

### 3. Performance / paginação / query à prova de Postgres ⚠️ **lição Sprint 1**
- **Listagem pública** (`GET /events`) é **sempre paginada** (`Pageable`, defaults `page=0,size=20`), nunca retorno irrestrito. Filtra `status = PUBLICADO` + filtros opcionais.
- **Bug Sprint 1 (`lower(bytea)`):** filtros opcionais `null` quebram no Postgres real (não no H2). **Aplicar a mesma técnica do `UsuarioRepository.findComFiltros`:** `CAST(:q AS string)` no `LIKE`, e `IS NULL OR ...` para cada filtro. Query exata em [`data-model.md`](data-model.md) e [`api-contracts.md`](api-contracts.md).
- **Índices:** `idx_eventos_publicados` (parcial, `WHERE status='PUBLICADO'`) cobre a listagem pública; `idx_eventos_promotor` (já em V1) cobre `GET /events/meus` e o filtro de ownership; `idx_eventos_status` (V1) ajuda agregações. Sem N+1 (entidade chapada, sem relações `@OneToMany` mapeadas nesta sprint).
- **Projeção de lista:** `EventoResumoResponse` (sem `descricao`/campos pesados) no item de lista; `EventoResponse` completo no detalhe.

### 4. Segurança/auth
- Lê `X-User-Id`/`X-User-Papel` (já injetados pelo gateway — Sprint 1 fechou a dívida ADR-T01 para o header de papel). Service confia nos headers.
- 403 (papel) vs 404 (ownership) conforme §Autorização.
- **Sem Spring Security no event-service:** o `pom.xml` **não** inclui `spring-boot-starter-security` (diferente do user-service). Logo **não criar `SecurityConfig`** nem puxar o starter — os endpoints já ficam abertos por padrão e o gateway é o único guardião (valida JWT, injeta `X-User-*`). A autorização aqui é só a checagem de papel/ownership no controller/service via header. Manter assim (menos é mais).

---

## Riscos técnicos

| Risco | Prob | Impacto | Mitigação |
|---|---|---|---|
| `@Entity` não bate com schema V1+V2 (`ddl-auto: validate` quebra no boot Postgres) | Média | Alto | Tabela de mapeamento acima conferida col-a-col; rodar `./mvnw -pl services/event-service test` cedo; **smoke em Postgres real** na validação (não confiar só no H2) |
| Timezone: `application.yml` sem `time_zone: UTC` | **Alta** (gap já existe) | Médio | Adicionar a config (§Estratégia 2) — item explícito no DoD |
| Filtro `null` quebra `LIKE` no Postgres (`lower(bytea)`) | Média | Alto | `CAST(:q AS string)` na query (igual user-service); teste de listagem **com filtro null** rodado contra Postgres |
| H2 do CI mascara bugs de Postgres (test profile usa `create-drop` + Flyway off) | **Alta** | Alto | tests-spec exige caminho-feliz completo + smoke Postgres na validação; ver nota no `tests-spec.md` |
| Ownership furado (promotor edita evento alheio) | Baixa | Alto | teste de ownership obrigatório → 404; lógica de ownership no service, não no controller |
| Ambiguidade de rota `/events/meus` vs `/events/{id}` | Baixa | Médio | declarar `meus` explicitamente; teste de roteamento |
| Edição de evento publicado corrompe oferta visível | Baixa | Médio | `PUT` só em RASCUNHO → 409 `EVENTO_NAO_EDITAVEL` |

---

## Definition of Done técnico

- [ ] `@Entity Evento` + enums batem 1:1 com V1+V2; `ddl-auto: validate` passa **em Postgres** (não só H2).
- [ ] Migration **V2** aplicável e revertível mentalmente; índice parcial criado.
- [ ] `application.yml` do event-service com `hibernate.jdbc.time_zone: UTC`.
- [ ] Stubs 501/lista-vazia removidos; 7 endpoints reais implementados.
- [ ] Authz por papel (403) + ownership (404) testados (cross-papel e cross-owner).
- [ ] Máquina de estados testada (transições válidas e inválidas).
- [ ] **Caminho-feliz** do promotor testado: criar → publicar → aparece na lista → detalhe.
- [ ] Listagem: só PUBLICADOS, filtros combinados, paginação, **filtro null não quebra** (validado em Postgres).
- [ ] Query de listagem com `CAST(:q AS string)`; índice definido junto.
- [ ] Cobertura ≥ 80% no `EventService`.
- [ ] `GlobalExceptionHandler` replicado (BusinessException/validação/integridade/genérico).
- [ ] OpenAPI `docs/api/event-service.yaml` atualizado (sair do "Sprint 0 stub").
- [ ] `./mvnw -B -ntp verify` verde; front sem erro de tipo.
- [ ] ADR-P08 e ADR-T07 registradas (já em `decisions.md`).
