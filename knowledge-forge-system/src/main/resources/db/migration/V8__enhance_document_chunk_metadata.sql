ALTER TABLE document_chunk
    ADD COLUMN chunk_type VARCHAR(50),
    ADD COLUMN section_title VARCHAR(255),
    ADD COLUMN section_path VARCHAR(1000),
    ADD COLUMN start_offset INTEGER,
    ADD COLUMN end_offset INTEGER,
    ADD COLUMN strategy_version VARCHAR(50);
