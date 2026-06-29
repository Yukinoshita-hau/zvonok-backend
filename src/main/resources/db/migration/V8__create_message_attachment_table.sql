create table if not exists message_attachment (
    id bigserial primary key,
    message_id bigint not null references "message"(id) on delete cascade,
    type varchar(16) not null,
    storage_key varchar(512) not null,
    original_file_name varchar(512) not null,
    content_type varchar(128) not null,
    size_bytes bigint not null,
    width integer,
    height integer,
    duration_ms bigint,
    waveform_json text,
    created_at timestamp not null default now(),
    constraint chk_message_attachment_type check (type in ('IMAGE','VIDEO','AUDIO','VIDEO_NOTE'))
);

create index if not exists idx_message_attachment_message
    on message_attachment(message_id);
