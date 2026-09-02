create table settlement (
    id bigserial primary key,
    name varchar(160) not null,
    created_at timestamptz not null default now()
);

create table app_user (
    id bigserial primary key,
    display_name varchar(120) not null,
    username varchar(80) not null,
    password_salt bytea not null,
    password_hash bytea not null,
    enabled boolean not null default true
);

create unique index app_user_username_ci_idx on app_user (lower(username));

create table settlement_membership (
    settlement_id bigint not null references settlement(id),
    user_id bigint not null references app_user(id),
    member_role varchar(32) not null,
    joined_at timestamptz not null default now(),
    primary key (settlement_id, user_id),
    unique (user_id),
    constraint settlement_membership_role_check
        check (member_role in ('JARL', 'WARRIOR', 'SHIPBUILDER', 'PRIEST'))
);

create table user_session (
    token_hash char(64) primary key,
    user_id bigint not null references app_user(id),
    active_settlement_id bigint not null references settlement(id),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    constraint user_session_membership_fk
        foreign key (active_settlement_id, user_id)
        references settlement_membership(settlement_id, user_id)
);

create index user_session_active_idx
    on user_session(user_id, expires_at) where revoked_at is null;

create table ship_type (
    code varchar(32) primary key,
    name varchar(80) not null,
    capacity integer not null check (capacity > 0)
);

create table ship_type_requirement (
    ship_type_code varchar(32) not null references ship_type(code),
    stage integer not null check (stage between 0 and 3),
    resource varchar(32) not null,
    quantity integer not null check (quantity > 0),
    primary key (ship_type_code, stage, resource)
);

create table expedition (
    id bigserial primary key,
    settlement_id bigint not null references settlement(id),
    name varchar(160) not null,
    target varchar(240) not null,
    status varchar(32) not null,
    planned_departure date not null,
    version integer not null default 0,
    finalized_at timestamptz,
    loot_gold integer,
    loot_provisions integer,
    loot_thralls integer,
    constraint expedition_status_check
        check (status in ('PREPARATION', 'SAILING', 'COMPLETED', 'CANCELLED')),
    constraint expedition_loot_non_negative check (
        coalesce(loot_gold, 0) >= 0 and
        coalesce(loot_provisions, 0) >= 0 and
        coalesce(loot_thralls, 0) >= 0
    )
);

create index expedition_settlement_status_idx
    on expedition (settlement_id, status, planned_departure);

create table crew_assignment (
    id bigserial primary key,
    expedition_id bigint not null references expedition(id),
    user_id bigint not null references app_user(id),
    expedition_role varchar(80) not null,
    participation_status varchar(32) not null,
    alive boolean not null default true,
    version integer not null default 0,
    unique (expedition_id, user_id),
    constraint crew_assignment_status_check
        check (participation_status in ('PENDING', 'CONFIRMED', 'DECLINED', 'REMOVED'))
);

create table warehouse_stock (
    settlement_id bigint not null references settlement(id),
    resource varchar(32) not null,
    quantity integer not null check (quantity >= 0),
    version integer not null default 0,
    primary key (settlement_id, resource)
);

create table ship (
    id bigserial primary key,
    settlement_id bigint not null references settlement(id),
    name varchar(120) not null,
    ship_type_code varchar(32) not null references ship_type(code),
    stage integer not null check (stage between 0 and 4),
    blessed boolean not null default false,
    version integer not null default 0
);

create unique index ship_settlement_name_ci_idx on ship (settlement_id, lower(name));

create table ship_stage_requirement (
    ship_id bigint not null references ship(id),
    stage integer not null check (stage between 0 and 3),
    resource varchar(32) not null,
    quantity integer not null check (quantity > 0),
    primary key (ship_id, stage, resource)
);

create table expedition_ship (
    expedition_id bigint not null references expedition(id),
    ship_id bigint not null references ship(id),
    assigned_at timestamptz not null default now(),
    primary key (expedition_id, ship_id)
);

create index expedition_ship_ship_idx on expedition_ship(ship_id, expedition_id);

create table ship_build_request (
    id bigserial primary key,
    settlement_id bigint not null references settlement(id),
    expedition_id bigint references expedition(id),
    ship_type_code varchar(32) not null references ship_type(code),
    ship_id bigint not null unique references ship(id),
    requested_by bigint not null references app_user(id),
    status varchar(32) not null,
    created_at timestamptz not null default now(),
    constraint ship_build_request_status_check
        check (status in ('IN_CONSTRUCTION', 'READY', 'CANCELLED'))
);

create index ship_build_request_settlement_idx
    on ship_build_request(settlement_id, status, created_at desc);

create table wergild_allocation (
    id bigserial primary key,
    expedition_id bigint not null references expedition(id),
    recipient varchar(160) not null,
    category varchar(32) not null,
    gold integer not null check (gold >= 0),
    provisions integer not null check (provisions >= 0),
    thralls integer not null check (thralls >= 0)
);

create table audit_event (
    id bigserial primary key,
    settlement_id bigint not null references settlement(id),
    happened_at timestamptz not null default now(),
    actor_role varchar(32) not null,
    event_type varchar(80) not null,
    aggregate_type varchar(80) not null,
    aggregate_id bigint not null,
    details jsonb not null default '{}'::jsonb,
    constraint audit_event_actor_role_check
        check (actor_role in ('JARL', 'WARRIOR', 'SHIPBUILDER', 'PRIEST'))
);

create index audit_event_settlement_aggregate_idx
    on audit_event (settlement_id, aggregate_type, aggregate_id, happened_at desc);

create or replace function prevent_finalized_expedition_rewrite()
returns trigger as $$
begin
    if old.finalized_at is not null and (
        new.status is distinct from old.status or
        new.loot_gold is distinct from old.loot_gold or
        new.loot_provisions is distinct from old.loot_provisions or
        new.loot_thralls is distinct from old.loot_thralls or
        new.finalized_at is distinct from old.finalized_at
    ) then
        raise exception 'Finalized expedition results are immutable';
    end if;
    return new;
end;
$$ language plpgsql;

create trigger expedition_results_immutable
before update on expedition
for each row execute function prevent_finalized_expedition_rewrite();
