-- Additive schema support for optional multipart clientRequestId; old rows remain NULL.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $$
BEGIN
    IF to_regclass('message') IS NULL THEN
        RAISE NOTICE 'message absent: initialize the reviewed baseline schema first';
        RETURN;
    END IF;
    ALTER TABLE message ADD COLUMN IF NOT EXISTS upload_request_key varchar(36);
    ALTER TABLE message ADD COLUMN IF NOT EXISTS upload_request_hash varchar(64);
    IF EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = 'message'::regclass AND NOT attisdropped
          AND attname IN ('upload_request_key', 'upload_request_hash')
          AND (atttypid <> 'character varying'::regtype OR attnotnull
               OR (attname = 'upload_request_key' AND atttypmod <> 40)
               OR (attname = 'upload_request_hash' AND atttypmod <> 68))
    ) THEN
        RAISE EXCEPTION 'Unexpected upload request column type/length/nullability; manual review required';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'message'::regclass
                   AND conname = 'uk_message_sender_upload_request') THEN
        ALTER TABLE message ADD CONSTRAINT uk_message_sender_upload_request UNIQUE (sender_id, upload_request_key);
    END IF;
END $$;
COMMIT;
