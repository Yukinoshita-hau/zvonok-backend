create table if not exists code_session (
    id bigserial primary key,
    call_session_id bigint not null references call_session(id) on delete cascade,
    room_id bigint not null references room(id),
    created_by varchar(128) not null,
    active boolean not null default true,
    language varchar(32) not null,
    code text not null,
    stdin text not null,
    last_output text,
    last_status varchar(32),
    last_exit_code integer,
    last_execution_time_ms bigint,
    active_editor_user_id bigint references "user"(id),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    closed_at timestamp,
    constraint chk_code_session_last_status check (
        last_status is null
        or last_status in (
            'SUCCESS',
            'COMPILATION_ERROR',
            'RUNTIME_ERROR',
            'TIME_LIMIT_EXCEEDED',
            'INTERNAL_ERROR'
        )
    )
);

create index if not exists idx_code_session_call_active
    on code_session(call_session_id, active);

create unique index if not exists uk_code_session_one_active_per_call
    on code_session(call_session_id)
    where active = true;
