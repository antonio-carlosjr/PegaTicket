# Sprint 3 — Planning do PO

## Objetivo (1 frase)

"Ao fim deste sprint, o **participante (Bruno)** consegue se inscrever num evento gratuito publicado, receber o ingresso com QR na hora, e o sistema garante que nenhuma vaga é vendida duas vezes — mesmo com múltiplas tentativas simultâneas."

---

## Histórias selecionadas

| ID | História (Como \<ator\> quero \<ação\> para \<valor\>) | Critérios de aceite operacionais |
|---|---|---|
| **US-030** | Como **participante**, quero me **inscrever num evento gratuito** e receber meu ingresso na hora, para garantir minha participação sem burocracia. | 1. Bruno abre o detalhe de um evento GRATUITO e PUBLICADO e vê o botão "Inscrever-se". 2. Ao clicar, o sistema processa e retorna em ≤ 2 s o ingresso com o código QR visível na tela (HTTP 201). 3. O ingresso aparece imediatamente em "Meus ingressos" sem precisar recarregar. 4. Se o evento for PAGO, o botão não está disponível ou exibe "Disponível em breve" — sem erro silencioso. 5. Se o evento não estiver PUBLICADO (rascunho, cancelado), a tentativa retorna 422 com mensagem "Evento não disponível para inscrição". |
| **US-031** | Como **participante**, quero que o sistema **controle a capacidade e impeça dupla inscrição**, para não haver overbooking nem surpresas na porta. | 1. Bruno vê a contagem de **vagas restantes** atualizada no detalhe do evento (campo `vagas_disponiveis` em tempo real, não estático). 2. Bruno tenta se inscrever pela **segunda vez** no mesmo evento → resposta 409 "Você já está inscrito neste evento" (sem criar nova inscrição). 3. Capacidade = N: as N primeiras inscrições são aceitas; a (N+1)ª retorna 409 "Evento esgotado". 4. **Cenário concorrente — última vaga:** K clientes fazem POST simultâneo para a última vaga disponível → **exatamente 1** retorna 201 (com ingresso), K-1 retornam 409 "Evento esgotado"; `vagas_disponiveis` final = 0, jamais negativo (sem overbooking). 5. **Cenário concorrente — dupla inscrição paralela:** o mesmo usuário dispara 2 requisições simultâneas de inscrição → **exatamente 1** sucesso, a outra retorna 409 "Você já está inscrito". 6. Se o event-service estiver fora do ar no momento da reserva de vaga, a inscrição falha inteiramente com 503, e nenhuma vaga é debitada. |
| **US-032** | Como **participante**, quero receber um **ingresso único com QR** após confirmação da inscrição, para apresentar na porta do evento. | 1. Cada inscrição aprovada gera **exatamente um** ingresso com `codigo_unico` (UUID v4 ou HMAC-assinado — conforme ADR-T09). 2. O QR é renderizado no frontend a partir do `codigo_unico` devolvido na resposta da inscrição (nenhuma imagem gerada no backend). 3. O QR exibe corretamente em mobile sem zoom: leitura visível em tela de 5". 4. Tentativas de emitir um segundo ingresso para a mesma inscrição (retry de chamada) retornam o ingresso existente (idempotência) ou falham com erro — **nunca geram dois ingressos para a mesma inscrição**. 5. O `codigo_unico` é único globalmente: colisão de UUID é impossível na prática; a constraint `UNIQUE(codigo_unico)` no banco é a última linha de defesa. |
| **US-033** | Como **participante**, quero ver **"meus ingressos"** com o QR e o **histórico de inscrições**, para ter acesso fácil a todos os meus eventos. | 1. Bruno acessa "Meus ingressos" e vê a lista de todos os ingressos ativos com: nome do evento, data, local e o QR (renderizado, não um link). 2. Cada ingresso exibe seu **status** (ATIVO / UTILIZADO / CANCELADO) de forma legível. 3. A tela de **histórico de inscrições** lista todas as inscrições (paginada, mais recente primeiro) com nome do evento, data da inscrição e status da inscrição (ATIVA / CANCELADA). 4. Se Bruno não tiver ingressos, a tela mostra estado vazio amigável ("Você ainda não se inscreveu em nenhum evento — que tal explorar os eventos disponíveis?"). 5. As listagens carregam em ≤ 3 s em conexão 4G normal. |

---

## Atores exercitados

- **Bruno (participante, 24):** caminho feliz completo — abre evento gratuito publicado → clica "Inscrever-se" → vê ingresso com QR → acessa "Meus ingressos" e confirma que o QR está lá. Caminho de erro: tenta segunda inscrição → mensagem clara. Tenta inscrição em evento lotado → mensagem "esgotado". Concorrência: dispara duas abas simultaneamente tentando a última vaga — só uma passa.
- **Marina (promotora, 35):** *fora do escopo ativo deste sprint* (o lado da promotora — ver lista de inscritos para seus eventos — é US-036, não mapeado para este sprint). Marina usa o sistema como participante normalmente.
- **Admin:** sem cenário direto neste sprint. O admin não interage com o fluxo de inscrição gratuita. Rastreabilidade: o admin pode indiretamente auditar inscricoes pela `ticket_db` (sem tela dedicada neste sprint).

---

## Riscos de produto

| Risco | Prob. | Impacto | Por que é dor de produto |
|---|---|---|---|
| **Overbooking na última vaga** (race condition) | Alta | Crítico | Duas pessoas pagam / confirmam para a mesma vaga → destroem a confiança no produto. A constraint `WHERE vagas > 0` + `rowsAffected` é inegociável; **teste de carga concorrente é gate do DoD, não opcional.** |
| **Falha parcial cross-service** (event-service cai entre reservar-vaga e criar inscrição local) | Média | Alto | Vaga é debitada mas ingresso nunca é gerado. Participante fica sem ingresso e sem feedback. A compensação (`liberar-vaga`) deve ser testada: simular falha no passo 3 e verificar que `vagas_disponiveis` volta ao valor anterior. |
| **Dupla emissão de ingresso** (retry / idempotência) | Baixa | Alto | Cliente faz retry da inscrição (rede caiu após 201) e recebe dois ingressos para a mesma inscrição. `UNIQUE(inscricao_id)` + mesma transação local protege; o frontend não deve re-POST em retry sem garantia de idempotência. |
| **QR previsível / forjável** | Média | Médio (impacto real no Sprint 5 — check-in) | UUID v4 não é forjável na prática; HMAC-assinado é mais robusto. Decisão deve ser registrada em ADR-T09 antes de codificar — mudar `codigo_unico` depois de emitido invalida todos os QRs gerados. |
| **`reservar-vaga` exposto publicamente no gateway** | Média | Alto | Qualquer usuário poderia zerar `vagas_disponiveis` sem se inscrever. Endpoint **não deve ser roteado** pelo gateway; isolamento de rede Docker + ADR-T08 (autorização inter-serviço) são o controle. |

---

## Fora deste sprint (intencional)

- **Caminho pago / escrow / saga AMQP** — Sprint 4. Inscrição em evento PAGO retorna 422 neste sprint.
- **Check-in por QR na porta** (US-034) — Sprint 5. O QR é gerado aqui, mas a validação pelo promotor/leitor entra depois.
- **Cancelamento de inscrição + reembolso** (US-035) — Sprint 5.
- **Lista de inscritos para a promotora Marina** (visão de gestão do evento) — BACKLOG / Sprint futura.
- **Notificações por e-mail de confirmação de inscrição** — nice-to-have futuro; RabbitMQ entra na Sprint 4.
- **Avaliações de evento** (US-024/025) — BACKLOG.
- **Ingresso pago / escrow** (US-040/041) — Sprint 4.
