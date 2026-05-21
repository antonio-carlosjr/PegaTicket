-- ============================================================
-- Executar UMA VEZ no Postgres da Railway apos provisionar o plugin.
-- O plugin entrega 1 database ("railway"). Vamos criar os 4 isolados.
--
-- Como rodar:
--   1) Na pagina do servico Postgres na Railway -> aba "Data" -> "Query"
--   2) Cole este script e clique em Run
--   OU pela CLI:
--      railway link
--      railway connect postgres < docs/deploy/postgres-init.sql
-- ============================================================

CREATE DATABASE user_db;
CREATE DATABASE event_db;
CREATE DATABASE ticket_db;
CREATE DATABASE payment_db;
