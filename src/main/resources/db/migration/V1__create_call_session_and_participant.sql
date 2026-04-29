create table if not exists call_session (
    id bigserial primary key,
    room_id bigint not null references room(id),
    room_type varchar(16) not null,
    livekit_room_name varchar(128) not null,
    status varchar(24) not null,
    created_by_user_id bigint not null references "user"(id),
    host_user_id bigint not null references "user"(id),
    started_at timestamp not null,
    ended_at timestamp null,
    ended_by_user_id bigint null references "user"(id),
    end_reason varchar(64) null,
    version bigint not null default 0,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint chk_call_session_status check (status in ('RINGING','ACTIVE','ENDED','DECLINED','CANCELLED','MISSED')),
    constraint chk_call_session_room_type check (room_type in ('PRIVATE','GROUP'))
);

create unique index if not exists uk_call_session_livekit_room_name
    on call_session(livekit_room_name);

create index if not exists idx_call_session_room_status
    on call_session(room_id, status);

create unique index if not exists uk_call_session_one_active_per_room
    on call_session(room_id)
    where status in ('RINGING', 'ACTIVE');

create table if not exists call_participant (
    id bigserial primary key,
    call_session_id bigint not null references call_session(id) on delete cascade,
    user_id bigint not null references "user"(id),
    identity varchar(128) not null,
    display_name varchar(128) null,
    role varchar(16) not null,
    status varchar(24) not null,
    joined_at timestamp null,
    left_at timestamp null,
    version bigint not null default 0,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint uk_call_participant_call_user unique (call_session_id, user_id),
    constraint chk_call_participant_role check (role in ('HOST','MEMBER')),
    constraint chk_call_participant_status check (status in ('INVITED','RINGING','ACCEPTED','JOINED','DECLINED','LEFT','KICKED'))
);

create index if not exists idx_call_participant_call_user
    on call_participant(call_session_id, user_id);

create index if not exists idx_call_participant_call_status
    on call_participant(call_session_id, status);
