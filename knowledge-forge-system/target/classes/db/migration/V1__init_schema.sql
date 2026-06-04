CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE knowledge_base (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    icon        VARCHAR(50),
    deleted     BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_kb_deleted ON knowledge_base(deleted);

CREATE TABLE document (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id            UUID NOT NULL REFERENCES knowledge_base(id),
    title            VARCHAR(500) NOT NULL,
    file_type        VARCHAR(20) NOT NULL,
    file_path        VARCHAR(1000),
    file_size        BIGINT,
    chunk_count      INTEGER DEFAULT 0,
    status           VARCHAR(20) DEFAULT 'PROCESSING',
    error_message    TEXT,
    version          INTEGER DEFAULT 1,
    deleted          BOOLEAN DEFAULT FALSE,
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_document_kb_id ON document(kb_id);
CREATE INDEX idx_document_deleted ON document(deleted);

CREATE TABLE document_chunk (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    content          TEXT NOT NULL,
    chunk_index      INTEGER NOT NULL,
    parent_chunk_id  UUID,
    token_count      INTEGER,
    embedding_ready  BOOLEAN DEFAULT FALSE,
    status           VARCHAR(20) DEFAULT 'READY',
    error_message    TEXT,
    embedding        vector(1024),
    created_at       TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_chunk_document_id ON document_chunk(document_id);
CREATE INDEX idx_chunk_embedding ON document_chunk
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 200);

CREATE TABLE conversation (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(255) NOT NULL,
    deleted          BOOLEAN DEFAULT FALSE,
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_conversation_deleted ON conversation(deleted);

CREATE TABLE chat_message (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    content          TEXT NOT NULL,
    metadata         JSONB DEFAULT '{}',
    created_at       TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_message_conversation_id ON chat_message(conversation_id);