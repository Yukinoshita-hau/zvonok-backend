create table if not exists canvas_board (
    id bigserial primary key,
    call_session_id bigint not null references call_session(id) on delete cascade,
    room_id bigint not null references room(id),
    mode varchar(32) not null,
    background varchar(32) not null,
    created_by varchar(128) not null,
    active boolean not null default true,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint chk_canvas_board_mode check (mode in ('WHITEBOARD','SCREEN_OVERLAY')),
    constraint chk_canvas_board_background check (background in ('WHITE','BLACK','TRANSPARENT'))
);

create index if not exists idx_canvas_board_call_active
    on canvas_board(call_session_id, active);

create index if not exists idx_canvas_board_call_mode_active
    on canvas_board(call_session_id, mode, active);

create unique index if not exists uk_canvas_board_one_active_mode_per_call
    on canvas_board(call_session_id, mode)
    where active = true;

create table if not exists canvas_stroke (
    id bigserial primary key,
    board_id bigint not null references canvas_board(id) on delete cascade,
    stroke_key varchar(128) not null,
    user_id varchar(128) not null,
    color varchar(64) not null,
    width integer not null,
    tool varchar(32) not null,
    ended_at timestamp null,
    created_at timestamp not null default now(),
    constraint uk_canvas_stroke_board_key unique (board_id, stroke_key),
    constraint chk_canvas_stroke_tool check (tool in ('PEN','ERASER')),
    constraint chk_canvas_stroke_width check (width between 1 and 64)
);

create index if not exists idx_canvas_stroke_board_created
    on canvas_stroke(board_id, created_at);

create table if not exists canvas_point (
    id bigserial primary key,
    stroke_id bigint not null references canvas_stroke(id) on delete cascade,
    x double precision not null,
    y double precision not null,
    position integer not null,
    created_at timestamp not null default now(),
    constraint uk_canvas_point_stroke_position unique (stroke_id, position),
    constraint chk_canvas_point_x check (x >= 0.0 and x <= 1.0),
    constraint chk_canvas_point_y check (y >= 0.0 and y <= 1.0)
);

create index if not exists idx_canvas_point_stroke_position
    on canvas_point(stroke_id, position);
