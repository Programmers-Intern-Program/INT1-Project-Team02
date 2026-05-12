-- V3: embedding search features for meeting summaries.

ALTER TABLE meeting_summaries ADD COLUMN IF NOT EXISTS embedding VECTOR(1536);
ALTER TABLE meeting_summaries ADD COLUMN IF NOT EXISTS summary_tsv TSVECTOR;

CREATE INDEX IF NOT EXISTS idx_meeting_summaries_embedding
    ON meeting_summaries USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_meeting_summaries_tsv
    ON meeting_summaries USING GIN (summary_tsv);

CREATE OR REPLACE FUNCTION update_meeting_summaries_tsv()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.summary_tsv := to_tsvector('simple', COALESCE(NEW.summary, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_meeting_summaries_tsv ON meeting_summaries;
CREATE TRIGGER trg_meeting_summaries_tsv
    BEFORE INSERT OR UPDATE OF summary
    ON meeting_summaries
    FOR EACH ROW
EXECUTE FUNCTION update_meeting_summaries_tsv();

UPDATE meeting_summaries
SET summary_tsv = to_tsvector('simple', COALESCE(summary, ''))
WHERE summary_tsv IS NULL;
