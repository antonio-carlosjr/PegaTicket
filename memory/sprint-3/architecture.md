# Sprint 3 — Arquitetura Técnica (Inscrição & Ingresso QR · gratuito)

> Autor: Arquiteto. Inputs: `00-sprint-spec.md`, `po-planning.md`, `architectural-plan.md` (§7 concorrência), `decisions.md` (ADR-T07/T08/T09), `coding-standards.md` (§1 Concorrência, §3 Banco), `ticket-service/.../V1__init.sql`, `event-service` (gabarito Sprint 2), `docs/api/ticket-service.yaml`. Gabarito de padrão: `user-service` (Sprint 1) e `event-service` (Sprint 2).
> Contratos detalhados → [`api-contracts.md`](api-contracts.md) · Mapeamento entidade⇿coluna → [`data-model.md`](data-model.md) · Specs de teste → [`tests-spec.md`](tests-spec.md).
>
> **Esta é a sprint de maior risco técnico do projeto: o abre-vendas concorrente.** A regra de ouro: *nunca* mais de um vencedor pela última vaga, *nunca* `vagas_disponiveis < 0`, *nunca* dois ingressos para a mesma inscrição. As três defesas vivem no banco (constraint/`UPDATE` atômico), não na aplicação.

---

## Histórias cobertas

- **US-030** — Participante se inscreve num evento **GRATUITO** e **PUBLICADO** → recebe o ingresso (com `codigo_unico` p/ QR) na hora (201, caminho-feliz síncrono, sem dinheiro).
- **US-031** — Controle de **capacidade** (decremento atômico anti-overbooking) + bloqueio de **dupla inscrição** (`UNIQUE(usuario_id, evento_id)`), inclusive sob concorrência (última vaga, dupla inscrição paralela).
- **US-032** — **Ingresso único** por inscrição (`UNIQUE(inscricao_id)` + `codigo_unico` UNIQUE), emitido na **mesma transação local** da inscrição.
- **US-033** — **"Meus ingressos"** (com `codigo_unico`/QR + dados do evento + status) e **histórico de inscrições** (paginado, mais recente primeiro).

**Fora de escopo (intencional):** caminho **pago** / escrow / saga AMQP (Sprint 4) · **check-in** por QR na porta (Sprint 5) · **cancelamento** de inscrição + reembolso (Sprint 5) · lista de inscritos para a promotora (backlog) · notificações por e-mail · consumidores RabbitMQ. **Sem AMQP nesta sprint** (`processed_events` entra na Sprint 4).

---

## Serviço(s) afetado(s)

| Serviço | Mudança |
|---|---|
| **`services/ticket-service`** | **Deixa de ser stub** (501/lista vazia) e vira serviço real: `@Entity Inscricao`/`Ingresso`, `InscricaoRepository`/`IngressoRepository`, `InscricaoService`, `TicketController`, DTOs (records), `EventClient` (cliente REST p/ event-service), `GlobalExceptionHandler` (replicado do event-service). **Sem migration** (V1 já basta — ver `data-model.md`). |
| **`services/event-service`** | **Estende o gabarito da Sprint 2** com 2 endpoints **internos**: `POST /events/{id}/reservar-vaga` (decremento atômico) e `POST /events/{id}/liberar-vaga` (compensação). Novos métodos `@Modifying @Query` no `EventRepository`; novos métodos no `EventService`; novo `InternalEventController` (rota separada + guarda de segredo). |
| **`services/api-gateway`** | **1 mudança obrigatória de segurança:** os endpoints internos `reservar/liberar-vaga` caem na rota wildcard `/api/events/**`. Precisamos **bloqueá-los publicamente** (ver §Autorização inter-serviço / ADR-T08). |
| **`frontend`** | botão "Inscrever-se" (só GRATUITO) no detalhe → ingresso com QR; tela "Meus ingressos" (renderiza QR de `codigo_unico` via lib JS nova); tela "Histórico de inscrições". Detalhe no handoff do Frontend. |

---

## Modelo de dados (delta) → detalhe em [`data-model.md`](data-model.md)

**`ticket_db` V1 já basta — SEM nova migration.** As tabelas `inscricoes`, `ingressos`, `checkins` (e todos os índices/constraints necessários) já existem no `V1__init.sql`. A Sprint 3 apenas **mapeia** `inscricoes` e `ingressos` em `@Entity` (1:1 com o DDL) e **usa** as constraints como mecanismo de concorrência. `checkins` **não** é mapeada nesta sprint (Sprint 5).

### Entidades novas no ticket-service

#### `Inscricao` (tabela `inscricoes`)
Mapeamento 1:1 conferido contra o `V1__init.sql`:

| Coluna (SQL) | Tipo SQL | Campo Java | Tipo Java | Anotação JPA |
|---|---|---|---|---|
| `id` | `BIGSERIAL` PK | `id` | `Long` | `@Id @GeneratedValue(IDENTITY)` |
| `usuario_id` | `BIGINT` NN | `usuarioId` | `Long` | `@Column(name="usuario_id", nullable=false)` |
| `evento_id` | `BIGINT` NN | `eventoId` | `Long` | `@Column(name="evento_id", nullable=false)` |
| `status` | `VARCHAR(20)` NN (`ATIVA`/`CANCELADA`) | `status` | `StatusInscricao` | `@Enumerated(STRING) @Column(nullable=false, length=20)` |
| `inscrito_em` | `TIMESTAMPTZ` NN | `inscritoEm` | `OffsetDateTime` | `@Column(name="inscrito_em", nullable=false)` |

Constraint de concorrência: **`uk_inscricao_usuario_evento UNIQUE (usuario_id, evento_id)`** (V1). É a defesa contra dupla inscrição — inclusive concorrente.

#### `Ingresso` (tabela `ingressos`)

| Coluna (SQL) | Tipo SQL | Campo Java | Tipo Java | Anotação JPA |
|---|---|---|---|---|
| `id` | `BIGSERIAL` PK | `id` | `Long` | `@Id @GeneratedValue(IDENTITY)` |
| `inscricao_id` | `BIGINT` NN **UNIQUE** | `inscricaoId` | `Long` | `@Column(name="inscricao_id", nullable=false, unique=true)` |
| `codigo_unico` | `VARCHAR(64)` NN **UNIQUE** | `codigoUnico` | `String` | `@Column(name="codigo_unico", nullable=false, unique=true, length=64)` |
| `status` | `VARCHAR(20)` NN (`ATIVO`/`UTILIZADO`/`CANCELADO`) | `status` | `StatusIngresso` | `@Enumerated(STRING) @Column(nullable=false, length=20)` |
| `emitido_em` | `TIMESTAMPTZ` NN | `emitidoEm` | `OffsetDateTime` | `@Column(name="emitido_em", nullable=false)` |

> **Decisão de modelagem `inscricao_id`:** mapeamos como **`Long` simples**, *não* como `@OneToOne Inscricao`. Inscrição e ingresso são criados na mesma transação, no mesmo service; a relação JPA não agrega valor e abriria porta a N+1/lazy-loading. (Princípio: sem abstração precoce.) A integridade é garantida por `UNIQUE(inscricao_id)` no banco.

> **`criado_em`/`emitido_em`/`inscrito_em`:** o V1 tem `DEFAULT NOW()`, mas com `ddl-auto: validate` **não dependemos do default do banco** para `NOT NULL` em insert via JPA (lição da Sprint 2). A entidade **seta o timestamp no factory method** (`Inscricao.criar(...)` / `Ingresso.emitir(...)`).

### Enums (`@Enumerated(STRING)`, espelham os `CHECK`)

```java
public enum StatusInscricao { ATIVA, CANCELADA }     // CANCELADA só na Sprint 5
public enum StatusIngresso  { ATIVO, UTILIZADO, CANCELADO } // UTILIZADO/CANCELADO na Sprint 5
```

> Nesta sprint só produzimos `ATIVA`/`ATIVO`. Os demais valores existem no enum (espelham o `CHECK`) mas só são atingidos em Sprints futuras — **não** criar branch morto que os produza agora.

### event-service: nada de schema novo
`vagas_disponiveis` (V2 da Sprint 2) já existe, com `chk_vagas_nao_neg (vagas_disponiveis IS NULL OR vagas_disponiveis >= 0)`. O `UPDATE ... WHERE vagas_disponiveis > 0` opera sobre essa coluna. O `CHECK >= 0` é a **última linha de defesa** (defesa em profundidade): mesmo num bug de lógica, o banco recusa o decremento que tornaria negativo.

---

## Endpoints novos → detalhe em [`api-contracts.md`](api-contracts.md)

### Públicos (via gateway `/api/tickets/**` → ticket-service `/tickets/**`)
| Método | Rota `/api` | Auth | Resumo |
|---|---|---|---|
| `POST` | `/tickets/inscricoes` | autenticado | inscreve em evento **GRATUITO** publicado → **201** com ingresso; 409 já-inscrito/esgotado; 422 pago/não-publicado; 503 event-service fora |
| `GET` | `/tickets/me` | autenticado | meus **ingressos** (com `codigo_unico`, dados do evento, status) |
| `GET` | `/tickets/inscricoes/me` | autenticado | **histórico** de inscrições (paginado, mais recente primeiro) |

### Internos (ticket→event, **NÃO públicos** — ver §Autorização inter-serviço)
| Método | Rota | Auth | Resumo |
|---|---|---|---|
| `POST` | `/internal/events/{id}/reservar-vaga` | segredo compartilhado | decremento atômico: **200** reservou · **409** esgotado · **404** inexistente · **422** não-publicado/cancelado |
| `POST` | `/internal/events/{id}/liberar-vaga` | segredo compartilhado | compensação: **200** liberou (incremento limitado pela capacidade) · idempotente/no-op se já no teto |

> **Decisão de rota dos internos:** prefixo dedicado **`/internal/...`** (não `/events/{id}/reservar-vaga`). Razão: a rota do gateway é `Path=/api/events/**` com `StripPrefix=1`. Se os internos ficassem sob `/events/...`, seriam alcançáveis como `/api/events/{id}/reservar-vaga` (o wildcard casa) — exatamente o risco do PO ("`reservar-vaga` exposto"). Com o prefixo `/internal/...`, **não existe rota no gateway que case** (`/api/internal/**` não está mapeado → 404 no gateway). É a defesa de roteamento; o segredo compartilhado é a defesa de autorização (ADR-T08). Mapeamento concreto e tabela de roteamento em §Autorização inter-serviço.

---

## Eventos de domínio (AMQP)

**Nenhum nesta sprint.** O fluxo gratuito é **inteiramente síncrono** (ticket→event via REST). RabbitMQ permanece declarado-mas-não-codado (ADR-T04). O `RabbitAutoConfiguration` continua excluído no profile de teste (já está no `application-test.yml` do ticket-service). `processed_events` (idempotência de consumidor) entra na Sprint 4 com o caminho pago. **Não** adicionar `RabbitTemplate`/`@RabbitListener` agora.

---

## Componentes backend

### ticket-service — estrutura `com.ticketeira.ticket` (espelha event/user-service, 2 camadas: controller fino + service)

```
ticket/
├── controller/TicketController.java         # /tickets — rotas públicas; lê X-User-Id via @RequestHeader
├── service/InscricaoService.java            # ORQUESTRA a mini-saga; @Transactional só na etapa local
├── repository/InscricaoRepository.java       # Spring Data JPA (+ findByUsuarioId paginado)
├── repository/IngressoRepository.java        # join p/ "meus ingressos" sem N+1
├── domain/Inscricao.java                    # @Entity + factory Inscricao.criar(...)
├── domain/Ingresso.java                     # @Entity + factory Ingresso.emitir(inscricaoId, codigo)
├── domain/StatusInscricao.java · StatusIngresso.java
├── client/EventClient.java                  # RestClient: validar evento + reservar/liberar vaga
├── client/EventResumo.java                  # record do payload de validação (id, tipo, status, vagasDisponiveis, ...)
├── config/EventClientConfig.java            # bean RestClient (baseUrl=EVENT_SERVICE_URL, timeouts, header de segredo)
├── dto/InscricaoRequest.java · InscricaoResponse.java · IngressoResponse.java · InscricaoHistoricoResponse.java
└── exception/GlobalExceptionHandler.java    # @RestControllerAdvice (cópia do padrão event/user-service)
```

**Reuso de `common-lib`:** `BusinessException(msg, status)`, `NotFoundException(msg)` (404), `ErrorResponse.of(...)`. O `GlobalExceptionHandler` é **replicado** do event-service (mesmo tratamento de `BusinessException`, `MethodArgumentNotValidException`→400, `MethodArgumentTypeMismatchException`→400, `DataIntegrityViolationException`→409, genérico→500). Já são ≤3 ocorrências do handler (user/event/ticket) — **não extrair** ainda; replicar é o padrão atual.

**Leitura de auth:** o ticket-service **não** valida JWT (gateway faz). `TicketController` lê `@RequestHeader("X-User-Id") Long userId`; se ausente → **401** (`UnauthorizedException`). **Não há checagem de papel** nesta sprint — qualquer autenticado (papel PARTICIPANTE base, ADR-P07) pode se inscrever. Não criar `SecurityConfig` nem puxar `spring-boot-starter-security` (o ticket-service não o tem; manter assim — o gateway é o guardião).

### event-service — extensão do gabarito (sem quebrar nada da Sprint 2)

```
event/
├── controller/InternalEventController.java  # NOVO: /internal/events/{id}/reservar-vaga | liberar-vaga; guarda de segredo
├── service/EventService.java                # +reservarVaga(eventoId) · +liberarVaga(eventoId)
├── repository/EventRepository.java          # +decrementarVaga(id) @Modifying · +incrementarVaga(id) @Modifying
└── config/InternalAuthFilter.java (ou checagem no controller)  # valida X-Internal-Token
```

> O `EventController` público (Sprint 2) **não muda**. Os internos vivem num controller separado com `@RequestMapping("/internal/events")` — isolamento físico + facilita o bloqueio de roteamento.

---

## Estratégias críticas de concorrência (o coração da sprint)

### 1. Decremento atômico anti-overbooking (event-service) — ADR-T07

A reserva de vaga **não** é "ler `vagasDisponiveis`, checar, decrementar e salvar" (isso é um clássico read-modify-write com race). É **um único `UPDATE` condicional**, atômico no banco, cuja cláusula `WHERE` é a checagem:

```java
// EventRepository
@Modifying
@Query("""
        UPDATE Evento e
           SET e.vagasDisponiveis = e.vagasDisponiveis - 1
         WHERE e.id = :id
           AND e.status = com.ticketeira.event.domain.StatusEvento.PUBLICADO
           AND e.vagasDisponiveis > 0
        """)
int decrementarVaga(@Param("id") Long id);
```

- **`rowsAffected == 1`** → reservou (havia vaga). **`rowsAffected == 0`** → esgotado **ou** evento não-publicado. O service distingue os dois carregando o evento *só nesse caso 0* (caminho frio) para devolver 409 (esgotado) vs 422 (não-publicado) vs 404 (inexistente) — ver §saga.
- Por que é à prova de corrida: o `UPDATE ... WHERE vagas > 0` adquire um **row lock** no Postgres; requisições concorrentes serializam-se nessa linha. Apenas a transação que vê `vagas > 0` decrementa; as demais veem o valor já decrementado e afetam 0 linhas. **Não há janela** entre checar e decrementar — é a mesma operação.
- `@Modifying` exige `@Transactional` no método de service. A query roda em sua **própria transação curta** (a chamada HTTP do ticket é uma unidade independente — ver §saga: o event-service commita a reserva *antes* de o ticket começar sua tx local).
- **Defesa em profundidade:** mesmo se a lógica falhasse, o `CHECK (vagas_disponiveis >= 0)` (V2) faria o Postgres recusar um decremento que tornasse negativo.

> **Por que não `@Version` (optimistic):** com `@Version` o abre-vendas vira uma tempestade de `OptimisticLockException`/retries (N threads competindo pela mesma versão → quase todas falham e precisam re-tentar, O(n²) de retentativas no pior caso). O `UPDATE ... WHERE` atômico resolve em **uma** ida ao banco por requisição, sem retry, O(1) por inscrição — é a escolha correta para alta contenção numa única linha. (Decisão registrada.)

### 2. `liberar-vaga` (compensação) — incremento **limitado pela capacidade**

```java
// EventRepository — incrementa, mas nunca acima de capacidade, e só se PUBLICADO
@Modifying
@Query("""
        UPDATE Evento e
           SET e.vagasDisponiveis = e.vagasDisponiveis + 1
         WHERE e.id = :id
           AND e.status = com.ticketeira.event.domain.StatusEvento.PUBLICADO
           AND e.vagasDisponiveis < e.capacidade
        """)
int incrementarVaga(@Param("id") Long id);
```

- `rowsAffected == 1` → liberou; `0` → **no-op idempotente** (já no teto, ou evento não mais publicado). Liberar nunca deve estourar a capacidade (senão um retry de compensação inflaria as vagas). O `WHERE vagas < capacidade` garante o teto.
- **Idempotência da compensação:** chamar `liberar-vaga` 2x para a mesma reserva *poderia* devolver 2 vagas indevidamente. Como nesta sprint a compensação é disparada **uma única vez** pelo ticket-service no caminho de falha (não há retry automático de `liberar`), e o teto `< capacidade` limita o dano, aceitamos o modelo simples. **Registramos a limitação:** `liberar-vaga` não é perfeitamente idempotente por reserva individual (não há "token de reserva"); o teto da capacidade é a salvaguarda. Reconciliação fina fica para quando houver saga AMQP (Sprint 4).

### 3. Unicidade da inscrição (dupla inscrição) — ticket-service

`UNIQUE(usuario_id, evento_id)` (V1). O service **tenta inserir** e captura `DataIntegrityViolationException` → **409 JA_INSCRITO**. **Não** fazer "SELECT antes para checar" como única defesa (TOCTOU: duas threads passam o SELECT e ambas tentam inserir). O SELECT prévio é apenas otimização do caminho comum (ver §saga passo 1.5); a **constraint é a verdade**.

### 4. Ingresso único por inscrição — ticket-service

`UNIQUE(inscricao_id)` + `codigo_unico` UNIQUE (V1). Inscrição **e** ingresso são criados na **mesma transação local** (`@Transactional` em `InscricaoService.criarInscricaoLocal`). Se a tx falha, **ambos** revertem (atomicidade local). Retry de uma inscrição já existente → a constraint barra a segunda emissão.

---

## Mini-saga síncrona cross-service (ticket→event) — ORDEM DEFINITIVA

Não há transação distribuída (bancos isolados, ADR-0002). Coordenamos com uma **saga síncrona com compensação**. A ordem abaixo é definitiva e justificada.

```
InscricaoService.inscrever(usuarioId, eventoId):

  PASSO 1  — VALIDAR EVENTO (GET event-service /internal/events/{id} ... ou GET público /events/{id})
             existe? PUBLICADO? tipo=GRATUITO?
             ├─ não existe        → 404 EVENTO_NAO_ENCONTRADO        (nada reservado)
             ├─ não PUBLICADO     → 422 EVENTO_NAO_PUBLICADO          (nada reservado)
             ├─ tipo=PAGO         → 422 EVENTO_PAGO_NAO_SUPORTADO     (nada reservado; pago é Sprint 4)
             └─ event-service down/timeout → 503 EVENTO_INDISPONIVEL  (nada reservado)

  PASSO 1.5 — PRÉ-CHECK DE DUPLICIDADE (otimização, NÃO defesa) [@Transactional(readOnly)]
             existsByUsuarioIdAndEventoId? → se já existe: 409 JA_INSCRITO
             ↳ evita "gastar" uma vaga (reservar) no caso comum de o usuário já estar inscrito.
               NÃO é a defesa contra corrida — só evita o trabalho/compensação no caminho frequente.

  PASSO 2  — RESERVAR VAGA (POST event-service /internal/events/{id}/reservar-vaga)  [decremento atômico]
             ├─ 200            → vaga reservada, segue
             ├─ 409 ESGOTADO  → 409 EVENTO_ESGOTADO                  (nada a compensar; não decrementou)
             ├─ 422           → 422 (estado mudou entre passo 1 e 2: ficou não-publicado/cancelado)
             └─ down/timeout  → 503 EVENTO_INDISPONIVEL              (ver §timeouts: se incerto, ver compensação)

  PASSO 3  — TX LOCAL ATÔMICA [@Transactional]: cria Inscricao(ATIVA) + Ingresso(codigo_unico, ATIVO)
             ├─ sucesso (commit) → segue para PASSO 4
             └─ DataIntegrityViolationException (UNIQUE usuario,evento — corrida do passo 1.5):
                   COMPENSA: POST /internal/events/{id}/liberar-vaga  → 409 JA_INSCRITO
             └─ qualquer outra falha de persistência:
                   COMPENSA: POST /internal/events/{id}/liberar-vaga  → 500 (ou 503) ao cliente
                   ↳ se a compensação TAMBÉM falhar: LOGAR p/ reconciliação (vaga "presa") — ver §reconciliação

  PASSO 4  — RETORNA 201 InscricaoResponse { inscricao + ingresso.codigoUnico p/ QR }
```

### Por que esta ordem (justificativa para o `architecture.md`)

1. **Validar antes de reservar (passo 1 antes do 2):** não faz sentido decrementar uma vaga de um evento PAGO/RASCUNHO — falharíamos depois e teríamos de compensar. Validar primeiro mantém o caminho de erro **sem efeito colateral** (PO US-031.6: "se event-service fora, nenhuma vaga é debitada").
2. **Pré-check de duplicidade antes de reservar (passo 1.5 antes do 2):** o caso comum de erro é o usuário re-clicar / já estar inscrito. Checar `exists` *antes* de reservar evita gastar uma vaga e ter de compensá-la no caminho frequente — economiza um round-trip de compensação e reduz "ruído" no `vagas_disponiveis`. **Mas a corrida real** (duas requisições simultâneas do mesmo usuário, ambas passam o `exists`) é resolvida **só** no passo 3 pela `UNIQUE` + compensação. O passo 1.5 é otimização; o passo 3 é a garantia.
3. **Reservar antes da tx local (passo 2 antes do 3):** a vaga é o recurso **escasso e compartilhado** (contenção alta entre usuários diferentes); a inscrição local é recurso **privado** do par (usuario,evento) (contenção baixa). Reservamos o escasso primeiro e o liberamos por compensação se o privado falhar. A alternativa (criar inscrição local primeiro, reservar depois) deixaria inscrições "órfãs sem vaga" e exigiria compensação no event-service de qualquer modo — pior, porque a inscrição órfã é visível ao usuário. **Reservar→local→compensar** mantém o estado visível ao usuário sempre consistente: ou tem vaga+inscrição+ingresso, ou tem nada.
4. **Compensar só se o passo 3 falhar *após* o passo 2 ter sucesso:** é o único ponto onde uma vaga foi debitada sem inscrição correspondente. A compensação (`liberar-vaga`) restaura.

### Idempotência da inscrição (US-032.4: retry não gera 2 ingressos)

- A **chave natural de idempotência** é `UNIQUE(usuario_id, evento_id)`. Um retry do cliente (rede caiu após o 201) re-`POST` → passo 1.5 ou passo 3 detecta a inscrição existente → **409 JA_INSCRITO** (não um segundo ingresso). 
- **Decisão de contrato:** no retry, devolvemos **409 JA_INSCRITO** (não "200 com o ingresso existente"). É mais simples e seguro; o frontend, ao ver 409 numa inscrição que ele acha que falhou, faz `GET /tickets/me` para recuperar o ingresso. (Registrado no handoff: o front **não** deve re-POST cegamente; em erro de rede, consultar `GET /tickets/me` antes de re-tentar.)
- **Por que não devolver o ingresso no 409:** evitar acoplar o corpo do erro ao recurso; o `GET /tickets/me` é a fonte canônica. Mantém o contrato de erro uniforme.

### Timeouts, retry e o caso ambíguo do passo 2

- **Cliente REST com timeouts curtos** (connect 2s, read 3s — ver §EventClient). O abre-vendas não pode pendurar threads.
- **Retry:** **não** re-tentar automaticamente o `reservar-vaga` (passo 2). `reservar-vaga` **não é idempotente** (cada chamada decrementa); um retry após timeout poderia decrementar 2 vagas. Em timeout/erro de conexão no passo 2 → **503 ao cliente**, e a inscrição falha inteira. 
- **Caso ambíguo (a reserva pode ter ou não acontecido):** se o `reservar-vaga` deu timeout de *leitura* (a requisição pode ter chegado e decrementado, mas a resposta se perdeu), há risco de uma vaga "presa". **Mitigação MVP:** como não re-tentamos e a tx local não roda, no pior caso perdemos 1 vaga (evento parece ter 1 vaga a menos). Isso é **conservador** (nunca causa overbooking — erra para menos, nunca para mais). Logamos o incidente para reconciliação. Para o escopo acadêmico desta sprint, "errar para menos sob falha de rede rara" é aceitável e explicitamente preferível a qualquer risco de overbooking.
- **Compensação que falha (passo 3 falhou, `liberar-vaga` também falhou):** logar em nível `ERROR` com `usuarioId`, `eventoId`, timestamp e a causa, marcado para **reconciliação manual/job futuro**. Não temos tabela de reconciliação nesta sprint (seria over-engineering); o log estruturado é o registro. Resultado para o usuário: erro 500/503 (a inscrição não foi criada) — a vaga presa é o único dano, conservador.

### Diagrama da saga (sequência)

```
Cliente            ticket-service                         event-service
  │  POST /tickets/inscricoes {eventoId}                       │
  │ ─────────────►│                                            │
  │               │ 1. GET evento (valida) ───────────────────►│
  │               │ ◄─────────────── 200 {tipo,status,vagas}   │
  │               │ 1.5 exists(usuario,evento)? (DB local)     │
  │               │ 2. POST /internal/.../reservar-vaga ──────►│  UPDATE ... WHERE vagas>0
  │               │ ◄─────────────── 200 (rowsAffected=1)      │  (atômico, row lock)
  │               │ 3. @Tx: INSERT inscricao + INSERT ingresso │
  │               │     └─ falhou? POST /liberar-vaga ────────►│  (compensa)
  │               │ 4. 201 {inscricao, ingresso.codigoUnico}   │
  │ ◄─────────────│                                            │
```

---

## Cliente REST ticket→event (`EventClient`)

- **Tecnologia:** **`RestClient`** (Spring Framework 6.1+, síncrono, já disponível via `spring-boot-starter-web` — **nenhuma dependência nova**). Preferido a `RestTemplate` (legado) e a `WebClient` (reativo, desnecessário aqui — o ticket-service é MVC bloqueante).
- **Base URL:** via env **`EVENT_SERVICE_URL`** (default `http://localhost:8082`; em Docker `http://event-service:8082`). Configurado num `@Bean RestClient` em `EventClientConfig`.
- **Timeouts:** connect 2s, read 3s (via `ClientHttpRequestFactorySettings` / `SimpleClientHttpRequestFactory`). Justificativa no §timeouts.
- **Header de segredo:** toda chamada injeta `X-Internal-Token: ${INTERNAL_SHARED_SECRET}` (ADR-T08).
- **Tradução de erros do event-service → erros do ticket-service:**

| Resposta do event-service | `EventClient` lança | ticket-service responde |
|---|---|---|
| 200 reservar | (ok) | segue |
| 404 (evento inexistente) | `NotFoundException` | 404 EVENTO_NAO_ENCONTRADO |
| 409 ESGOTADO | `BusinessException("EVENTO_ESGOTADO", 409)` | 409 EVENTO_ESGOTADO |
| 422 (não-publicado/pago) | `BusinessException("EVENTO_NAO_PUBLICADO", 422)` | 422 |
| timeout / connection refused / 5xx | `BusinessException("EVENTO_INDISPONIVEL", 503)` | 503 |

> O `RestClient` configura `.defaultStatusHandler(...)` para mapear status do event-service nessas exceções, em vez de deixar vazar `RestClientResponseException` crua (que cairia no handler genérico → 500). **Erro de comunicação nunca pode virar 500 silencioso** — vira 503 tipado.

---

## Autorização inter-serviço (ticket→event) — ADR-T08

**Risco concreto encontrado no código (não hipotético):** o gateway tem `route events: Path=/api/events/** → event-service` com `StripPrefix=1` e `JwtAuthGlobalFilter` só protege com JWT (qualquer usuário logado passa). Se os endpoints internos ficassem sob `/events/{id}/reservar-vaga`, **qualquer participante autenticado** poderia chamar `POST /api/events/{id}/reservar-vaga` repetidamente e **zerar `vagas_disponiveis`** de qualquer evento sem se inscrever (DoS de vagas). O planning dizia "não roteado pelo gateway" — mas o **wildcard `/api/events/**` roteia**. Precisa de defesa explícita.

**Decisão (duas camadas):**

1. **Roteamento — prefixo `/internal/...` não mapeado no gateway.** Os internos vivem em `POST /internal/events/{id}/reservar-vaga` e `/liberar-vaga`. O gateway **não tem** rota `/api/internal/**` → uma tentativa externa de `/api/internal/...` retorna **404 no gateway** (nunca chega ao event-service). Confirmado contra `application.yml` + `application-docker.yml` do gateway: as únicas rotas são `auth`, `users`, `events` (`/api/events/**`), `tickets`, `payments`. Nenhuma casa `/internal`. **DoD: teste/smoke confirmando que `/api/internal/events/1/reservar-vaga` via gateway dá 404.**

2. **Autorização — segredo compartilhado `X-Internal-Token`.** Os internos exigem o header `X-Internal-Token == ${INTERNAL_SHARED_SECRET}` (env, gitignored, em `.env`/`.env.example` com placeholder). Sem ele → **403**. Camada de profundidade caso a rede Docker seja comprometida ou alguém adicione uma rota errada no gateway no futuro. O ticket-service injeta o header no `EventClient`. Implementação: um `@Bean OncePerRequestFilter` (ou checagem no `InternalEventController`) que valida o header só nas rotas `/internal/**`.

> **Não usamos mTLS** nesta sprint (over-engineering para o MVP acadêmico). O segredo compartilhado + isolamento de rede + não-roteamento é a postura registrada; evolução para mTLS fica anotada como dívida. **Importante:** o `X-Internal-Token` é um segredo de *infra* (serviço↔serviço), distinto dos `X-User-*` (injetados pelo gateway). O gateway **não** injeta nem repassa `X-Internal-Token`; mesmo que um cliente externo o forjasse no header, ele não alcança `/internal/**` (camada 1).

---

## Estratégia de `codigo_unico` (QR) — ADR-T09

**Decisão: UUID v4 (aleatório), gerado no backend (`java.util.UUID.randomUUID()`), persistido em `codigo_unico VARCHAR(64)`.**

Justificativa:
- **Não-forjável na prática:** UUID v4 tem 122 bits de entropia (CSPRNG). Adivinhar um código válido é inviável; não há padrão sequencial a explorar.
- **Suficiente para o check-in da Sprint 5:** a validação na porta será **lookup no banco** (`SELECT ... WHERE codigo_unico = ?` + checar `status=ATIVO` e `evento` correto), não verificação criptográfica offline. Para um lookup server-side, UUID basta — o atacante teria de adivinhar um UUID que *exista* e esteja *ativo* para *aquele* evento.
- **Simplicidade > HMAC agora:** HMAC-assinado só agrega valor se o check-in precisar validar **offline** (sem ir ao banco) ou se quiséssemos detectar adulteração sem lookup. Nenhum dos dois é requisito conhecido. Introduzir HMAC agora (gestão de chave, formato do payload, rotação) é abstração precoce.
- **`UNIQUE(codigo_unico)`** é a última linha de defesa contra a colisão (praticamente impossível em v4, mas o banco garante).

**Impacto declarado na Sprint 5 (check-in):** o check-in fará lookup por `codigo_unico` e marcará `ingressos.status=UTILIZADO` + criará `checkins` (constraint `UNIQUE(ingresso_id)` impede duplo check-in). **Se** num futuro a validação offline/anti-adulteração virar requisito, migra-se para HMAC então — mas isso **invalidaria os QRs já emitidos**, então a decisão de UUID é registrada como definitiva para o escopo atual. (Anotado em ADR-T09.)

**QR no frontend:** o backend devolve `codigo_unico` (string) na resposta da inscrição e em `GET /tickets/me`. O **frontend renderiza a imagem do QR** a partir dessa string com uma lib JS (nova dependência do front — `qrcode.react` ou `qrcode` — justificada no `frontend-log.md`). **O backend NÃO gera imagem de QR.**

---

## Performance / O(n) / sem N+1

- **`GET /tickets/me` (meus ingressos):** precisa de `inscricao` (status do evento) + `ingresso` (codigo/status) + dados do evento (nome/data/local — que vivem no **event-service**, outro banco). Os ingressos do usuário são lidos com **um** join `ingressos ⨝ inscricoes` no ticket_db (sem N+1: `JOIN FETCH` ou projeção numa query), filtrando `inscricoes.usuario_id = :userId`. Os **dados do evento** (nome/data/local) vêm do event-service: para evitar N chamadas REST (uma por ingresso, N+1 cross-service), o front pode (a) buscar os eventos em lote, ou (b) o ticket-service expõe só os ids/codigos e o front compõe. **Decisão MVP:** `GET /tickets/me` devolve `eventoId` + dados do ingresso; o **frontend** busca os detalhes dos eventos (lista já em cache do "Eventos", ou um GET por id). Mantém o ticket-service simples e sem fan-out REST. (Se virar dor, considerar um endpoint de batch no event-service numa sprint futura — não agora, sem 3 ocorrências.)
- **`GET /tickets/inscricoes/me` (histórico):** **sempre paginado** (`Pageable`, default `page=0,size=20`, `sort=inscritoEm,desc`), nunca retorno irrestrito. Índice `idx_inscricoes_usuario` (V1) cobre o filtro `usuario_id`.
- **Reserva/decremento:** O(1) — um `UPDATE` por linha indexada por PK.

---

## Riscos técnicos

| Risco | Prob | Impacto | Mitigação |
|---|---|---|---|
| **Overbooking na última vaga** (race) | **Alta** | **Crítico** | `UPDATE ... WHERE vagas>0` atômico + checar `rowsAffected`; `CHECK(vagas>=0)` como rede; **teste de concorrência K-threads é gate do DoD** (ver tests-spec) |
| **`reservar-vaga` exposto publicamente pelo wildcard `/api/events/**`** | **Alta** (o wildcard casa hoje) | **Alto** | prefixo `/internal/**` (não roteado no gateway → 404) + `X-Internal-Token` (403); **teste: `/api/internal/...` via gateway = 404** |
| Falha parcial cross-service (event cai após reservar) | Média | Alto | compensação `liberar-vaga`; sem retry do reservar (não-idempotente) → erra para menos (nunca overbooking); log p/ reconciliação |
| Dupla inscrição concorrente (mesmo usuário, 2 reqs) | Média | Médio | `UNIQUE(usuario,evento)` + captura `DataIntegrityViolationException` → 409 + compensa a vaga; teste concorrente |
| H2 (CI) **mascara** o lock de linha do Postgres | **Alta** | **Alto** | o teste de última-vaga concorrente exige **Postgres real** (Testcontainers) ou smoke de carga via Docker — H2 in-process não reproduz fielmente `UPDATE...WHERE` sob concorrência. Ver nota crítica no `tests-spec.md` |
| Timezone: ticket-service `application.yml` **sem** `hibernate.jdbc.time_zone: UTC` | **Alta** (gap existe) | Médio | adicionar a config (igual event-service Sprint 2) — item do DoD; `inscrito_em`/`emitido_em` são TIMESTAMPTZ |
| `@Entity` não bate com V1 (`ddl-auto: validate` quebra no boot Postgres) | Média | Alto | tabela de mapeamento conferida col-a-col (acima + `data-model.md`); smoke em Postgres real na validação |
| `RestClientResponseException` crua vira 500 | Média | Médio | `EventClient` com `defaultStatusHandler` mapeando para `BusinessException` tipada (503/409/422) |
| Compensação que também falha → vaga presa | Baixa | Médio | log `ERROR` estruturado p/ reconciliação; teto `<capacidade` no liberar evita inflar |

---

## Definition of Done técnico

- [ ] `@Entity Inscricao`/`Ingresso` + enums batem 1:1 com `V1__init.sql`; `ddl-auto: validate` passa **em Postgres** (não só H2).
- [ ] **Sem nova migration** no ticket_db (confirmado: V1 basta) — documentado em `data-model.md`.
- [ ] ticket-service `application.yml` com `hibernate.jdbc.time_zone: UTC`.
- [ ] event-service: `reservar-vaga` (decremento atômico `WHERE vagas>0`, checa `rowsAffected`) e `liberar-vaga` (incremento `WHERE vagas<capacidade`) implementados e **testados isoladamente**.
- [ ] Internos sob `/internal/events/**` + `X-Internal-Token`; **smoke: `/api/internal/events/1/reservar-vaga` via gateway → 404** (não roteado).
- [ ] `EventClient` (RestClient) com `EVENT_SERVICE_URL`, timeouts (connect 2s/read 3s), header de segredo, e tradução de erro (down→503, esgotado→409, não-pub→422) — **comunicação nunca vira 500**.
- [ ] Mini-saga implementada na ordem definitiva (validar→pré-check→reservar→tx local→compensar); compensação no caminho de falha do passo 3.
- [ ] **TESTE DE CONCORRÊNCIA — GATE INEGOCIÁVEL:** última vaga (K threads → exatamente 1 sucesso, K-1×409, `vagas_disponiveis`=0 nunca negativo) **rodado em Postgres real** (Testcontainers ou smoke Docker).
- [ ] Dupla inscrição concorrente testada (1 sucesso, 1×409).
- [ ] Compensação testada (falha forçada no passo 3 → `vagas_disponiveis` restaurada).
- [ ] Ingresso único por inscrição testado (segunda emissão falha).
- [ ] Evento pago/não-publicado → 422 testado.
- [ ] Caminho-feliz completo testado (inscrever → 201 com codigo_unico → aparece em `GET /tickets/me`).
- [ ] `GET /tickets/inscricoes/me` paginado, `sort=inscritoEm,desc`; sem N+1 em `GET /tickets/me`.
- [ ] Cobertura ≥ 80% no `InscricaoService`.
- [ ] `GlobalExceptionHandler` replicado (BusinessException/validação/type-mismatch→400/integridade→409/genérico→500).
- [ ] Stubs do `TicketController` (501/lista vazia) removidos.
- [ ] OpenAPI `docs/api/ticket-service.yaml` atualizado (sair de "Sprint 0 stubs"); contrato dos internos documentado.
- [ ] Front: inscrever (só GRATUITO) + meus-ingressos (QR via lib JS) + histórico, estados de UI (loading/empty/error/success).
- [ ] `./mvnw -B -ntp verify` verde; front sem erro de tipo.
- [ ] ADR-P09 (já existe), ADR-T07/T08/T09 complementados com a decisão definitiva (este doc).
