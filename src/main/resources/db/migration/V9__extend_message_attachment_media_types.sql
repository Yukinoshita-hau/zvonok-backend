alter table message_attachment
    add column if not exists waveform_json text;

alter table message_attachment
    drop constraint if exists chk_message_attachment_type;

alter table message_attachment
    add constraint chk_message_attachment_type
        check (type in ('IMAGE','VIDEO','AUDIO','VIDEO_NOTE'));
