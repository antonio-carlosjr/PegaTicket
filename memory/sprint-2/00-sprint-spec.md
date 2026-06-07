# Sprint 2 — Eventos (event-service) · Spec Mestre (ultra-plan)

> Gerada por `/planejar-sprint 2`. Depende do **Sprint 1** (papel PROMOTOR real + `X-User-Papel`).
> Lê antes: [`architectural-plan.md`](../project/architectural-plan.md) (§6 event_db, §7 concorrência), [`backlog.md`](../project/backlog.md) (Épico A), `docs/api/event-service.yaml`, `services/event-service/.../V1__init.sql`. Gabarito: `user-service`.

---

## 1. Objetivo (1 frase)
> **Ao fim deste sprint, o Promotor (aprovado) cria/edita/publica/cancela seus eventos e o Participante lista, busca e vê o detalhe de eventos publicados** — transformando o 1º esqueleto (event-service) em serviço real. (Roadmap §8 / RF02.)

## 2. Escopo — histórias

| ID | História | Cabe porque… |
|---|---|---|
| **US-020** | Como promotor verificado, quero **criar** um evento (gratuito ou pago) para abrir inscrições | schema `eventos` já existe; falta `@Entity`+service+controller |
| **US-021** | Como promotor, quero **editar/publicar/cancelar** meu evento para gerir a oferta | máquina de estados `RASCUNHO→PUBLICADO→CANCELADO/REALIZADO` |
| **US-022** | Como participante, quero **listar/buscar** eventos publicados para escolher | leitura paginada + filtros simples |
| **US-023** | Como participante, quero ver o **detalhe** de um evento (data, local, preço, vagas) | uma query + projeção |

> Avaliações/reputação (US-024/025) ficam para a Sprint 5. Inscrição (Épico B) é Sprint 3.

## 3. Serviços afetados
| Componente | Mudança |
|---|---|
| `services/event-service` | **vira real**: `@Entity Evento`, repository, `EventService`, `EventController` (substitui stubs 501), migration V2 (campos auxiliares), validação de papel/ownership lendo `X-User-Id`/`X-User-Papel` |
| `api-gateway` | rotas `/api/events/**` já existem; confirmar encaminhamento de `X-User-Papel` (Sprint 1) |
| `frontend` | telas de promotor (Meus eventos + criar/editar) e de participante (lista + detalhe) |

## 4. Delta de modelo de dados — `V2__eventos_aux.sql` (event_db)
```sql
-- Vaga disponível para reserva atômica (consumida na Sprint 3)
ALTER TABLE eventos ADD COLUMN vagas_disponiveis INTEGER;          -- inicializada = capacidade ao PUBLICAR
ALTER TABLE eventos ADD CONSTRAINT chk_vagas_nao_neg CHECK (vagas_disponiveis IS NULL OR vagas_disponiveis >= 0);
-- Imagem opcional (melhora UI/demo; sem upload — apenas URL)
ALTER TABLE eventos ADD COLUMN imagem_url VARCHAR(300);
CREATE INDEX idx_eventos_publicados ON eventos(status) WHERE status = 'PUBLICADO';
```
> `eventos` já tem: titulo, descricao, data_inicio/fim, local, tipo (GRATUITO/PAGO), status, capacidade>0, preco, prazo_reembolso_dias, promotor_id, timestamps. **UTC no banco** (coding-standards).

## 5. Autorização & ownership
| Operação | Regra |
|---|---|
| criar / editar / publicar / cancelar | `X-User-Papel == PROMOTOR` (senão **403**) **e** `evento.promotor_id == X-User-Id` (senão **403/404**, não vaza existência) |
| listar / detalhe (publicados) | autenticado (qualquer papel ativo); RASCUNHO só o **owner** vê |
| `GET /events/meus` | promotor vê os próprios (qualquer status) |

## 6. Endpoints (detalhe em `api-contracts.md`)
| Método | Rota `/api` | Auth | O quê |
|---|---|---|---|
| POST | `/events` | PROMOTOR | cria (status RASCUNHO; `promotor_id`=X-User-Id) |
| GET | `/events/meus` | PROMOTOR | meus eventos (qualquer status) |
| PUT | `/events/{id}` | PROMOTOR owner | edita (regras por status — ver §8) |
| POST | `/events/{id}/publicar` | PROMOTOR owner | RASCUNHO→PUBLICADO; **inicializa `vagas_disponiveis=capacidade`** |
| POST | `/events/{id}/cancelar` | PROMOTOR owner | →CANCELADO (na Sprint 4+ dispara `evento.cancelado`/reembolso) |
| GET | `/events` | autenticado | lista **PUBLICADOS**; filtros `q`(texto), `tipo`, `de/ate`(data); paginado (`page,size`) |
| GET | `/events/{id}` | autenticado | detalhe (RASCUNHO só owner) |

## 7. Frontend
- **Promotor:** "Meus eventos" (lista + status + ações publicar/cancelar) · **criar/editar evento** (wizard: dados → data/local → tipo/preço/capacidade/imagem). Itens de menu só para `papel==PROMOTOR`.
- **Participante:** "Eventos" (lista publicados + busca/filtros) · **detalhe** (data, local, preço, capacidade — vagas reais chegam na Sprint 3). Estados loading/empty/error (padrão do front).

## 8. Concorrência & invariantes
| Cenário | Estratégia |
|---|---|
| Transição de estado inválida (publicar cancelado, etc.) | validar máquina de estados no service → 409/422 |
| Editar `capacidade` depois de publicado | na Sprint 2 não há inscritos → permitido; **regra defensiva já**: não reduzir `capacidade` abaixo de `capacidade - vagas_disponiveis` (preparando Sprint 3) |
| `preco`/`tipo` coerentes | CHECK do schema (PAGO→preco>0) + validação na borda |
> Concorrência pesada (abre-vendas) é da **Sprint 3**; aqui o foco é integridade de estado e ownership.

## 9. Dependências
US-020 (criar) → US-021 (editar/publicar) → US-022/023 (listar/detalhe dependem de existir publicado). Tudo depende do **Sprint 1** (papel PROMOTOR + header).

## 10. Riscos & mitigação
| Risco | Prob. | Impacto | Mitigação |
|---|---|---|---|
| 1ª implementação JPA do event-service não bate com schema (`ddl-auto: validate`) | Média | Alto | mapear `@Entity` exatamente ao V1/V2; rodar `./mvnw -pl services/event-service test` cedo |
| Timezone (data_inicio/fim) | Média | Médio | UTC no banco, converter na borda (coding-standards) |
| Ownership furado (promotor edita evento de outro) | Baixa | Alto | teste de ownership obrigatório; 404 para não-owner |
| Lista pública sem paginação | Baixa | Médio | paginar desde já (`page,size`), nunca retorno irrestrito |

## 11. Fora de escopo (intencional)
Inscrição/ingresso (Sprint 3) · pagamento (Sprint 4) · avaliações/reputação (Sprint 5) · check-in · upload de imagem (só URL) · busca geo/avançada.

## 12. Critérios de sucesso verificáveis
1. Promotor aprovado **cria→publica** um evento; ele aparece na **lista pública**.
2. **Não-promotor** (participante) → **403** ao tentar `POST /events`.
3. **Não-owner** → 403/404 ao editar/cancelar evento alheio.
4. Evento em **RASCUNHO não aparece** na lista pública; **cancelar** muda status e some da lista.
5. `vagas_disponiveis` fica = `capacidade` após publicar (pronto para a Sprint 3).
6. `./mvnw verify` verde; front sem erro de tipo.

## 13. Testes-chave (Arquiteto detalha em `tests-spec.md`)
- Authz cross-papel: participante → 403 em criar/editar/publicar.
- Ownership: promotor B → 403/404 ao editar evento do promotor A.
- Máquina de estados: publicar evento CANCELADO → erro; publicar 2x idempotente/erro.
- Listagem: só PUBLICADOS; filtros por texto/tipo/data; paginação.
- `ddl-auto: validate` passa (entidades ⇿ schema).

## 14. Decisões a registrar (ADR)
- **ADR-P08** — escopo Sprint 2 (Eventos) aprovado.
- **ADR-T07** — `reservar/liberar vaga` (decremento atômico) é **preparado** aqui (`vagas_disponiveis`) e **consumido** na Sprint 3; endpoint de reserva entra na Sprint 3 (cross-service).

## 15. Definition of Done
- [ ] event-service real (CRUD + publicar/cancelar), stubs 501 removidos.
- [ ] V2 aplicada/revertível; `ddl-auto: validate` ok.
- [ ] Authz por papel + ownership testados.
- [ ] Front: meus-eventos + criar/editar + lista + detalhe, estados de UI.
- [ ] `./mvnw verify` + CI verdes; commits atômicos; PR.
- [ ] ADRs registradas; backlog atualizado; retrospectiva.
