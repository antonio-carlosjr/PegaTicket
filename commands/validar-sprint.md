# Comando: validar-sprint `<n>`

> Runbook **versionado e tool-agnostic**. Validação final do sprint: roda todos os testes, faz code review profundo, corrige e abre o PR. Lógica de revisão em [`agents/revisor.md`](../agents/revisor.md).

**Executor:** orquestrador.
**Objetivo:** garantir qualidade (CI verde + code review sem P0/P1) e abrir o PR.
**Entrada:** número do sprint. Trabalha na branch `feat/sprint-<n>-<tema>`.
**Pré-requisitos:** sprint desenvolvido (`desenvolver-sprint`) e com aceite do PO (`po-acceptance.md`).
**Referências:** [`agents/revisor.md`](../agents/revisor.md), [`agents/devops.md`](../agents/devops.md), [`rules/coding-standards.md`](../rules/coding-standards.md), [`memory/project/decisions.md`](../memory/project/decisions.md) (dívidas de segurança).

## Regras
Revisor = `opus`; DevOps/Back/Front/Tester = `sonnet`. Nunca `haiku`. Commits atômicos, sem `Co-Authored-By`. PR só abre com tudo verde.

## Passos
1. **DevOps — CI local** (a verdade do GitHub Actions):
   ```
   ./mvnw -B -ntp verify
   cd frontend && npm ci && npm run build && npm run test:run && cd ..
   ```
   Vermelho → owner corrige **com teste de regressão** → DevOps `fix:` → repete. Não prossiga com CI vermelho.
2. **Revisor** (`opus`) — revisar `git diff main...feat/sprint-<n>-<tema>` caçando: **recursão infinita / sem caso base**; **race conditions** (inscrição, capacidade, emissão, pagamento; evento dentro de transação; consumidor AMQP não-idempotente); **O(n²) ou pior e N+1**; vazamento de recurso/transação; **dívidas de segurança** (endpoint ADMIN sem checagem, header forjável, segredo, dado sensível em log). Gera `memory/sprint-<n>/code-review.md` (achados P0..P3 com arquivo:linha, porquê, correção). Aplica P0/P1 óbvios com teste; devolve o resto ao owner. Loop até zerar P0/P1.
3. **Tester** — regressão final completa (back + front); `regression-report.md`; confirmar concorrência verde.
4. **DevOps — abrir PR:** reconfirmar CI verde; escrever `memory/sprint-<n>/pr-body.md` (o que muda, por quê, histórias aceitas, riscos, resultado do review, DoD); `git push -u origin feat/sprint-<n>-<tema>`; `gh pr create --base main --title "feat: Sprint <n> — <tema>" --body-file memory/sprint-<n>/pr-body.md`; registrar link em `devops-log.md`.
5. **Retrospectiva** — `retrospective.md`; promover recorrências de review para `coding-standards.md`/`decisions.md` e `memory/code-review/<sprint>.md`; `backlog.md` → DONE.

## Entrega
CI, resumo do `code-review.md`, link do PR, aprendizados promovidos a regra. **Não faça merge** — decisão humana.
