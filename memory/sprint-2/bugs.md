# Sprint 2 — Bug Report

> QA/Test Engineer Sênior. Data: 2026-06-26. Branch: `feat/sprint-2-eventos`.

---

## Nenhum P0 / Nenhum P1

Zero bugs de severidade P0 (bloqueante / dados corrompidos) ou P1 (critico / fluxo principal quebrado) foram encontrados na Sprint 2.

---

## BUG-001 — `Register.test.tsx` falha em "rejeita CPF sem mascara correta"

- **Severidade:** P3 (cosmético / nao bloqueia)
- **Origem:** Sprint 1 (pre-existente, nao introduzido pela Sprint 2)
- **Suite:** `src/pages/__tests__/Register.test.tsx`
- **Teste:** `rejeita CPF sem mascara correta`
- **Reproducao:** `npm run test:run` → 1 falha em Register.test.tsx
- **Causa raiz:** A aba "Promotor" do componente `Register` renderiza dois campos com label que casa `/^E-mail/i` (o campo de e-mail principal e o campo `emailContato`). O teste chama `screen.getByLabelText(emailLabel)` e recebe `TestingLibraryElementError: Found multiple elements with the text: E-mail` (dois elementos com o mesmo label).
- **Impacto:** Nenhum impacto na Sprint 2. As 4 telas de Eventos nao tocam o componente `Register`.
- **Status:** OPEN — aguardando Sprint 3 ou hotfix independente.
- **Owner:** Frontend Engineer.
- **Resolucao sugerida:** Diferenciar os labels (`"E-mail"` vs `"E-mail de contato"`) ou usar `getAllByLabelText` no teste + `[1]` para o segundo campo.

---

## Observacoes de qualidade (nao sao bugs)

### OBS-001 — Warning `--localstorage-file` no Vitest
- **Tipo:** Warning de ambiente de teste (jsdom)
- **Impacto:** Nenhum — apenas log de warning; todos os testes executam normalmente.
- **Origem:** `frontend/src/test/setup.ts` — polyfill de localStorage para jsdom sem caminho configurado.
- **Acao recomendada:** Configurar `environmentOptions.jsdom.storageQuota` no `vitest.config.ts` (ja iniciado no `frontend-log.md`). P4.

### OBS-002 — Warning chunk > 500kB no build de producao
- **Tipo:** Warning do Vite/Rollup
- **Impacto:** Nenhum em desenvolvimento/homolog. Em producao pode impactar o LCP em conexoes lentas.
- **Acao recomendada:** Code splitting via `import()` dinamico para `CriarEditarEvento` e `MeusEventos` (maiores). P4, pos-mvp.
