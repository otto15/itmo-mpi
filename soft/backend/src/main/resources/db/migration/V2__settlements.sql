create table settlement (
    id uuid primary key,
    name varchar(160) not null,
    created_at timestamptz not null default now()
);

create table settlement_membership (
    settlement_id uuid not null references settlement(id),
    user_id uuid not null references app_user(id),
    member_role varchar(32) not null,
    joined_at timestamptz not null default now(),
    primary key (settlement_id, user_id)
);

create index settlement_membership_user_idx
    on settlement_membership (user_id, settlement_id);

insert into settlement (id, name)
values ('00000000-0000-0000-0000-000000000001', 'Каттегат');

insert into settlement_membership (settlement_id, user_id, member_role)
select '00000000-0000-0000-0000-000000000001', id, system_role
from app_user;

alter table user_session add column active_settlement_id uuid;
update user_session
set active_settlement_id = '00000000-0000-0000-0000-000000000001';
alter table user_session alter column active_settlement_id set not null;
alter table user_session
    add constraint user_session_active_settlement_fk
    foreign key (active_settlement_id) references settlement(id);

alter table expedition add column settlement_id uuid;
update expedition
set settlement_id = '00000000-0000-0000-0000-000000000001';
alter table expedition alter column settlement_id set not null;
alter table expedition
    add constraint expedition_settlement_fk
    foreign key (settlement_id) references settlement(id);
create index expedition_settlement_status_idx
    on expedition (settlement_id, status, planned_departure);

alter table warehouse_stock add column settlement_id uuid;
update warehouse_stock
set settlement_id = '00000000-0000-0000-0000-000000000001';
alter table warehouse_stock alter column settlement_id set not null;
alter table warehouse_stock drop constraint warehouse_stock_pkey;
alter table warehouse_stock
    add constraint warehouse_stock_pkey primary key (settlement_id, resource);
alter table warehouse_stock
    add constraint warehouse_stock_settlement_fk
    foreign key (settlement_id) references settlement(id);

alter table ship add column settlement_id uuid;
update ship
set settlement_id = '00000000-0000-0000-0000-000000000001';
alter table ship alter column settlement_id set not null;
alter table ship
    add constraint ship_settlement_fk
    foreign key (settlement_id) references settlement(id);
create index ship_settlement_idx on ship (settlement_id, name);

alter table audit_event add column settlement_id uuid;
update audit_event
set settlement_id = '00000000-0000-0000-0000-000000000001';
alter table audit_event alter column settlement_id set not null;
alter table audit_event
    add constraint audit_event_settlement_fk
    foreign key (settlement_id) references settlement(id);
drop index audit_event_aggregate_idx;
create index audit_event_settlement_aggregate_idx
    on audit_event (settlement_id, aggregate_type, aggregate_id, happened_at desc);

alter table app_user drop column system_role;
