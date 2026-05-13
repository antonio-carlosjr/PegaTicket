# ADR 0002 — Database-per-service com instância Postgres compartilhada

**Status:** Aceito · 2026-05-12

## Contexto

O padrão clássico de microsserviços é **um banco por serviço**, sem joins cross-context. Em ambiente acadêmico/local, rodar 4 containers Postgres consome ~600 MB de RAM e complica o `docker-compose`.

## Decisão

Cada serviço tem **seu próprio database lógico** (`user_db`, `event_db`, `ticket_db`, `payment_db`), todos hospedados **na mesma instância Postgres** durante o desenvolvimento.

- Init script (`infra/postgres/init/01-create-databases.sql`) cria os 4 databases na primeira subida do volume.
- Cada serviço aponta para o seu próprio database via `DB_NAME`.
- **Nenhum serviço consulta o database de outro** — toda integração é via REST ou AMQP.
- Migrations gerenciadas por **Flyway**, isoladas por serviço.

Referências entre contextos viram **IDs (`Long`)** em vez de FKs cross-database (ex.: `pagamentos.inscricao_id` referencia um id que vive no `ticket_db`, sem foreign key declarada).

## Consequências

✅ Isolamento de schema preservado — refatorar `ticket_db` não quebra `payment-service`.
✅ Migration por serviço — cada time evolui seu schema sem coordenação.
✅ Redução de RAM e tempo de startup em dev.
✅ Em produção, basta mudar `DB_HOST` por serviço para separar fisicamente sem mudar código.

❌ Sem `JOIN` SQL entre serviços — relatórios cross-context exigem chamadas REST ou um BI separado.
❌ Sem `FOREIGN KEY` cross-database — integridade referencial vira responsabilidade da aplicação. Ex.: `payment_service` precisa validar via REST que o `inscricao_id` existe antes de processar a cobrança.
❌ Backup/restore feitos por database, não global.
