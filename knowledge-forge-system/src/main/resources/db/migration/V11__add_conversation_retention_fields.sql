ALTER TABLE conversation
    ADD COLUMN cleanup_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN expires_at TIMESTAMP,
    ADD COLUMN remind_at TIMESTAMP,
    ADD COLUMN reminded_at TIMESTAMP;

CREATE INDEX idx_conversation_cleanup_status ON conversation(cleanup_status);
CREATE INDEX idx_conversation_expires_at ON conversation(expires_at);
CREATE INDEX idx_conversation_remind_at ON conversation(remind_at);
