-- V6: 知识卡片表 — 支持对话知识提取与审核流程
CREATE TABLE IF NOT EXISTS knowledge_card (
    id UUID PRIMARY KEY,
    kb_id UUID NOT NULL,
    conversation_id UUID,
    message_id UUID,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT '概念',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    entity_type VARCHAR(100),
    source_context TEXT,
    reviewer_note TEXT,
    vectorized BOOLEAN NOT NULL DEFAULT FALSE,
    graph_entity_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kc_kb_id ON knowledge_card(kb_id);
CREATE INDEX IF NOT EXISTS idx_kc_status ON knowledge_card(status);
CREATE INDEX IF NOT EXISTS idx_kc_category ON knowledge_card(category);
CREATE INDEX IF NOT EXISTS idx_kc_conversation_id ON knowledge_card(conversation_id);