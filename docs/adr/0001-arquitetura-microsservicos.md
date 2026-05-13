# ADR 0001 — Arquitetura em microsserviços

**Status:** Aceito · 2026-05-12

## Contexto

O mini-mundo descreve um sistema com picos de tráfego concentrados na abertura de vendas de ingressos (alta concorrência) e operações que misturam cargas síncronas (catálogo, login) com cargas pesadas/transacionais (pagamentos, reembolsos, repasses).

RNFs relevantes:

- **RNF01** — múltiplos usuários simultâneos.
- **RNF02** — consistência na inscrição sob concorrência.
- **RNF04** — consistência financeira (escrow → reembolso/repasse).
- **RNF05** — processamento assíncrono de pagamentos.
- **RNF06** — arquitetura distribuída, responsabilidade única por serviço.

Alternativas consideradas: monolito modular, modular com schema-per-bounded-context, e microsserviços.

## Decisão

Adotamos **microsserviços** organizados por **bounded context**:

- `user-service` — identidade, autenticação, verificação de promotor.
- `event-service` — catálogo de eventos, avaliações, reputação.
- `ticket-service` — inscrições, ingressos únicos, check-in.
- `payment-service` — cobrança, escrow, reembolso, repasse.

Mais um **API Gateway** como único ponto de entrada.

Comunicação **síncrona via REST** (consultas em tempo real) e **assíncrona via RabbitMQ** (eventos de domínio que disparam fluxos pesados — ver ADR 0004).

## Consequências

✅ Cada serviço escala independente do outro. O pico de inscrições no `ticket-service` não derruba o `user-service`.
✅ Falha localizada — um bug no `payment-service` não impede login/busca de eventos.
✅ Boa fronteira para divisão de trabalho do time da disciplina.

❌ Complexidade operacional maior (docker-compose, network, observabilidade). Compensado pelo uso de Docker e infra como código.
❌ Consistência eventual entre serviços (ex.: reputação do promotor calculada no `event-service` e refletida no `user-service`). Aceito porque o domínio tolera atraso de segundos.
❌ Necessidade de tratar idempotência nos consumidores AMQP — endereçado em ADR 0004.

Para projeto acadêmico, mantemos **um único container Postgres com 4 databases lógicos** (ver ADR 0002) para reduzir consumo de recursos sem sacrificar o isolamento de schemas.
