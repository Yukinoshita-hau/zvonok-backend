create table if not exists room_invite (
    id bigserial primary key,
    room_id bigint not null references room(id) on delete cascade,
    token_hash varchar(128) not null unique,
    created_by_user_id bigint not null references "user"(id),
    created_at timestamp not null default now(),
    expires_at timestamp,
    max_uses integer,
    uses_count integer not null default 0,
    active boolean not null default true
);

create index if not exists idx_room_invite_room
    on room_invite(room_id);

create table if not exists user_settings (
    id bigserial primary key,
    user_id bigint not null unique references "user"(id) on delete cascade,
    selected_theme varchar(64) not null default 'dark',
    custom_theme_enabled boolean not null default false,
    custom_theme_json text
);
