# Coding Standards — Ticketeira

> Padrões para todo código do monorepo. Backend (Spring/Java) e Frontend (React/TS) seguem este doc.
> Atualizações relevantes viram ADR em [`memory/project/decisions.md`](../memory/project/decisions.md).
> Leitura obrigatória de Backend, Frontend, Arquiteto, Tester e DevOps.

---

## 0. Princípios transversais

1. **Identificadores em inglês; comentários e docs em pt-BR.** (convenção do projeto)
2. **Comentário só explica o "porquê"**, nunca o "o quê" (o nome já diz). Sem `// removido`, `// fix #123`, sem TODO órfão.
3. **Sem código morto.** Se não é usado, não entra (ou é removido no mesmo PR).
4. **Reuso antes de criar.** 3 ocorrências antes de extrair abstração.
5. **Complexidade O(n) ou melhor** em loops sobre dados de domínio. Pior que isso → justificar no log.
6. **Race condition é bug grave** (ver §4). Toda mutação concorrente declara sua estratégia.
7. **Segredos só via `.env`** (gitignored). Nunca hardcode. `.env.example` mantém placeholders.

---

## 1. Backend — Java 21 / Spring Boot

### Estrutura por serviço (package `com.ticketeira.<svc>`)
```
<svc>/
├── <Svc>Application.java
├── config/         # SecurityConfig, JwtConfig, RabbitConfig, OpenApiConfig
├── controller/     # @RestController — rota, validação (@Valid), lê X-User-* via @RequestHeader
├── service/        # regra de negócio (@Service, @Transactional)
├── repository/     # Spring Data JPA (interfaces)
├── domain/         # @Entity, enums, value objects
├── dto/            # records de request/response (Bean Validation)
└── exception/      # GlobalExceptionHandler (@RestControllerAdvice)
```
Resources: `db/migration/V__*.sql` (Flyway), `application.yml` + `application-{docker,prod,test}.yml`.

### Regras
- **Java 21 idioms:** `record` para DTOs e value objects, `sealed`/pattern matching quando ajudar, `Optional` em repositório (`findByX`), Streams sem efeito colateral.
- **DTOs são `record`** com Bean Validation (`@NotBlank`, `@Email`, `@Size`, `@Pattern`). Validar na **borda** (controller `@Valid`). Nunca expor `@Entity` na API; mapear via `Response.from(entity)`.
- **Service** carrega a regra; controller é fino. `@Transactional` no service; `readOnly = true` em leitura.
- **Senhas:** sempre `BCryptPasswordEncoder`. Nunca logar/serializar `senhaHash`.
- **Erros tipados:** lançar `BusinessException(msg, status)` / `NotFoundException` / `UnauthorizedException` (de `common-lib`). `GlobalExceptionHandler` traduz para `ErrorResponse` (timestamp, status, error, message, path). **Nunca** `catch (Exception e) {}` silencioso.
- **Param malformado → 400, nunca 500:** o `GlobalExceptionHandler` trata `MethodArgumentTypeMismatchException` (enum/número/data inválida em `@RequestParam`/`@PathVariable`). *(aprendizado Sprint 1+2: erro de input do cliente não pode virar 500)*
- **Rota inexistente → 404, nunca 500:** o `GlobalExceptionHandler` trata `NoResourceFoundException` (Spring 6.1 cai no resource handler ao não casar rota). Sem isso o catch-all `Exception` a transforma em 500. *(aprendizado Sprint 3, CR-S3-03 — generaliza a regra acima: nenhum caminho de cliente vira 500)*
- **Handlers de borda centralizados:** os handlers de input (400/404, `MethodArgumentTypeMismatch`, `HttpMessageNotReadable`) reincidem por serviço (CR-S3-03 → CR-S4-03 repetiu o 500 em query inválida). Extrair um `@RestControllerAdvice` **base no `common-lib`** que cada serviço estende, para não recriar (e esquecer). *(follow-up Sprint 4, CR-S4-03)*
- **Auth:** o serviço NÃO valida JWT (o gateway faz). Endpoints autenticados leem `@RequestHeader("X-User-Id") Long userId` (401 se ausente). `SecurityConfig` = csrf/cors off, STATELESS, `permitAll`.
- **Chamada cross-service usa só o canal interno** (`/internal/**` + `X-Internal-Token`), **nunca** endpoint público user-scoped (que exige `X-User-*` — indisponível service-to-service). O endpoint interno **não** lê `X-User-*`; valida o token na borda. O gateway não roteia `/api/internal/**`. *(aprendizado Sprint 3, BUG-S3-02 + ADR-T08)*
- **Segredo compartilhado: comparação constante-no-tempo** (`MessageDigest.isEqual(a.getBytes(UTF_8), b.getBytes(UTF_8))`, null-safe), **nunca** `String.equals` (curto-circuita → timing attack). *(aprendizado Sprint 3, CR-S3-05)*
- **Anti-enumeração:** login e forgot-password devolvem resposta genérica (não revelar se e-mail existe).

### Persistência (Postgres + Flyway + JPA)
- **`ddl-auto: validate`** — o schema é versionado por **Flyway**, nunca gerado por Hibernate. Toda mudança de schema = nova migration `V<n>__descricao.sql` (idempotente quando possível: `CREATE ... IF NOT EXISTS`).
- Colunas `snake_case`; entidades/DTOs `camelCase`. `@Enumerated(STRING)` para enums; espelhar com `CHECK` constraint no SQL.
- **Sem N+1:** usar `@EntityGraph`/`join fetch`/projeções. Toda query nova → pensar índice.
- **Banco por serviço:** nunca FK cross-service; referência é `BIGINT` simples. Validar existência via REST quando necessário.
- Migrations **revertíveis** mentalmente (documentar destrutivas em `data-model.md`). Prod usa `baseline-on-migrate`.

### Concorrência (§obrigatório declarar a estratégia)
| Cenário | Estratégia |
|---|---|
| Unicidade (dupla inscrição) | `UNIQUE` constraint + capturar `DataIntegrityViolationException` → 409 tipado |
| Capacidade/saldo | `UPDATE ... SET x = x - 1 WHERE id = ? AND x > 0` e checar `rowsAffected`; ou `@Version` (optimistic) |
| Ler-modificar-gravar | `@Lock(PESSIMISTIC_WRITE)` na query dentro de `@Transactional` |
| Serializar por chave | `pg_advisory_xact_lock(hashtext(:key))` |
A escolha vai no `backend-log.md`.

- **Hot path não relê o banco se o corpo não é consumido:** quando o cliente descarta a resposta (`toBodilessEntity`), não pague um `SELECT`/`findById` extra na linha mais contendida — use `UPDATE ... RETURNING` ou devolva sem reconsultar. *(aprendizado Sprint 3, CR-S3-02/03)*
- **Job/método com I/O remoto não segura transação:** um `@Scheduled`/serviço que faz chamada de rede (HTTP/AMQP) faz a mutação local em **tx curta** e o I/O **fora** dela — não segure conexão/lock de banco durante I/O de rede. Cuidado com **auto-invocação de proxy AOP**: chamar um método `@Transactional` de outro método **da mesma classe** não abre transação (extraia para outro bean ou use `TransactionTemplate`). *(aprendizado Sprint 4, CR-S4-02)*

### Mensageria (RabbitMQ)
- Produtor publica **após o commit** (`TransactionSynchronization.afterCommit`), nunca dentro da transação.
- Consumidor (`@RabbitListener`) é **idempotente**: checar/gravar `processed_events(event_id)` (at-least-once). Falha → DLQ.
- **Idempotência cobre o evento "tarde demais":** se o evento chega para um agregado em estado já avançado/inválido (ex.: `pagamento.aprovado` para inscrição `EXPIRADA`), o consumidor faz **ACK no-op** (o efeito não se aplica mais) — **nunca** deixa a transição de estado lançar, pois o rollback desfaz o `INSERT processed_events` → reentrega infinita → **poison message** parando na DLQ. Trate o estado inesperado explicitamente. *(aprendizado Sprint 4, CR-S4-01, P0)*
- Payload de evento é um `record` versionável; routing key `entidade.acao` (`pedido.criado`).

### Testes (back)
- JUnit 5 + AssertJ + Spring Boot Test. Integração com **H2** (`application-test.yml`, RabbitMQ excluído). TDD: teste vermelho → mínimo verde → refator.
- Cobertura ≥ 80% nos services críticos. **Teste de concorrência obrigatório** para mutação com risco (ex.: 2 inscrições simultâneas → 1 sucesso, 1 falha). Fixtures realistas.
- **Integração com Postgres/RabbitMQ reais via Testcontainers** (`@Testcontainers(disabledWithoutDocker=true)` — pula no Windows local, roda no CI). Regras que bateram 2× (S4 payment, 5A ticket):
  - **`TestcontainersBase` = SINGLETON** quando ≥1 classe a estende: containers `static` iniciados **manualmente** num bloco `static` guardado por `DockerClientFactory.instance().isDockerAvailable()`, **sem `@Container`**. Com `@Container static` numa base compartilhada por **2+ classes**, o extension PARA o container ao fim da 1ª classe → a 2ª não conecta/consome (HikariPool `total=0` ou listener em 30s timeout). *(S4 CR pós-merge; 5A CR-5A pós-CI)*
  - **`@BeforeEach` purga as filas** (`rabbitAdmin.purgeQueue(...)`) — contexto/broker são compartilhados (cacheados); mensagens de um teste vazam para o outro. *(S4 confirmar_2x)*
  - **Perfil de teste com broker NÃO exclui `RabbitAutoConfiguration`** (o listener/template precisam do broker real); os Postgres-only excluem via `@DynamicPropertySource` e mockam o publisher.
  - **`RabbitConfig` delega o `RabbitTemplate` ao autoconfigure** (sem bean próprio, sem `@ConditionalOnBean(ConnectionFactory.class)` em config de usuário — é avaliado antes do autoconfig e pula os beans). Declarar só o `MessageConverter` (com `JavaTimeModule`) + filas/exchanges/bindings.

---

## 2. Frontend — React 18 / Vite / TS

### Estrutura
```
frontend/src/
├── api/        # client.ts (axios + interceptor Bearer), <feature>.ts (funções tipadas)
├── components/ # ui/ (design system próprio: button, input, card, ...), layouts
├── hooks/      # useAuth, use<Feature>
├── lib/        # validation.ts (Zod), utils.ts
├── pages/      # uma por rota
└── routes/     # AppRoutes (ProtectedRoute, GuestOnly)
```

### Regras
- **TS strict, zero `any`.** Sem `console.log` em PR.
- **Formulários:** `react-hook-form` + `zodResolver`, schema em `lib/validation.ts`. Erro do back mapeado pro campo.
- **Datas:** `<input type="datetime-local">` gera valor **sem offset** — converter para ISO **com offset** (`new Date(v).toISOString()`) antes de enviar, em **formulários E filtros** (o back `OffsetDateTime` rejeita sem offset → 500). *(aprendizado Sprint 2, CR-S2-01)*
- **HTTP:** sempre via `api/client.ts` (axios, baseURL `VITE_API_URL`, interceptor injeta `Authorization: Bearer`). Sem `fetch` solto. Funções por feature em `api/<feature>.ts`, tipadas.
- **Token** no `localStorage['ticketeira.token']` (padrão atual do projeto). Sessão derivada via `useAuth`.
- **Estados de UI explícitos:** loading (spinner/skeleton sem layout shift), empty (com CTA), error (mensagem clara, nunca "algo deu errado" cru), success (toast `sonner`).
- **UI:** reusar o design system de `components/ui/` antes de criar. Tailwind (sem estilo inline). Acessibilidade: foco, `aria-label`, contraste. Mobile-first nas telas críticas.
- **Role-aware:** UI adapta por `papel`/`verificado` (ex.: CTA "Criar evento" só promotor verificado; card admin só ADMIN).
- **Máscaras** (CPF/telefone) via `imask`; validação espelha o back.

### Testes (front)
- Vitest + Testing Library. Por feature: render + happy path + 1 caminho de erro/validação. Sem testar implementação interna; testar comportamento.

---

## 3. Banco de Dados (PostgreSQL)

- Tabelas `snake_case` plural; colunas `snake_case`; PK `id BIGSERIAL`; timestamps `criado_em`/`atualizado_em TIMESTAMPTZ`.
- Invariantes do domínio como `CHECK` (defesa em profundidade): `nota BETWEEN 1 AND 5`, `capacidade > 0`, `preco >= 0`.
- Índice sempre que houver query nova; UNIQUE para regras de unicidade.
- Sem FK entre bancos de serviços diferentes.

---

## 4. Git & Commits (convenção do projeto)

### Branches
- `main` protegida.
- Sprint: `feat/sprint-<n>-<tema>` (ex.: `feat/sprint-1-eventos`).
- Estória/épico: `feat/sprint-<n>/<US-id>-<slug>` quando há paralelismo de devs.
- Fix: `fix/<slug>`. Hotfix: `hotfix/<slug>`.

> **1 estória = 1 dono.** Antes de criar a branch, **reivindique a estória** preenchendo a coluna *Dono* em [`memory/project/checklist-estorias.md`](../memory/project/checklist-estorias.md) — assim ninguém toca a mesma estória. O `<US-id>` na branch + no commit (abaixo) torna isso rastreável.

### Commits — **atômicos** e **Conventional Commits**
- Cada pequena unidade coesa = 1 commit. Não acumular feature inteira num commit gigante.
- Tipos: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`, `ci:`, `build:`, `perf:`.
- **Escopo = ID da estória** no trabalho de sprint: `tipo(US-xxx): assunto`. Ex.: `feat(US-031): inscricao com unique constraint`, `test(US-031): cenario de ultima vaga concorrente`, `fix(US-031): libera vaga quando inscricao falha`. Assim todo commit fica amarrado à estória — `git log --grep US-031` mostra tudo dela e fica claro quem está em quê (anti-duplicação). Para trabalho **transversal** (infra/pipeline/docs) use a área: `build:`, `ci:`, `docs(sdd): ...`.
- Subject ≤ 72 chars, imperativo, pt-BR ou en (escolha e mantenha).
- Corpo explica o **porquê** quando não óbvio.
- **NÃO usar trailer `Co-Authored-By`** (preferência explícita do dono do projeto).
- Nunca `--no-verify`. Nunca commitar `.env` (gitignored).

### Ordem típica de commits atômicos numa feature back
1. `test:` specs vermelhos (TDD) → 2. `feat:`/`build:` migration Flyway → 3. `feat:` entity + repository → 4. `feat:` service (verde) → 5. `feat:` controller + DTO → 6. `docs:` contrato/log. Cada um compila e (idealmente) passa o que já existe.

### PRs
- Título no padrão de commit. Descrição: o que muda, por quê, riscos, screenshots se UI, checklist de DoD.
- CI verde antes do merge. Aberto via `gh pr create` ao fim de `/validar-sprint`.

---

## 5. CI/CD (GitHub Actions)

- `backend.yml`: `./mvnw -B -ntp verify` (JDK 21, reactor inteiro) — path filter `pom.xml`, `shared/**`, `services/**`.
- `frontend.yml`: `npm ci && npm run build && npm run lint` (Node 20) — path filter `frontend/**`.
- DoD de CI: ambos **verdes** no último commit antes de abrir/mergear PR.
- **Wiring service-to-service explícito:** toda URL/`depends_on` entre serviços vai no `docker-compose`/env (o default `localhost` é armadilha dentro de container). Validar subindo o stack e exercendo o **caminho integrado** (não só `/health`) faz parte do DoD. *(aprendizado Sprint 3, BUG-S3-01 — `mvnw verify`/H2 não pega config de runtime; o smoke em Postgres real pega)*
- Deploy manual: Railway (backend, Dockerfile builder) + Vercel (frontend).

---

## 6. Definition of Done (resumo por camada)

**Backend feature:** testes antes do código · cobertura ≥80% no service · migration Flyway aplicável · `@Valid` na borda · header `X-User-*` lido onde autenticado · erro tipado · concorrência tratada · sem N+1 · OpenAPI atualizado · `handoff-frontend.md` se tem UI · `./mvnw verify` verde.

**Frontend tela:** loading/empty/error/success · critérios do PO atendidos · acessível · responsivo · tipos/validação alinhados ao back · sem `any`/`console.log` · `npm run build` + `test:run` verdes · `handoff-tester.md`.
