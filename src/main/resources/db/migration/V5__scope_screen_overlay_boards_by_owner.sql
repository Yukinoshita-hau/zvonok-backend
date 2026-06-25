alter table canvas_board
    add column if not exists overlay_owner_username varchar(255);

update canvas_board
set overlay_owner_username = created_by
where mode = 'SCREEN_OVERLAY'
  and overlay_owner_username is null;

drop index if exists uk_canvas_board_one_active_mode_per_call;

create unique index if not exists uk_canvas_board_one_active_whiteboard_per_call
    on canvas_board(call_session_id, mode)
    where active = true and mode = 'WHITEBOARD';

create unique index if not exists uk_canvas_board_one_active_overlay_owner_per_call
    on canvas_board(call_session_id, mode, overlay_owner_username)
    where active = true and mode = 'SCREEN_OVERLAY';

create index if not exists idx_canvas_board_call_mode_owner_active
    on canvas_board(call_session_id, mode, overlay_owner_username, active);
