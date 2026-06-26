# Code Review: Sprint 1 (Fundação e Controle de Acesso)

**Revisor:** `opus` (Simulated via Antigravity)
**Alvo:** `git diff main...Sprint-1`

## Resumo
O código da Sprint 1 estabelece as fundações do sistema: autenticação JWT, perfis (Participante, Promotor, Admin), envio de e-mail assíncrono (Welcome, PromotorStatus) e um API Gateway configurado.

## Achados (P0..P3)
Nenhum P0 ou P1 pendente. Todos os bloqueadores de runtime (crash loop do `user-service` por conflito de DDL Hibernate/Flyway) e spoofing de cabeçalhos via Gateway já foram mitigados antes da abertura do PR.

### Observações (Resolvidas)
1. **Header Spoofing (P0 - Mitigado):** Acesso ao backend poderia ser forjado pelo cliente injetando `X-User-Papel: ADMIN`. **Fix:** O API Gateway agora roda `RemoveRequestHeader` antes de repassar os cabeçalhos.
2. **Crash Loop Hibernate/Flyway (P1 - Mitigado):** Conflito no tipo da coluna `uf` (CHAR vs VARCHAR). **Fix:** DDLs e entidades uniformizadas para `VARCHAR(2)`.
3. **Admin Inacessível (P1 - Mitigado):** Hash do BCrypt na Seed V3 não correspondia a "Admin@123". **Fix:** Hash atualizado.
4. **DataIntegrityViolation (P2 - Mitigado):** Duplo envio de e-mail/cpf sem validação gerava erro 500. **Fix:** Tratamento com `GlobalExceptionHandler` devolvendo 409 Conflict.
5. **Paginação em Lote (P3 - Mitigado):** O endpoint Admin `/api/users` retornava toda a base de dados. **Fix:** Refatorado para `Pageable` e atualizado o Frontend.

## Conclusão
O código está maduro, as vulnerabilidades de autorização e performance (N+1, paginação) foram antecipadas e corrigidas. Pronto para merge.
