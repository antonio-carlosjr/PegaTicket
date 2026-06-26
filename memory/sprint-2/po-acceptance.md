# Sprint 2 — Aceite do PO

> Product Owner encarnando Marina (promotora, 35) e Bruno (participante, 24).
> Data: 2026-06-26. Branch: `feat/sprint-2-eventos`.

---

| História | Veredicto | Cenário verificado | Motivo (se ❌) |
|---|---|---|---|
| **US-020** — Criar evento | ✅ | Marina preencheu o wizard em 3 etapas (dados gerais → data/local → tipo/preço/capacidade), salvou e o evento nasceu em RASCUNHO sem aparecer para Bruno — smoke #4-5. Bruno tentou `POST /api/events` e levou 403 antes de qualquer dado ser gravado — smoke #10. Campos obrigatórios ausentes barram com mensagem inline e sem perder os dados já digitados — `CriarEditarEvento.test.tsx` PASS. Evento PAGO com `preco=0` bloqueado em back+front — `EventControllerIntegrationTest` + validação front PASS. | — |
| **US-021** — Editar / Publicar / Cancelar | ✅ | Marina editou um RASCUNHO e as alterações persistiram com status intacto. Publicou e `vagasDisponiveis` inicializou igual à capacidade — smoke #6. Depois que publicou, Bruno viu o evento na lista — smoke #7. Marina tentou editar e publicar evento de outro promotor (promotor B → evento A) e recebeu 404 sem vazar a existência — smoke #11. Publicar evento CANCELADO → 409 `TRANSICAO_INVALIDA`; cancelar CANCELADO → 409 `EVENTO_JA_CANCELADO`. Marina cancelou um evento PUBLICADO e ele sumiu da lista de Bruno imediatamente — MockMvc e-to-e step 6-7. Participante com token adulterado (papel errado) → 403 em qualquer escrita. | — |
| **US-022** — Listar e buscar eventos | ✅ | Bruno abriu a lista e viu apenas eventos PUBLICADOS — RASCUNHO e CANCELADO excluídos em todos os testes e no smoke #5 e #7. Filtros `q` (texto), `tipo` (GRATUITO/PAGO) e intervalo `de`/`ate` funcionam isolados e combinados — `EventControllerIntegrationTest` PASS. Paginação retornou `content.size()==2`, `totalElements==5`, `totalPages==3` com 5 publicados e `size=2`. Filtro sem match retorna lista vazia (`totalElements==0`) com HTTP 200 — empty state visível no front (`Eventos.test.tsx` PASS). Após Marina cancelar, Bruno atualizou a lista sem logout e o evento sumiu — smoke #6-7. | — |
| **US-023** — Detalhe do evento | ✅ | Bruno clicou num evento PUBLICADO e viu título, descrição, datas formatadas, local, tipo, preço (ou "Gratuito"), capacidade total, `vagasDisponiveis` e `imagemUrl` — smoke #9 e `EventoDetalhe.test.tsx` PASS. Acessar detalhe de um RASCUNHO de outro promotor retornou 404 sem vazar existência — `EventControllerIntegrationTest` + smoke #11. Tela exibe estado de loading e erro amigável em falha de rede — `EventoDetalhe.test.tsx` PASS. Botão "Inscrever-se" **ausente** — detalhe somente leitura conforme escopo da Sprint 2. | — |

---

## Histórias devolvidas ao backlog

Nenhuma. Todas as quatro histórias da Sprint 2 atingiram os critérios de aceite operacionais.

---

## Observações do PO

### O que Marina diz

> "Consigo criar um evento em três telas, publicar e ver ele aparecer. Se cometi um erro no campo, a tela me avisa sem apagar o que eu já tinha digitado. E se alguém tentar mexer no meu evento, recebe 404 — minha oferta está protegida."

### O que Bruno diz

> "A lista traz só o que está aberto. Consigo filtrar por texto e tipo de evento. O detalhe me dá tudo que preciso para decidir se vou — data, local, preço. Não tem botão de inscrição ainda, mas o PO disse que vem na Sprint 3 e eu confio."

### Pendências de menor severidade (não bloqueiam aceite)

| ID | Descrição | Severidade | Sprint sugerida |
|---|---|---|---|
| BUG-001 | `Register.test.tsx` — "rejeita CPF sem máscara correta" falha por dois elementos com label `/^E-mail/i` | P3 (pré-existente Sprint 1) | Sprint 3 (hotfix) |
| OBS-001 | Warning `--localstorage-file` no Vitest | P4 | Sprint 3 / backlog |
| OBS-002 | Warning chunk > 500kB no build de produção | P4 | Pós-MVP |

Nenhuma delas é da Sprint 2 nem afeta os fluxos de Marina ou Bruno.

---

## Veredicto final

**SPRINT 2 APROVADA** — US-020, US-021, US-022, US-023 aceitas. O event-service saiu de stub para serviço real: promotora cria/edita/publica/cancela; participante lista, filtra, pagina e consulta detalhe. Zero P0/P1. Sprint 3 (inscrições) pode avançar.
