-- ============================================================
-- Cria os 4 databases isolados por servico.
-- Executado apenas na primeira inicializacao do volume Postgres.
-- ============================================================

CREATE DATABASE user_db;
CREATE DATABASE event_db;
CREATE DATABASE ticket_db;
CREATE DATABASE payment_db;

-- Concede privilegios (o owner ja e o POSTGRES_USER do compose,
-- mas deixamos explicito para o caso de mudar a estrategia depois).
GRANT ALL PRIVILEGES ON DATABASE user_db    TO CURRENT_USER;
GRANT ALL PRIVILEGES ON DATABASE event_db   TO CURRENT_USER;
GRANT ALL PRIVILEGES ON DATABASE ticket_db  TO CURRENT_USER;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO CURRENT_USER;
