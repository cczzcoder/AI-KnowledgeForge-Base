CREATE TABLE knowledge_graph_entity (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id             UUID NOT NULL REFERENCES knowledge_base(id),
    name              VARCHAR(500) NOT NULL,
    entity_type       VARCHAR(100) NOT NULL,
    source_chunk_id   UUID,
    source_document_id UUID,
    description       TEXT,
    aliases           TEXT,
    created_at        TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_kge_kb_id ON knowledge_graph_entity(kb_id);
CREATE INDEX idx_kge_name ON knowledge_graph_entity(name);
CREATE INDEX idx_kge_type ON knowledge_graph_entity(entity_type);

CREATE TABLE knowledge_graph_relation (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id             UUID NOT NULL REFERENCES knowledge_base(id),
    source_entity_id  UUID NOT NULL REFERENCES knowledge_graph_entity(id) ON DELETE CASCADE,
    target_entity_id  UUID NOT NULL REFERENCES knowledge_graph_entity(id) ON DELETE CASCADE,
    relation_type     VARCHAR(100) NOT NULL,
    description       TEXT,
    source_chunk_id   UUID,
    created_at        TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_kgr_kb_id ON knowledge_graph_relation(kb_id);
CREATE INDEX idx_kgr_source ON knowledge_graph_relation(source_entity_id);
CREATE INDEX idx_kgr_target ON knowledge_graph_relation(target_entity_id);