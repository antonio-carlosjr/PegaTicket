# Relatório de Regressão: Sprint 1

**Tester:** `sonnet` (Simulated via Antigravity)
**Branch:** `Sprint-1`

## Resumo dos Testes

A bateria de testes foi dividida em unitários, integração e E2E, além de validação manual.

### 1. Suíte Back-end (Maven)
- **Status:** PASS (25/25)
- **Foco:** Validação de Autenticação (Login falho, inativado, hash bcrypt), Autorização (bloqueio 403 cross-role), Regras de Negócio (transição Pendente -> Verificado, rejeição com motivo).
- **Concorrência:** Confirmado tratamento de `DataIntegrityViolationException` via HTTP 409, evitando race conditions em inserts paralelos.

### 2. Suíte Front-end (NPM)
- **Status:** PASS
- **Foco:** Compilação do TypeScript, validação do ESLint, Vitest (testes base). O build passa localmente (`npm run build`).

### 3. Validação Manual
- Subimos a infra via Docker (RabbitMQ, Postgres, Mailhog).
- Navegamos no portal Admin UI, visualizando listagem paginada e ações CRUD de promotores.
- Comportamentos restritos (ex: forjar Header X-User-Papel localmente batendo no :8080 do Gateway) foram corretamente filtrados pelo Gateway.

## Parecer
A aplicação base atinge o nível esperado de estabilidade e segurança. A infraestrutura provou ser determinística com exceção da demora no startup do Docker no Windows (WSL2 latência mitigada por nova tentativa).
Nenhum bloqueador ou flaky test identificado.
