# Sprint 3 — Handoff Tester (Frontend)

## Telas entregues

| Rota | Componente | Papel |
|---|---|---|
| `/eventos/:id` | `EventoDetalhe.tsx` (estendido) | Qualquer autenticado |
| `/meus-ingressos` | `MeusIngressos.tsx` (nova) | Qualquer autenticado |
| `/minhas-inscricoes` | `MinhasInscricoes.tsx` (nova) | Qualquer autenticado |

Nav: item "Meus ingressos" (ícone Ticket) adicionado em `AppLayout` para todos os autenticados.

---

## Roteiro de teste happy path (Bruno — participante)

### 1. Inscrição em evento gratuito publicado

1. Login como Bruno (PARTICIPANTE).
2. Navegar para `/eventos` → abrir um evento com tipo **GRATUITO** e status **PUBLICADO**.
3. Verificar que:
   - Badge "Gratuito" e "Publicado" estão visíveis.
   - Campo "Vagas" exibe o valor real de `vagasDisponiveis` (ex.: "50 vagas disponíveis").
   - Botão **"Inscrever-se"** está visível e habilitado.
   - Texto abaixo do botão exibe as vagas disponíveis.
4. Clicar em "Inscrever-se".
5. Verificar que:
   - O botão muda para "Inscrevendo..." durante o POST.
   - Após resposta 201: card "Inscrição confirmada!" aparece com o QR renderizado.
   - Toast de sucesso aparece no canto superior direito.
   - O botão "Inscrever-se" desaparece (substituído pelo card do QR).
   - Botão "Ver todos os meus ingressos" navega para `/meus-ingressos`.

### 2. Evento PAGO — sem botão de inscrição

1. Abrir um evento com tipo **PAGO** e status **PUBLICADO**.
2. Verificar que:
   - Botão "Inscrever-se" **não aparece**.
   - Texto "Inscrições pagas disponíveis em breve." está visível.

### 3. Evento GRATUITO esgotado (vagas = 0)

1. Abrir um evento GRATUITO/PUBLICADO com `vagasDisponiveis = 0`.
2. Verificar que:
   - Botão "Inscrever-se" **não aparece**.
   - Mensagem "Não há mais vagas disponíveis para este evento." é exibida.

### 4. Tentativa de dupla inscrição

1. Estando já inscrito no evento, tentar clicar "Inscrever-se" novamente (se ainda visível).
2. Back retorna 409 `JA_INSCRITO`.
3. Verificar que toast de erro exibe: "Você já está inscrito neste evento."

### 5. Tela "Meus ingressos" (`/meus-ingressos`)

1. Navegar para "Meus ingressos" (link na nav ou botão pós-inscrição).
2. Verificar que:
   - Lista todos os ingressos com card por ingresso.
   - Cada card exibe: nome do evento, data, local e QR code (SVG).
   - QR é legível (tamanho 180px, ECC M).
   - Badge de status do ingresso (ATIVO em verde).
   - Código UUID exibido abaixo do QR.
3. **Estado vazio:** sem ingressos → mensagem "Você ainda não se inscreveu em nenhum evento" + link "Explorar eventos".

### 6. Tela "Minhas inscrições" (`/minhas-inscricoes`)

1. Navegar diretamente para `/minhas-inscricoes`.
2. Verificar que:
   - Lista paginada (20 por página) das inscrições, mais recente primeiro.
   - Cada item exibe: `Inscrição #id`, `Evento #eventoId`, data de inscrição, badge de status.
   - Badges: ATIVA (verde), CANCELADA (vermelho).
   - Botões "Anterior" / "Próxima" funcionam (desabilitados nas bordas).
3. **Estado vazio:** mensagem "Nenhuma inscrição encontrada".

---

## Cenários de erro a validar

| Cenário | Esperado |
|---|---|
| POST inscrição → 409 `JA_INSCRITO` | Toast: "Você já está inscrito neste evento." |
| POST inscrição → 409 `EVENTO_ESGOTADO` | Toast: "Não há mais vagas disponíveis." |
| POST inscrição → 422 `EVENTO_NAO_PUBLICADO` | Toast: "Evento não disponível para inscrição." |
| POST inscrição → 503 `EVENTO_INDISPONIVEL` | Toast: "Serviço temporariamente indisponível. Tente novamente em instantes." |
| GET /tickets/me → 401 | Redirect para `/login?redirect=/meus-ingressos` (via `ProtectedRoute`) |
| Event-service fora ao carregar "Meus ingressos" | Ingresso exibido com fallback "Evento #id" + nota de aviso |

---

## Critérios de aceite cruzados com o PO

| US | Critério | Implementado |
|---|---|---|
| US-030.1 | Botão "Inscrever-se" visível para GRATUITO/PUBLICADO | Sim |
| US-030.2 | Retorna ingresso com QR em ≤ 2 s | Back + front (não há delay artificial) |
| US-030.3 | Ingresso aparece sem recarregar | Sim (state local `inscricao`) |
| US-030.4 | PAGO: botão indisponível + "Disponível em breve" | Sim |
| US-030.5 | 422 → "Evento não disponível para inscrição" | Sim |
| US-031.1 | Vagas reais (`vagasDisponiveis`) exibidas | Sim (campo "Vagas" no card) |
| US-031.2 | 409 JA_INSCRITO → mensagem clara | Sim |
| US-031.3 | 409 EVENTO_ESGOTADO → mensagem clara | Sim |
| US-032.2 | QR renderizado no front a partir de `codigoUnico` | Sim (qrcode.react) |
| US-032.3 | QR legível em mobile 5" | Sim (size=180-200, SVG escalável) |
| US-033.1 | Meus ingressos com nome/data/local + QR | Sim (composição com event-service) |
| US-033.2 | Status do ingresso legível | Sim (badges ATIVO/UTILIZADO/CANCELADO) |
| US-033.3 | Histórico paginado, mais recente primeiro | Sim (`MinhasInscricoes`) |
| US-033.4 | Estado vazio amigável | Sim (ambas as telas) |

---

## Notas para o tester

- **Retry de rede no POST de inscrição:** o front não faz re-POST automático. Em erro de rede, o usuário deve verificar manualmente em "Meus ingressos" antes de tentar novamente.
- **Dados do evento em "Meus ingressos":** cada ingresso dispara 1 GET ao event-service. Em ambiente de teste com o event-service mockado/fora do ar, os cards exibirão o fallback sem quebrar a página.
- **QR code:** o valor codificado é o UUID `codigoUnico`. Pode ser validado com qualquer leitor de QR para confirmar que o UUID está correto.
