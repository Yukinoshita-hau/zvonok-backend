-- Stage 6: additive migration for an existing Hibernate-managed schema.
-- Run with psql -v ON_ERROR_STOP=1 after choosing the target database/search_path.
-- Does not bootstrap the application's baseline schema.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $$
BEGIN
    IF to_regclass('call_session') IS NULL THEN
        RAISE NOTICE 'call_session absent: initialize the reviewed baseline schema first';
        RETURN;
    END IF;
    ALTER TABLE call_session ADD COLUMN IF NOT EXISTS media_empty_since timestamp(6) without time zone;
    ALTER TABLE call_session ADD COLUMN IF NOT EXISTS media_observed_at timestamp(6) without time zone;
    IF EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = 'call_session'::regclass
          AND attname IN ('media_empty_since', 'media_observed_at')
          AND NOT attisdropped
          AND (atttypid <> 'timestamp without time zone'::regtype OR attnotnull)
    ) THEN
        RAISE EXCEPTION 'Unexpected media observation column type/nullability; manual review required';
    END IF;
END $$;
COMMIT;
