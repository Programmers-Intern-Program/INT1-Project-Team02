-- V5: replace spoken_at/sequence_no with speech_started_at/speech_ended_at.
-- sequenceNo was assigned at STT completion time, not actual speech time.
-- startMs/endMs from the bot represent the real audio boundary.

-- RENAME COLUMN is not idempotent, so use ADD + backfill + DROP instead.
ALTER TABLE utterances
    ADD COLUMN IF NOT EXISTS speech_started_at TIMESTAMP;

UPDATE utterances
SET speech_started_at = spoken_at
WHERE speech_started_at IS NULL;

ALTER TABLE utterances
    ALTER COLUMN speech_started_at SET NOT NULL;

ALTER TABLE utterances
    DROP COLUMN IF EXISTS spoken_at;

ALTER TABLE utterances
    ADD COLUMN IF NOT EXISTS speech_ended_at TIMESTAMP NULL;

ALTER TABLE utterances
    DROP COLUMN IF EXISTS sequence_no;

ALTER TABLE context_cache
    DROP COLUMN IF EXISTS start_sequence_no;

ALTER TABLE context_cache
    DROP COLUMN IF EXISTS end_sequence_no;
