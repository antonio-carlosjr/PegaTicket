# Sprint 3 — Code Review (adversarial)

> Revisor (Principal Engineer). Diff revisado: `git diff main...feat/sprint-3-inscricao-ingresso`.
> Foco: código de produção (event-service, ticket-service, frontend, docker-compose).
> Os 3 bugs P1 da validação (BUG-S3-01/02/03) já estão corrigidos e **não** são re-reportados.
> Dívidas já aceitas em `decisions.md` (ADR-T05 seed admin, ADR-T08 sem mTLS, idempotência imperfeita de `liberar-vaga`) **não** são contadas como achados novos.

## Resumo executivo

- **Arquivos de produção revisados:** ~22 (back: `InscricaoService`, `EventClient`, `EventClientConfig`, `InternalEventController`, `EventService`, `EventRepository`, entidades/DTOs/handlers; front: `tickets.ts`, `EventoDetalhe`, `MeusIngressos`, `MinhasInscricoes`, `AppLayout`, `AppRoutes`; infra: `docker-compose.yml`, `application*.yml`).
- **Achados:** **P0 = 0 · P1 = 0 · P2 = 5 · P3 = 5.**
- **O coração da sprint (abre-vendas concorrente) está correto.** Decremento atômico `UPDATE ... WHERE vagas>0` + `rowsAffected`, `UNIQUE` + captura de `DataIntegrityViolationException`, saga validar→pré-check→reservar→tx local→compensar, e a tx local via `TransactionTemplate(REQUIRES_NEW)` fora do escopo do catch da compensação — tudo implementado conforme ADR-T07/T08/T09 e coberto por teste (incl. Testcontainers em Postgres real). **Nenhuma race, recursão, N+1 de banco ou vazamento de transação encontrado.**
- **Veredito: PRONTO PARA PR.** Zero P0/P1. Os P2 são melhorias de robustez/hygiene recomendadas (não bloqueiam); os P3 são nits.

---

## P0 — Bloqueadores

Nenhum.

## P1 — Importantes (bloqueariam)

Nenhum.

---

## P2 — Melhorias recomendadas (não bloqueiam o PR)

### CR-S3-01 — `EventClient.getEvento` colapsa 422/403 em 503, e a tradução de erro não bate com o contrato/arquitetura
- **Local:** `services/ticket-service/.../client/EventClient.java:38-46`
- **Problema:** `getEvento()` só trata 404 explicitamente; **qualquer outro 4xx** vira `EVENTO_INDISPONIVEL` (503). A arquitetura (`api-contracts.md` §4 e `architecture.md` §EventClient) e o contrato dizem que a leitura interna pode responder **403 `ACESSO_INTERNO_NEGADO`** (token errado). Hoje, um 403 (token interno divergente entre serviços — exatamente o cenário de mis-config que o `.env.example` não documenta, ver CR-S3-04) é apresentado ao usuário como "serviço indisponível", escondendo um defeito de configuração de infra que deveria gritar nos logs como erro de autorização interna. Funciona "por acidente" porque o status PUBLICADO/GRATUITO é re-checado no `reservarVaga`, mas o diagnóstico fica obscurecido.
- **Por que é P2 e não P1:** o caminho-feliz e os caminhos de erro de negócio (404/esgotado/não-publicado) estão corretos; o impacto é **observabilidade/diagnóstico** num cenário de mis-config, não correção funcional do usuário final.
- **Correção sugerida:** logar o status real e distinguir 403:
```java
.onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
    int sc = resp.getStatusCode().value();
    if (sc == 404) throw new NotFoundException("EVENTO_NAO_ENCONTRADO");
    if (sc == 403) {
        log.error("X-Internal-Token rejeitado pelo event-service (403) ao validar evento {} — checar INTERNAL_TOKEN dos dois servicos", eventoId);
        throw new BusinessException("EVENTO_INDISPONIVEL", 503);
    }
    log.warn("event-service respondeu {} ao validar evento {}", sc, eventoId);
    throw new BusinessException("EVENTO_INDISPONIVEL", 503);
})
```

### CR-S3-02 — `EventService.reservarVaga` faz um `findById` extra no caminho QUENTE (trabalho desperdiçado no ponto mais contendido)
- **Local:** `services/event-service/.../service/EventService.java:122-128`
- **Problema:** no caminho `rowsAffected == 1` (a reserva bem-sucedida — o caminho **mais frequente e mais quente** do abre-vendas), o service executa um `findById` adicional só para preencher `ReservaResponse.vagasDisponiveis`. Porém o **ticket-service descarta** esse corpo: `EventClient.reservarVaga()` chama `.toBodilessEntity()` e retorna `void`. Ou seja: na linha mais contendida do sistema, há um `SELECT` extra por inscrição cujo resultado nunca é usado. A arquitetura (`api-contracts.md` §4) é explícita: *"O caminho quente (rowsAffected=1) **não** toca o banco de novo."* — o código viola a decisão registrada. (Em alta contenção, o `SELECT` extra ainda incorre em I/O e contenção de pool, mesmo sem lock.)
- **Correção sugerida:** não reler no caminho quente. Como o consumidor não usa o valor, devolver um corpo sem reconsultar (ou mudar a query para `... RETURNING vagas_disponiveis` se quiser o valor sem 2º round-trip). Mínimo:
```java
if (rows == 1) {
    return new ReservaResponse(eventoId, null); // corpo não é consumido pelo ticket-service
}
```
Se preferir manter o campo populado para futuros consumidores, usar uma query nativa com `RETURNING` em vez de `findById`.

### CR-S3-03 — `liberarVaga` (compensação) também faz `findById` desnecessário e pode lançar 404 espúrio
- **Local:** `services/event-service/.../service/EventService.java:143-147`
- **Problema:** `liberarVaga` faz `incrementarVaga` (no-op idempotente, ok) e depois `findById(...).orElseThrow(404)`. (a) O `ReservaResponse` retornado também é descartado pelo `EventClient.liberarVaga()` (`.toBodilessEntity()`), então o `findById` é trabalho desperdiçado na compensação. (b) Mais relevante: se o evento foi **deletado** entre a reserva e a compensação, `liberarVaga` lança 404 → `EventClient.liberarVaga` mapeia `isError`→`COMPENSACAO_FALHOU` (503), que é capturado e logado como "[RECONCILIACAO] vaga presa". Mas se o evento não existe mais, **não há vaga a reconciliar** — é um falso-positivo de reconciliação. O contrato (`api-contracts.md` §5) define 404 só "se o evento sumiu", mas na prática da compensação isso polui o log de reconciliação com um caso inócuo.
- **Correção sugerida:** na compensação, não relançar 404; o objetivo é restaurar a vaga, e "evento sumiu" significa "nada a restaurar". Retornar no-op:
```java
@Transactional
public ReservaResponse liberarVaga(Long eventoId) {
    eventRepository.incrementarVaga(eventoId);
    return new ReservaResponse(eventoId, null); // corpo não consumido; sem findById
}
```

### CR-S3-04 — `INTERNAL_TOKEN` ausente do `.env.example` (ADR-T08 exige placeholder)
- **Local:** `.env.example` (raiz) — só contém `JWT_SECRET`.
- **Problema:** ADR-T08 e `architecture.md` §Autorização inter-serviço dizem explicitamente que o segredo interno vai *"em `.env`/`.env.example` com placeholder"*. O `.env.example` **não** foi atualizado nesta sprint. Consequência prática: quem clona o repo e sobe sem definir `INTERNAL_TOKEN` cai no default `dev-internal-secret` (hardcoded nos dois `application.yml` e no compose) — que funciona em dev mas é um segredo conhecido. Sem o placeholder no `.env.example`, não há sinalização de que esse valor **precisa** ser sobrescrito em produção (Railway), o que é justamente a ressalva de segurança do `po-acceptance.md`.
- **Correção sugerida:** adicionar ao `.env.example`:
```
# Segredo compartilhado dos endpoints internos (/internal/**) entre ticket-service e event-service.
# Gerar valor aleatório >= 32 chars antes de produção; deve ser idêntico nos dois serviços.
INTERNAL_TOKEN=troque-este-segredo-interno-por-valor-aleatorio
```

### CR-S3-05 — Comparação do `X-Internal-Token` não é timing-safe (replicada em 3 handlers)
- **Local:** `services/event-service/.../controller/InternalEventController.java:43, 56, 70` (`internalToken.equals(token)`).
- **Problema:** a verificação do segredo usa `String.equals`, que **curto-circuita no primeiro byte divergente** — teoricamente vulnerável a timing attack para recuperar o segredo byte-a-byte. Em rede interna de container o vetor é fraco, e ADR-T08 aceita "sem mTLS no MVP", mas a comparação **constante-no-tempo** é trivial e é a postura correta para qualquer segredo. Além disso, a checagem está **triplicada** (3 endpoints copiam o mesmo `if`), violando o "3 ocorrências → extrair" do coding-standards §0.4: já são 3, candidato a um `OncePerRequestFilter` (que a própria arquitetura sugeriu como alternativa).
- **Correção sugerida:** `java.security.MessageDigest.isEqual(a.getBytes(UTF_8), b.getBytes(UTF_8))` (constante no tempo, null-safe via guarda), e idealmente extrair para um filtro único sobre `/internal/**`:
```java
private boolean tokenValido(String token) {
    return token != null &&
        MessageDigest.isEqual(internalToken.getBytes(UTF_8), token.getBytes(UTF_8));
}
```

---

## P3 — Nits

### CR-S3-06 — `getEvento` revalida status/tipo que o `reservar-vaga` já garante (validação redundante, mas com benefício de UX)
- **Local:** `InscricaoService.java:74-81`.
- **Observação:** o pré-check de `status`/`tipo` em `getEvento` é tecnicamente redundante com a cláusula atômica `WHERE status=PUBLICADO` do `reservarVaga`. **Não é bug** — é necessário para distinguir `EVENTO_PAGO_NAO_SUPORTADO` (que o `reservar-vaga` não distingue) e evita gastar/compensar uma vaga no caminho comum de erro. Mantido como está; registrado só para deixar claro que a redundância é intencional (não remover achando que é morto).

### CR-S3-07 — `MeusIngressos`: fan-out de N chamadas REST ao event-service (`detalheEvento` por ingresso)
- **Local:** `frontend/src/pages/MeusIngressos.tsx:65-75`.
- **Problema:** `Promise.all(ingressos.map(detalheEvento))` faz **uma chamada por ingresso** ao `GET /api/events/{id}`. É exatamente o fan-out N+1 cross-service que a arquitetura quis evitar no backend — e foi *empurrado* para o front como decisão consciente (`architecture.md` §Performance: "o front compõe"). Para poucos ingressos é aceitável; cresce linearmente. **Não bloqueia** (decisão registrada), mas: (a) não há deduplicação — se o usuário tem 3 ingressos do mesmo evento, são 3 GETs idênticos; (b) sem cache entre montagens. Anotar como dívida para um endpoint batch no event-service quando houver 3ª ocorrência (coerente com a própria nota da arquitetura).
- **Sugestão leve agora:** deduplicar por `eventoId` antes do fan-out (`new Set`), reduzindo chamadas sem novo endpoint.

### CR-S3-08 — `EventoDetalhe`: após inscrição, `vagasDisponiveis` exibido fica stale
- **Local:** `frontend/src/pages/EventoDetalhe.tsx:117-121`.
- **Problema:** ao inscrever com sucesso, mostra-se o ingresso (correto), mas o card de "Vagas" continua exibindo o valor pré-inscrição (não há refetch do evento). Cosmético — o usuário acabou de consumir 1 vaga e o número não reflete. Não afeta a lógica de capacidade (que é server-side).
- **Sugestão:** atualizar `evento.vagasDisponiveis` localmente (`-1`) ou refetch após o 201.

### CR-S3-09 — `EventResumo`/`EventClientConfig`: `@Value`/javadoc desatualizados (referência ao endpoint público antigo)
- **Local:** `client/EventResumo.java:5` (javadoc diz "retornado pelo event-service (GET /events/{id})") e `EventClient.java:7` (`import ...Value` não usado).
- **Problema:** (a) o javadoc do `EventResumo` ainda cita o endpoint público `GET /events/{id}`, mas após BUG-S3-02 a leitura passou para `GET /internal/events/{id}` — comentário "do quê" desatualizado (coding-standards §0.2). (b) `EventClient.java` importa `org.springframework.beans.factory.annotation.Value` sem usar (import morto — §0.3). Nits de limpeza.
- **Correção:** atualizar o javadoc para `/internal/events/{id}` e remover o import não usado.

### CR-S3-10 — Telas de listagem: ausência de `useCallback`/cancelamento e `window.location.reload()` como retry
- **Local:** `MeusIngressos.tsx:101` (`window.location.reload()` no botão "Tentar novamente").
- **Problema:** o retry recarrega a página inteira em vez de re-disparar o fetch (`MinhasInscricoes` faz o certo com `carregar(page)`). Funciona, mas é um recarregamento completo desnecessário e perde estado de navegação. Nit de consistência entre as duas telas.
- **Sugestão:** extrair o efeito para uma função `carregar()` e chamá-la no botão, como em `MinhasInscricoes`.

---

## Confirmado correto (dá confiança ao gate)

1. **Decremento atômico anti-overbooking — sólido.** `EventRepository.decrementarVaga` é `UPDATE ... WHERE id=:id AND status=PUBLICADO AND vagasDisponiveis>0` com `int rowsAffected`. Row lock do Postgres serializa concorrentes; **sem janela** check-then-act. Coberto por `VagaConcorrenciaTest` (K=50 → 1 sucesso; N=20/K=100 → 20 sucessos; vagas nunca <0) em **Postgres real** (Testcontainers). `@Modifying(clearAutomatically=true)` está correto e o backend-log documenta por que (cache L1 stale). Conforme ADR-T07.
2. **`liberar-vaga` limitado pela capacidade** (`WHERE vagasDisponiveis < capacidade`) — não estoura o teto, no-op idempotente. A imperfeição de idempotência por reserva individual está **aceita e documentada** em ADR-T07 (não é achado novo).
3. **Unicidade da inscrição (dupla inscrição) — correta.** `UNIQUE(usuario_id,evento_id)` é a verdade; o pré-check (passo 1.5) é só otimização. A corrida real (2 reqs do mesmo usuário passam o `exists`) é resolvida no INSERT pela constraint → `DataIntegrityViolationException` capturada → compensa a vaga → 409 `JA_INSCRITO`. Testado em `InscricaoServiceTest.inscrever_uniqueVioladoNaTxLocal_compensa_lanca409` e no concorrente.
4. **Transação local via `TransactionTemplate(REQUIRES_NEW)` fora do catch — correto e bem justificado.** `inscrever()` não é `@Transactional` de propósito: a `DataIntegrityViolationException` só materializa no flush/commit; encerrar a tx **antes** do catch permite a compensação rodar fora de um contexto em rollback. Sem isso, a compensação ficaria presa num rollback. (backend-log §2.) Não há HTTP remoto dentro da `@Transactional` — o anti-pattern "conexão de pool segura durante chamada REST" **não** ocorre: `reservarVaga` (REST) acontece **antes** de abrir a tx local; a tx local só faz 2 INSERTs e fecha. **Sem vazamento de conexão/transação.**
5. **Sem retry do `reservar-vaga`** (não-idempotente) — timeout vira 503 e erra para menos, nunca overbooking. Timeouts configurados (connect 2s / read 3s) em `EventClientConfig`. Conforme arquitetura §timeouts.
6. **Compensação que também falha** é capturada e logada como `[RECONCILIACAO]` ERROR sem mascarar a exceção original ao cliente (`InscricaoService.compensar` + teste `inscrever_compensacaoTambemFalha_...`). Correto.
7. **Erros nunca viram 500 silencioso.** `EventClient` traduz todos os status do event-service em exceções tipadas via `onStatus`; `ResourceAccessException` (timeout/conn refused) → 503. `GlobalExceptionHandler` (ambos os serviços) cobre `BusinessException`, validação→400, type-mismatch→400, `HttpMessageNotReadable`→400, `DataIntegrityViolation`→409, `NoResourceFound`→404, genérico→500. Nenhum `catch (Exception){}` mudo (o catch genérico do saga **remapeia + loga + compensa**, não engole).
8. **Segurança do canal interno.** `/internal/**` exige `X-Internal-Token` (403 se ausente/errado); o controller **não** lê `X-User-*` (correto — internos não confiam em header de usuário). O gateway não roteia `/api/internal/**` (404). O `X-Internal-Token` **não** é logado em lugar nenhum. Defesa em duas camadas conforme ADR-T08. (Ressalva timing-safe → CR-S3-05.)
9. **`resumoInterno` sem checagem de ownership é correto** — a autorização é o token na borda; um read interno não deve aplicar regra de usuário (foi a causa-raiz de BUG-S3-02, agora resolvida).
10. **Sem N+1 de banco em `GET /tickets/me`.** `IngressoRepository.findIngressoComInscricaoByUsuarioId` faz **um** join `ingressos ⨝ inscricoes` retornando `[Ingresso, Inscricao]`; `idx_inscricoes_usuario` (V1) cobre o filtro. `inscricaoId` mapeado como `Long` (não `@OneToOne`) evita lazy-loading. Histórico é paginado (`size` cap em 100). Tudo O(1)/O(n) sem fan-out de banco.
11. **Timezone:** `ticket-service/application.yml` agora tem `hibernate.jdbc.time_zone: UTC` (gap da §5 do data-model fechado). `OffsetDateTime` em todas as bordas.
12. **Mapeamento `@Entity` 1:1 com V1** (tipos/length/unique conferidos contra o DDL); `ddl-auto: validate` em Postgres real passou no smoke. Sem migration nova (correto — V1 basta).
13. **Frontend — sem `any`, sem `console.log`, datas com `OffsetDateTime`/offset.** QR renderizado **no front** a partir de `codigoUnico` via `qrcode.react` (nunca pede imagem ao backend — ADR-T09). Estados de UI loading/empty/error/success presentes nas 3 telas. Botão "Inscrever-se" só para `GRATUITO` + `PUBLICADO` + vagas>0; PAGO mostra "em breve" sem POST. Mapeamento de erro por **código semântico** (`startsWith('JA_INSCRITO')` etc.), não por texto. Rotas `/meus-ingressos` e `/minhas-inscricoes` sob `ProtectedRoute` (autenticado). **Retry seguro:** `handleInscrever` não re-POSTa em loop; em erro mostra toast e o usuário decide (sem re-POST cego). Sem recursão/loop infinito.
14. **Sem recursão** em nenhum ponto do diff (back ou front). Sem O(n²) em dados de domínio.

---

## Recorrências a promover para `coding-standards.md` (via `/validar-sprint`)

Estas já vinham de `bugs.md`/`po-acceptance.md` e este review confirma que devem virar regra:

1. **Backend — rota inexistente → 404** via `@ExceptionHandler(NoResourceFoundException.class)`, nunca 500. Generaliza R-S2-02 (input do cliente nunca vira 500). Já aplicado nos 3 serviços; formalizar na §1 (Erros tipados).
2. **Arquitetura — leitura/escrita cross-service usa só o canal interno** (`/internal/**` + `X-Internal-Token`), nunca endpoints públicos user-scoped (que exigem `X-User-*`). Causa-raiz de BUG-S3-02. Promover à §1 (Banco por serviço / validação via REST).
3. **DevOps — toda URL/`depends_on` service-to-service explícita no compose**; o default `localhost` é armadilha em container. Subir o stack e exercer o **caminho integrado** (não só `/health`) faz parte do DoD. Causa-raiz de BUG-S3-01.
4. **Segurança — comparação de segredo compartilhado deve ser constante-no-tempo** (`MessageDigest.isEqual`), nunca `String.equals` (CR-S3-05). Candidato a nova linha na §1 (Auth/segredos) — recorrência provável quando o caminho pago (Sprint 4) adicionar mais segredos inter-serviço.
5. **Backend — não reler o banco no caminho quente quando o corpo não é consumido** (CR-S3-02/03): se o consumidor descarta a resposta (`toBodilessEntity`), não pague um `SELECT` extra na linha mais contendida. Reforça a §1 (sem trabalho desnecessário em hot path) / §0.5.
