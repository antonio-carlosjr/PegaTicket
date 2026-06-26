-- Sprint 2: campos auxiliares de eventos.
-- vagas_disponiveis prepara o decremento atomico anti-overbooking (consumido na Sprint 3, ADR-T07).
-- Fica NULL enquanto RASCUNHO; o service inicializa = capacidade ao PUBLICAR.
ALTER TABLE eventos ADD COLUMN vagas_disponiveis INTEGER;
ALTER TABLE eventos ADD CONSTRAINT chk_vagas_nao_neg
    CHECK (vagas_disponiveis IS NULL OR vagas_disponiveis >= 0);

-- Imagem opcional (apenas URL — sem upload nesta sprint).
ALTER TABLE eventos ADD COLUMN imagem_url VARCHAR(300);

-- Indice parcial: a listagem publica filtra sempre status='PUBLICADO'.
-- Indice parcial mantem o B-tree pequeno (so as linhas publicadas) e cobre o caso quente.
CREATE INDEX idx_eventos_publicados ON eventos (status) WHERE status = 'PUBLICADO';
