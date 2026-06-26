# Sprint 2 — Frontend Log

> Autor: Senior Frontend Engineer. Data: 2026-06-26.
> Fase 5 do /desenvolver-sprint 2: implementação das telas de Eventos.

---

## Arquivos criados / alterados

### Novos
- `frontend/src/api/events.ts` — funções tipadas para todos os 7 endpoints de eventos
- `frontend/src/components/PromotorRoute.tsx` — guarda de rota análogo ao AdminRoute
- `frontend/src/pages/Eventos.tsx` — lista pública paginada com filtros
- `frontend/src/pages/EventoDetalhe.tsx` — detalhe completo do evento
- `frontend/src/pages/MeusEventos.tsx` — painel do promotor (lista + ações)
- `frontend/src/pages/CriarEditarEvento.tsx` — wizard 3 etapas para criar/editar
- `frontend/src/pages/__tests__/Eventos.test.tsx`
- `frontend/src/pages/__tests__/EventoDetalhe.test.tsx`
- `frontend/src/pages/__tests__/MeusEventos.test.tsx`
- `frontend/src/pages/__tests__/CriarEditarEvento.test.tsx`

### Alterados
- `frontend/src/lib/validation.ts` — adicionado `eventoSchema`, `EventoFormValues`, `eventosFiltroPSchema`
- `frontend/src/routes/AppRoutes.tsx` — adicionadas rotas `/eventos`, `/eventos/:id`, `/meus-eventos`, `/eventos/novo`, `/eventos/:id/editar`
- `frontend/src/components/AppLayout.tsx` — itens "Eventos", "Meus eventos", "Criar evento" no nav
- `frontend/src/test/setup.ts` — polyfill localStorage para ambiente de teste com jsdom e --localstorage-file sem caminho
- `frontend/vitest.config.ts` — `environmentOptions.jsdom.storageQuota` para evitar warning

---

## Decisões de UX

### Wizard 3 etapas (CriarEditarEvento)
- Etapa 1: Título + Descrição
- Etapa 2: Data início / fim + Local
- Etapa 3: Tipo / Capacidade / Preço (só se PAGO) / Prazo de reembolso (só se PAGO) / URL imagem

**Não perde dados ao voltar**: `useForm` é único para todo o wizard; `trigger(campos)` valida apenas os campos da etapa corrente antes de avançar.

**Dados de datas**: `<input type="datetime-local">` retorna "YYYY-MM-DDTHH:mm" (sem timezone). A função `localParaIso()` converte via `new Date(local).toISOString()` — interpreta como hora local do usuário e serializa em UTC+offset. O backend recebe ISO-8601 completo.

### R1 — Editar só para RASCUNHO
`podeEditar = evento.status === 'RASCUNHO'` no `EventoCard`. Eventos publicados/cancelados/realizados não têm o link de edição.

### R3 — vagasDisponiveis null no RASCUNHO
`vagasTexto(null, status)`:
- `status === 'RASCUNHO'` → "Disponível após publicar"
- `vagas === null` → "—"
- `vagas === 0` → "Esgotado"
- default → "N vaga(s)"

Na tela `MeusEventos`, como o `EventoResumo` não retorna `vagasDisponiveis`, sempre chama `vagasTexto(null, evento.status)`. Eventos PUBLICADOS mostrarão "—" nesta tela (vagas só estão na response completa do detalhe). Isso é aceitável pois o detalhe (`GET /events/:id`) já mostra o valor correto.

### Timezone
Datas exibidas com `toLocaleString('pt-BR', { timeZoneName: 'short' })` — usa o fuso local do navegador automaticamente (conforme req. US-023.3).

### Button asChild evitado
`@radix-ui/react-slot@1.1.0` apresenta `React.Children.only` error no jsdom quando `Button asChild` contém `Link` com múltiplos filhos (ícone + texto). Solução: substituir por `Link` com `buttonVariants()` para links de navegação. `Button` sem `asChild` continua normal para ações (onClick). Nenhuma dependência nova adicionada.

### Sem botão de inscrição
Conforme critério US-023.4 / po-validation.md: a tela `EventoDetalhe` exibe apenas uma nota "Inscrições disponíveis em breve — Sprint 3."

---

## Resultado do build
```
npm run build → tsc -b && vite build
✓ zero erros de tipo
✓ built in ~2s
warning: chunk > 500kB (informativo — não bloqueia)
```

## Resultado dos testes
```
npm run test:run
Test Files: 1 failed | 10 passed (11)
Tests: 1 failed | 50 passed (51)
```

O único teste com falha é `Register.test.tsx > rejeita CPF sem mascara correta` — pré-existente do Sprint 1, não relacionado às telas de eventos. Causa: aba Promotor do Register renderiza dois campos com label /^E-mail/ (email principal + emailContato). Bug documentado aqui para correção futura.

Todos os 12 testes novos (Eventos, EventoDetalhe, MeusEventos, CriarEditarEvento) passam.
