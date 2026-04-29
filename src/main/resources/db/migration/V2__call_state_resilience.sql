ALTER TABLE call_session
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP,
    ALTER COLUMN end_reason TYPE VARCHAR(64);

ALTER TABLE call_participant
    ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS declined_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP;

UPDATE call_participant
SET accepted_at = joined_at
WHERE accepted_at IS NULL AND status IN ('ACCEPTED','JOINED');

CREATE INDEX IF NOT EXISTS idx_call_participant_call ON call_participant(call_session_id);
