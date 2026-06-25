alter table canvas_board
    add column if not exists drawing_access varchar(32) not null default 'EVERYONE';

alter table canvas_board
    add column if not exists selected_drawer_username varchar(255);
