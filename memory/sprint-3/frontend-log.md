# Sprint 3 — Frontend Log

## Decisões de implementação

### Dependência nova: `qrcode.react`
- **Pacote:** `qrcode.react@^4.x`
- **Justificativa:** O contrato `api-contracts.md` §QR define explicitamente que "o front renderiza QR a partir de `codigoUnico`; nunca pedir imagem ao backend". É necessária uma lib de QR client-side. `qrcode.react` é a mais adotada no ecossistema React, mantida ativamente, e expõe `QRCodeSVG` (SVG nativo, sem dependência de canvas — compatível com SSR e telas de acessibilidade). Alternativas descartadas: `react-qr-code` (menos features), `qrious` (não-React), gerar imagem no back (violaria o contrato ADR-T09).
- **Uso:** `import { QRCodeSVG } from 'qrcode.react'` — `size={180-200}`, `level="M"` (ECC nível médio, boa legibilidade em 5").

### `api/tickets.ts` — padrão de tipos
- Todos os tipos são `interface` sem `any`. `OffsetDateTime` do Java chega como string ISO-8601 — tratado como `string` no TS (exibição via `toLocaleString('pt-BR', { ... })`).
- `Page<T>` reutilizado de `api/events.ts` (importado via `import type`).

### `EventoDetalhe.tsx` — extensão
- Adicionado estado `inscricao: InscricaoResponse | null` separado do estado do evento para não misturar concerns.
- Botão "Inscrever-se" condicional: `tipo === 'GRATUITO' && status === 'PUBLICADO' && (vagasDisponiveis === null || vagasDisponiveis > 0)`. Vagas nulas são tratadas como disponíveis (RASCUNHO nunca chega aqui pois status !== PUBLICADO).
- Mapeamento de erros por código semântico (`JA_INSCRITO`, `EVENTO_ESGOTADO`, etc.) — `mensagemErroInscricao()` separado para testabilidade.
- Após inscrição: card inline com QR + botão para `/meus-ingressos`. **Não** navega automaticamente (o usuário pode querer ver o QR na própria tela de detalhe).

### `MeusIngressos.tsx` — composição de dados
- `GET /tickets/me` retorna apenas `eventoId`. Conforme decisão do Arquiteto (anti N+1 cross-service), o front compõe chamando `detalheEvento(eventoId)` por ingresso em paralelo via `Promise.all`.
- Falha de um evento individual não trava a lista inteira — `erroEvento: boolean` por item, exibe fallback `"Evento #${id}"` com nota de aviso.
- Estado vazio: "Você ainda não se inscreveu em nenhum evento — Que tal explorar os eventos disponíveis?" com CTA para `/eventos`.
- QR renderizado via `QRCodeSVG` com `size={180}` — legível em mobile 5".

### `MinhasInscricoes.tsx` — histórico paginado
- Chama `GET /tickets/inscricoes/me?page=p&size=20&sort=inscritoEm,desc`.
- Estado de loading por página (botões Anterior/Próxima desabilitados durante fetch).
- Sem chamada ao event-service (o histórico não exibe nome do evento nesta sprint — decisão de escopo, seria N+1 adicional para uma tela secundária).

### `AppLayout.tsx` — nav
- Adicionado link "Meus ingressos" (com ícone `Ticket`) visível para qualquer usuário autenticado, à direita de "Eventos". Em mobile (`sm:hidden`) mostra só o ícone; desktop mostra texto.

### Testes
- Padrão: `renderWithProviders` (de `src/test/utils.tsx`) + mocks via `vi.mock`.
- `MeusIngressos.test.tsx`: 3 testes (happy path, vazio, event-service falhando parcialmente).
- `MinhasInscricoes.test.tsx`: 2 testes (happy path, vazio).
- `EventoDetalhe.test.tsx`: estendido com 3 novos (botão aparece GRATUITO/PUBLICADO, botão não aparece PAGO, happy path inscrição + QR).
- Falha pré-existente `Register.test.tsx` (Sprint 1 — CPF mask) mantida conforme instrução.

## Resultado do build

```
npm run build → tsc -b && vite build
✓ 1741 modules transformed.
✓ built in ~2.1s
Zero erros de tipo TypeScript.
```

## Resultado dos testes

```
Test Files: 1 failed (Register.test.tsx — falha pré-existente Sprint 1) | 12 passed (13)
Tests: 1 failed | 59 passed (60)
```
