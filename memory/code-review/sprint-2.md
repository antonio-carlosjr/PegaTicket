# Aprendizados de Code Review — Sprint 2 (Eventos)

> Recorrências do review que viram **regra** em [`rules/coding-standards.md`](../../rules/coding-standards.md).

## R-S2-01 (Frontend) — `datetime-local` → ISO com offset
`<input type="datetime-local">` produz `YYYY-MM-DDTHH:mm` **sem offset**; o backend (`OffsetDateTime`) rejeita o bind → **500**. Converter **sempre** (helper `localParaIso` via `toISOString()`) antes de enviar — em **formulários E filtros**. *(Origem: CR-001, P1 — o filtro de data da lista pública quebrava.)*

## R-S2-02 (Backend) — type-mismatch de query param → 400
Todo serviço com `@RequestParam` tipado (enum/número/data) precisa de `@ExceptionHandler(MethodArgumentTypeMismatchException)` → **400** no `GlobalExceptionHandler`; senão `tipo=FOO`/id não-numérico/data inválida viram **500**. *(Origem: CR-002, P2 — mesma classe do "500 em vez de erro tipado" da Sprint 1.)*

## R-S2-03 (Backend) — listagem paginada precisa de `ORDER BY` determinístico
`Page<>` sem `ORDER BY` estável → paginação instável (mesmo item pode aparecer em 2 páginas). Definir sort default no repositório/serviço. *(Origem: CR-006, P3 — pendente, devolvido ao owner.)*

## Diferido (não vira regra agora)
- Concorrência de transição de estado sem `@Version` → tratado na **Sprint 3** (ADR-T07, reserva atômica de vaga).
- `imagemUrl` como `<img src>` sem allowlist de esquema (CR-005, P3) → endurecer quando houver upload/render de imagem de terceiros.
