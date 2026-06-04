-- V7: 为 conversation 表添加知识库关联字段，支持对话与知识库的正确关联
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS kb_id UUID;
CREATE INDEX IF NOT EXISTS idx_conversation_kb_id ON conversation(kb_id);