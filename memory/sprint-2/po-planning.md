# Sprint 2 — Planning do PO

## Objetivo (1 frase)

"Ao fim deste sprint, a Marina (promotora aprovada) cria, edita e publica seus eventos sem se perder, e o Bruno (participante) encontra e consulta eventos publicados — transformando o event-service de stub em serviço real."

---

## Histórias selecionadas

| ID | História (Como \<ator\> quero \<ação\> para \<valor\>) | Critérios de aceite operacionais |
|---|---|---|
| **US-020** | Como promotora aprovada, quero **criar** um evento (gratuito ou pago) para abrir inscrições | 1. Marina (papel PROMOTOR verificado) preenche o wizard em **≤ 3 telas** (dados gerais → data/local → tipo/preço/capacidade) e clica "Salvar" — o sistema cria o evento com status **RASCUNHO** e exibe confirmação visual. 2. O evento recém-criado **não aparece** na lista pública de Bruno enquanto estiver em RASCUNHO. 3. **Bruno (participante) recebe 403** ao tentar `POST /api/events` — sem que nenhum dado de evento seja criado. 4. Campos obrigatórios ausentes (título, data, capacidade) impedem o envio e exibem mensagem inline — Marina não perde os dados já preenchidos. 5. Evento pago com `preco = 0` é barrado na borda (back + front). |
| **US-021** | Como promotora, quero **editar, publicar e cancelar** meu evento para gerir a oferta | 1. Marina abre um RASCUNHO seu, edita qualquer campo e salva — as alterações persistem e o status continua RASCUNHO. 2. Marina clica "Publicar": o sistema muda o status para **PUBLICADO**, inicializa `vagas_disponiveis = capacidade` e exibe feedback de sucesso. A partir deste momento o evento aparece na lista pública de Bruno. 3. Marina tenta editar/publicar/cancelar um evento de **outro promotor** (promotor_id diferente) → recebe **403 ou 404** sem vazar que o registro existe. 4. Marina tenta publicar um evento já **CANCELADO** → recebe erro 409/422 com mensagem clara. 5. Marina cancela um evento PUBLICADO → status vira CANCELADO e o evento **some da lista pública** de Bruno imediatamente. 6. Um promotor sem papel PROMOTOR (ex.: participante com token adulterado) → 403 em qualquer operação de escrita. |
| **US-022** | Como participante, quero **listar e buscar** eventos publicados para escolher onde ir | 1. Bruno acessa a lista e vê **apenas eventos com status PUBLICADO** — nenhum RASCUNHO ou CANCELADO aparece. 2. Bruno busca por texto (`q`), filtra por `tipo` (GRATUITO/PAGO) e por intervalo de datas (`de`/`ate`) — os filtros podem ser combinados e retornam resultados corretos. 3. A lista é paginada (`page`, `size`); Bruno navega para a próxima página sem erro. 4. Quando não há eventos que correspondam ao filtro, Bruno vê uma mensagem de "nenhum evento encontrado" (estado vazio, não tela em branco). 5. Após Marina cancelar um evento, Bruno **atualiza a lista e o evento some** — sem necessidade de logout/login. |
| **US-023** | Como participante, quero ver o **detalhe** de um evento (data, local, preço, vagas) | 1. Bruno clica num evento publicado e vê: título, descrição, data/hora de início e fim (formatados no fuso do usuário), local, tipo (gratuito/pago), preço (ou "Gratuito"), capacidade total e imagem (se houver URL). 2. Bruno tenta acessar a URL de detalhe de um evento em **RASCUNHO** que não é seu → recebe 404 (não vaza existência). 3. O detalhe exibe estado de **loading** enquanto carrega e **erro amigável** se a requisição falhar — não quebra a tela. 4. Os botões de inscrição **não estão presentes** (inscrição é Sprint 3) — o detalhe é somente leitura para o participante neste sprint. |

---

## Atores exercitados

- **Marina (promotora, aprovada, `X-User-Papel: PROMOTOR`):**
  - Cria evento novo via wizard de 3 etapas; confirma que fica em RASCUNHO.
  - Edita campos do rascunho e publica; confirma que aparece para Bruno.
  - Tenta editar evento de outro promotor; espera 403/404.
  - Cancela evento publicado; confirma que some da lista pública.
  - Tenta publicar evento cancelado; espera erro de transição de estado.

- **Bruno (participante, `X-User-Papel: PARTICIPANTE`):**
  - Tenta criar evento via `POST /api/events`; espera 403 imediato.
  - Lista eventos com filtros de texto, tipo e data; vê só PUBLICADOS.
  - Abre detalhe de evento publicado; vê todas as informações.
  - Tenta acessar detalhe de RASCUNHO de outro promotor; espera 404.
  - Lista eventos após Marina cancelar um; confirma ausência do cancelado.

- **Admin (não exercitado diretamente no Sprint 2):**
  - Pré-condição: Admin já aprovou Marina no Sprint 1 (`perfil_verificado = APROVADO`). Sem nova ação do Admin neste sprint.

---

## Riscos de produto

| Risco | Impacto | Mitigação no aceite |
|---|---|---|
| **Formulário de evento complexo** — wizard com 3 etapas pode perder dados entre etapas ou confundir Marina em dispositivos móveis | Alto (abandono de criação) | Critério: Marina não perde dados já preenchidos ao voltar uma etapa; wizard indica progresso visual |
| **Datas e timezone** — `data_inicio`/`data_fim` armazenadas em UTC; Marina informa no horário local; Bruno vê no horário dele | Médio (evento exibido na hora errada) | Critério: front converte para UTC ao enviar; back armazena UTC; detalhe exibe horário local do usuário. Smoke test: criar evento às 14h BRT e verificar que o detalhe exibe 14h BRT |
| **Ownership furado** — promotor A consegue editar/cancelar evento do promotor B | Alto (destruição de dados, desconfiança) | Critério obrigatório: teste com token do promotor B apontando para evento do promotor A → 403/404; não deve retornar o recurso nem modificá-lo |
| **Transições de estado inválidas** — publicar evento CANCELADO, publicar evento já PUBLICADO duas vezes | Médio (corrupção de estado) | Critério: `POST /events/{id}/publicar` em evento CANCELADO retorna 409/422; publicar evento já PUBLICADO retorna erro ou é idempotente (definido no código, não silencioso) |
| **Lista sem paginação** — retorno irrestrito de todos os eventos publicados | Médio (degradação de performance na demo) | Critério: endpoint `/api/events` sem `page`/`size` usa defaults razoáveis; resposta sempre paginada |

---

## Fora deste sprint (intencional)

- **Inscrição e ingresso** — Sprint 3 (US-030/031/032/033). O botão "Inscrever-se" não entra no front ainda; o detalhe é somente leitura.
- **Pagamento e escrow** — Sprint 4 (US-040/041/042/043). Eventos pagos são criados mas não processam transação.
- **Avaliações e reputação** — Sprint 5 (US-024/025). Campo de avaliação fora de escopo.
- **Check-in por QR** — Sprint 5+ (US-034). Sem scanner de ingresso neste sprint.
- **Upload de imagem** — fora do roadmap atual. Apenas URL de imagem opcional.
- **Busca geográfica/avançada** — fora do roadmap atual. Filtros: texto livre, tipo, intervalo de datas.
- **Reembolso automático ao cancelar** — Sprint 4. O cancelamento muda o status mas não dispara saga de pagamento neste sprint.
- **Consumidores RabbitMQ** — Sprint 5 (US-060). Topologia declarada; não codada neste sprint.
