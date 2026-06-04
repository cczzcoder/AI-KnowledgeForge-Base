CREATE TABLE vector_store (
    id        UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding vector(1024)
);

CREATE INDEX idx_vector_embedding ON vector_store
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 200);