-- Read-only audit of disposable Stage10 DB; never target an existing deployment.
BEGIN READ ONLY;
SELECT current_database(), version();
SELECT status, count(*) FROM call_session GROUP BY status;
SELECT count(*) AS active_sessions FROM call_session WHERE status <> 'ENDED';
SELECT count(*) AS active_participants_in_ended_calls FROM call_participant p
JOIN call_session s ON s.id=p.call_session_id
WHERE s.status='ENDED' AND p.status IN ('ACCEPTED','JOINED','RINGING');
SELECT count(*) AS duplicate_burst_messages FROM (
 SELECT room_id,sender_id,content FROM message WHERE content LIKE 'burst %'
 GROUP BY room_id,sender_id,content HAVING count(*)>1
) duplicates;
SELECT count(*) AS preview_id_mismatches FROM room r
WHERE coalesce(r.last_message_id,-1) <> coalesce((
 SELECT max(m.id) FROM message m WHERE m.room_id=r.id AND m.deleted_at IS NULL
),-1);
SELECT count(*) AS messages FROM message;
SELECT count(*) AS attachments FROM message_attachment;
SELECT count(*) AS database_connections FROM pg_stat_activity WHERE datname=current_database();
SELECT id,status,end_reason,started_at,media_empty_since,media_observed_at,ended_at
FROM call_session ORDER BY id DESC LIMIT 4;
COMMIT;
