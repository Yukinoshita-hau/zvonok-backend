alter table canvas_board
    add column if not exists template_type varchar(64) not null default 'CLEAN';

alter table canvas_board
    add column if not exists timer_started_at timestamp null;

alter table canvas_board
    add column if not exists timer_duration_seconds integer null;

alter table canvas_board
    add column if not exists timer_status varchar(32) not null default 'STOPPED';

alter table canvas_board
    add column if not exists background_image_url varchar(1024);

alter table canvas_board
    add column if not exists background_image_created_by varchar(255);

alter table canvas_board
    add column if not exists background_image_created_at timestamp;

alter table canvas_board
    add column if not exists presenter_username varchar(255);

alter table canvas_board
    add column if not exists presenter_mode_enabled boolean not null default false;

create table if not exists canvas_sticky_note (
    id bigserial primary key,
    board_id bigint not null references canvas_board(id) on delete cascade,
    note_key varchar(128) not null,
    created_by varchar(255) not null,
    text varchar(1000) not null,
    color varchar(64) not null,
    x double precision not null,
    y double precision not null,
    width double precision not null,
    height double precision not null,
    z_index integer not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_canvas_sticky_note_board_key unique (board_id, note_key),
    constraint chk_canvas_sticky_note_x check (x >= 0.0 and x <= 1.0),
    constraint chk_canvas_sticky_note_y check (y >= 0.0 and y <= 1.0),
    constraint chk_canvas_sticky_note_width check (width > 0.0 and width <= 1.0),
    constraint chk_canvas_sticky_note_height check (height > 0.0 and height <= 1.0)
);

create index if not exists idx_canvas_sticky_note_board_id
    on canvas_sticky_note(board_id);

create table if not exists canvas_note_vote (
    id bigserial primary key,
    board_id bigint not null references canvas_board(id) on delete cascade,
    note_id bigint not null references canvas_sticky_note(id) on delete cascade,
    user_id varchar(255) not null,
    created_at timestamp not null,
    constraint uk_canvas_note_vote_note_user unique (note_id, user_id)
);

create index if not exists idx_canvas_note_vote_board_id
    on canvas_note_vote(board_id);

create index if not exists idx_canvas_note_vote_note_id
    on canvas_note_vote(note_id);
