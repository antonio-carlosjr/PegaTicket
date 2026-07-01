# Sprint 5 · Trilha 5B — Bugs

## P0 / P1 remanescentes (bloqueiam aceite)
_Nenhum na suíte executável localmente (reactor `verify` verde + frontend 97/97 + regressão S2–5A intacta)._

## Corrigidos na Fase 5 (caçados na verificação local, antes do commit da implementação)
Registrados para o code review do `/validar-sprint 5b` (viraram invariantes).

1. **[P1 · context-load] Publishers do ticket com `RabbitTemplate` obrigatório derrubavam o contexto sob perfil `test` (H2).**
   `InscricaoCanceladaPublisher` (novo) e `PedidoCriadoPublisher` (S4) faziam constructor-injection de `RabbitTemplate`. Sob `test` a `RabbitAutoConfiguration` é excluída (sem broker) → `NoSuchBeanDefinitionException` → **38 erros** de `@SpringBootTest` que não mockavam o publisher (cadeia `cancelamentoController → cancelamentoInscricaoService → inscricaoCanceladaPublisher`).
   **Fix:** injeção **opcional** via `@Autowired(required=false) setRabbitTemplate(...)` + guarda null (no-op) em `publicar()` — espelha `PagamentoAprovadoPublisher` do payment. Invariante: nenhum `@SpringBootTest` precisa mockar publisher só para subir o contexto; com broker (test-postgres/prod) o setter injeta e publica normalmente.

2. **[P2 · teste] `CancelamentoControllerTest` era `@Transactional` mas o service commita em REQUIRES_NEW → seed invisível → 409 em vez de 200.**
   O seed `save()` roda na transação (não commitada) do teste; `CancelamentoInscricaoService.cancelar()` abre `TransactionTemplate` REQUIRES_NEW, que sob READ_COMMITTED não enxerga o INSERT não-commitado → `cancelarPorParticipante` retorna 0 linhas → `INSCRICAO_JA_CANCELADA` 409. Falharia igual no Postgres do CI — defeito do teste, não da produção.
   **Fix:** removido `@Transactional` do teste (o `@BeforeEach limpar()` com `deleteAll` garante isolamento); o seed passa a commitar e a tx REQUIRES_NEW o enxerga. Alinha ao padrão dos testes de integração com commit próprio (S4 `PagamentoAprovadoListenerIntegrationTest`).

3. **[P2 · borda] `GET /internal/tickets/participou` sem `usuarioId` retornava 500 em vez de 400.**
   `@RequestParam Long usuarioId` ausente → `MissingServletRequestParameterException`, sem handler dedicado → caía no catch-all `Exception` → 500. (O caso não-numérico já era 400 via `MethodArgumentTypeMismatchException`.)
   **Fix:** `@ExceptionHandler(MissingServletRequestParameterException.class)` no `GlobalExceptionHandler` → 400. Contrato do canal interno: **nunca 500** em parâmetro faltando/ inválido.

## Aguardando validação no CI (Testcontainers — não roda local)
Se algum falhar no `/validar-sprint 5b` vira P0 e entra no loop:
- Check-in/cancelamento concorrentes (unicidade + row lock) · reembolso individual idempotente · corrida reembolso individual-vs-massa · afterCommit publica só pós-commit · elegibilidade `participou`.
- **Atenção especial** (lição S4/5A): fiação AMQP/perfil de teste e o novo `TicketClient` outbound do event-service são os pontos mais prováveis de exigir ajuste no CI.

## Notas / follow-ups (não bloqueiam)
- `EventResumo` (client do ticket) ganhou `dataInicio`/`prazoReembolsoDias`; **9 fixtures S4** foram ajustadas para a nova aridade — comportamento externo idêntico, revisar no code review.
- `TicketClient` (event → ticket) é **fail-closed**: indisponibilidade do ticket → 503 na avaliação (não grava avaliação sem confirmar elegibilidade).
