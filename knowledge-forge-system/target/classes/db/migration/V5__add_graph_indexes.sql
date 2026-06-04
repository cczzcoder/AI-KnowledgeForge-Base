CREATE INDEX IF NOT EXISTS idx_kgr_kb_source_target_type ON knowledge_graph_relation(kb_id, source_entity_id, target_entity_id, relation_type);

CREATE INDEX IF NOT EXISTS idx_kge_kb_name ON knowledge_graph_entity(kb_id, name);