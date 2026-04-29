-- V2: embedding search features.
-- Existing databases baseline at V1 run this migration.
-- Fresh databases run V1 first, then this migration.

DO
$$
    DECLARE
        current_type      TEXT;
        has_non_null_data BOOLEAN;
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'decisions') THEN
            RETURN;
        END IF;

        IF NOT EXISTS (SELECT 1
                       FROM information_schema.columns
                       WHERE table_name = 'decisions'
                         AND column_name = 'embedding') THEN
            ALTER TABLE decisions ADD COLUMN embedding VECTOR(1536);
            RETURN;
        END IF;

        SELECT pg_catalog.format_type(atttypid, atttypmod)
        INTO current_type
        FROM pg_attribute
        WHERE attrelid = 'decisions'::regclass
          AND attname = 'embedding'
          AND NOT attisdropped;

        IF current_type = 'vector(1536)' THEN
            RETURN;
        END IF;

        SELECT EXISTS(SELECT 1 FROM decisions WHERE embedding IS NOT NULL) INTO has_non_null_data;

        IF has_non_null_data THEN
            RAISE EXCEPTION
                'decisions.embedding has non-null data. Back up or clear existing embeddings before changing vector dimensions.';
        END IF;

        ALTER TABLE decisions DROP COLUMN embedding;
        ALTER TABLE decisions ADD COLUMN embedding VECTOR(1536);
    END
$$;

ALTER TABLE decisions     ADD COLUMN IF NOT EXISTS content_tsv       TSVECTOR;
ALTER TABLE work_logs    ADD COLUMN IF NOT EXISTS status             VARCHAR(20) NOT NULL DEFAULT 'TODO';
ALTER TABLE utterances   ADD COLUMN IF NOT EXISTS speaker_type       VARCHAR(20) NOT NULL DEFAULT 'HUMAN';
ALTER TABLE utterances   ADD COLUMN IF NOT EXISTS sequence_no        BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE utterances   ADD COLUMN IF NOT EXISTS token_count        INTEGER;
ALTER TABLE context_cache ADD COLUMN IF NOT EXISTS version           INTEGER     NOT NULL DEFAULT 1;
ALTER TABLE context_cache ADD COLUMN IF NOT EXISTS start_sequence_no BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE context_cache ADD COLUMN IF NOT EXISTS end_sequence_no   BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE context_cache DROP COLUMN IF EXISTS turn_range;

CREATE INDEX IF NOT EXISTS idx_decisions_embedding
    ON decisions USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_decisions_tsv
    ON decisions USING GIN (content_tsv);

CREATE OR REPLACE FUNCTION update_decisions_tsv()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.content_tsv := to_tsvector('simple', COALESCE(NEW.content, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_decisions_tsv ON decisions;
CREATE TRIGGER trg_decisions_tsv
    BEFORE INSERT OR UPDATE OF content
    ON decisions
    FOR EACH ROW
EXECUTE FUNCTION update_decisions_tsv();
