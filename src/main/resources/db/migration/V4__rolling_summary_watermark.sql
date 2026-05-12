-- V4: add utterance-id watermark support for rolling summaries.

ALTER TABLE utterances
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE context_cache
    ADD COLUMN IF NOT EXISTS compressed_until_utterance_id BIGINT NULL;
