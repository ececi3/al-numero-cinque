-- V7: categoria come entita' gestibile (creazione/eliminazione da admin)
-- invece di stringa libera su menu_item. Migra i valori distinti gia'
-- presenti in menu_item.categoria in righe categoria, poi ricollega
-- menu_item tramite categoria_id e rimuove la vecchia colonna testuale.

CREATE TABLE categoria (
    id              BIGSERIAL PRIMARY KEY,
    nome            VARCHAR(64) NOT NULL UNIQUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

INSERT INTO categoria (nome)
SELECT DISTINCT categoria FROM menu_item WHERE categoria IS NOT NULL;

ALTER TABLE menu_item ADD COLUMN categoria_id BIGINT REFERENCES categoria(id);

UPDATE menu_item
SET categoria_id = (SELECT c.id FROM categoria c WHERE c.nome = menu_item.categoria)
WHERE categoria IS NOT NULL;

ALTER TABLE menu_item DROP COLUMN categoria;

CREATE INDEX idx_menu_item_categoria ON menu_item(categoria_id);
