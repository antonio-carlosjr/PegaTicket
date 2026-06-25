# ✅ Checklist de Estórias — Ticketeira

> Board de **reivindicação e acompanhamento** das estórias. Antes de começar uma estória, escreva seu nome em **Dono** (assim ninguém pega a mesma). Fonte das histórias: [`backlog.md`](backlog.md).

## Como usar (anti-duplicação)
1. Escolha uma estória **sem dono** e **respeitando a ordem** (setas de dependência abaixo).
2. Preencha a coluna **Dono** com seu nome e marque o **Status** como `EM ANDAMENTO`.
3. Crie a branch no padrão e use o **ID no commit** (ver `rules/coding-standards.md §4`):
   - Branch: `feat/sprint-<n>/<US-id>-<slug>`
   - Commit: `tipo(US-id): assunto` → ex.: `feat(US-031): inscricao com unique constraint`
4. Ao integrar/abrir PR, marque `EM REVIEW`; depois do merge, `DONE` (✅).

**Legenda Status:** ⬜ A fazer · 🟡 Em andamento · 🔵 Em review · ✅ Done · ⛔ Bloqueada

---

## 🟦 Sprint 1 — Identidade & Autorização
> Ordem/dependência: **US-051 (enabler) primeiro** → depois US-052 ‖ US-050 → US-053 e US-054 junto de US-050.

| ✓ | ID | Estória | Dono | Branch sugerida | Status |
|---|---|---|---|---|---|
| ⬜ | **US-051** | Papel trafega no token + `X-User-Papel` (autorização real) | _____ | `feat/sprint-1/US-051-papel-no-token` | ⬜ |
| ⬜ | **US-052** | Cadastro de promotor com perfil completo (CPF, endereço, redes, contato) | _____ | `feat/sprint-1/US-052-cadastro-promotor` | ⬜ |
| ⬜ | **US-050** | Tela de controle de usuários (aprovar/rejeitar promotor + ver dados) | _____ | `feat/sprint-1/US-050-controle-usuarios` | ⬜ |
| ⬜ | **US-053** | Admin ativa/inativa usuários (inativo não loga) | _____ | `feat/sprint-1/US-053-ativar-inativar` | ⬜ |
| ⬜ | **US-054** | E-mail de aprovação/rejeição (c/ motivo) + seguir como participante + reenviar | _____ | `feat/sprint-1/US-054-email-status-promotor` | ⬜ |

## 🟩 Sprint 2 — Eventos (event-service)
> Ordem/dependência: **US-020 (criar) → US-021 (editar/publicar/cancelar)**; US-022 e US-023 (leitura) entram após o modelo existir.

| ✓ | ID | Estória | Dono | Branch sugerida | Status |
|---|---|---|---|---|---|
| ⬜ | **US-020** | Promotor cria evento (gratuito/pago) → status RASCUNHO | _____ | `feat/sprint-2/US-020-criar-evento` | ⬜ |
| ⬜ | **US-021** | Promotor edita/publica/cancela evento (ownership + transições) | _____ | `feat/sprint-2/US-021-editar-publicar-cancelar` | ⬜ |
| ⬜ | **US-022** | Participante lista/busca eventos publicados (filtros + paginação) | _____ | `feat/sprint-2/US-022-listar-buscar-eventos` | ⬜ |
| ⬜ | **US-023** | Participante vê detalhe do evento (data, local, preço, vagas) | _____ | `feat/sprint-2/US-023-detalhe-evento` | ⬜ |

## 🟧 Sprint 3 — Inscrição & Ingresso QR (ticket-service · gratuito)
> Ordem/dependência: **US-030 (inscrição base) → US-031 (capacidade/concorrência)**; US-032 (ingresso QR) junto de US-030; US-033 (meus ingressos) depois. ⚠️ Sprint de **concorrência** — teste de última vaga é gate do DoD.

| ✓ | ID | Estória | Dono | Branch sugerida | Status |
|---|---|---|---|---|---|
| ⬜ | **US-030** | Inscrição em evento gratuito → recebe ingresso na hora | _____ | `feat/sprint-3/US-030-inscricao-gratuita` | ⬜ |
| ⬜ | **US-031** | Controle de capacidade + sem dupla inscrição (concorrência) | _____ | `feat/sprint-3/US-031-capacidade-concorrencia` | ⬜ |
| ⬜ | **US-032** | Ingresso único com QR (`codigo_unico`) | _____ | `feat/sprint-3/US-032-ingresso-qr` | ⬜ |
| ⬜ | **US-033** | "Meus ingressos" + histórico de inscrições | _____ | `feat/sprint-3/US-033-meus-ingressos` | ⬜ |

---

## 📋 Backlog (sprints futuras — ainda não planejadas em detalhe)
| ID | Estória | Épico / Sprint provável |
|---|---|---|
| US-024 | Participante avalia evento (nota 1-5) | Eventos · Sprint 5 |
| US-025 | Promotor vê reputação (média) do evento | Eventos · Sprint 5 |
| US-034 | Promotor valida ingresso (check-in por QR) na porta | Ingressos · Sprint 5 |
| US-035 | Participante cancela inscrição conforme política | Ingressos · Sprint 5 |
| US-040 | Pagar evento pago (gateway simulado) com escrow | Pagamentos · Sprint 4 |
| US-041 | Emitir ingresso só após `pagamento.aprovado` (saga) | Pagamentos · Sprint 4 |
| US-042 | Reembolso se evento for cancelado | Pagamentos · Sprint 4 |
| US-043 | Repasse ao promotor (−10%) após `evento.finalizado` | Pagamentos · Sprint 4 |
| US-060 | Consumidores RabbitMQ idempotentes (`processed_events`) | Plataforma · Sprint 4/5 |
| US-061 | Testes de carga no abre-vendas (concorrência) | Plataforma · Sprint 5+ |

> Total planejado (Sprints 1-3): **13 estórias**. Mantenha este arquivo em dia — é o que evita dois devs na mesma estória.
