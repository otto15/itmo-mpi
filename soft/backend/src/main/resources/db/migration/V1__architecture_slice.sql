create table app_user (
    id uuid primary key,
    display_name varchar(120) not null,
    system_role varchar(32) not null
);

create table user_account (
    user_id uuid primary key references app_user(id),
    username varchar(80) not null unique,
    password_salt bytea not null,
    password_hash bytea not null,
    enabled boolean not null default true
);

create table user_session (
    token_hash char(64) primary key,
    user_id uuid not null references app_user(id),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked_at timestamptz
);

create index user_session_active_idx on user_session(user_id, expires_at) where revoked_at is null;

create table expedition (
    id uuid primary key,
    name varchar(160) not null,
    target varchar(240) not null,
    status varchar(32) not null,
    planned_departure date not null,
    ship_name varchar(120) not null,
    version integer not null default 0,
    finalized_at timestamptz,
    loot_gold integer,
    loot_provisions integer,
    loot_thralls integer,
    constraint expedition_loot_non_negative check (
        coalesce(loot_gold, 0) >= 0 and
        coalesce(loot_provisions, 0) >= 0 and
        coalesce(loot_thralls, 0) >= 0
    )
);

create table crew_assignment (
    id uuid primary key,
    expedition_id uuid not null references expedition(id),
    user_id uuid not null references app_user(id),
    expedition_role varchar(80) not null,
    participation_status varchar(32) not null,
    alive boolean not null default true,
    version integer not null default 0,
    unique (expedition_id, user_id)
);

create table warehouse_stock (
    resource varchar(32) primary key,
    quantity integer not null check (quantity >= 0),
    version integer not null default 0
);

create table ship (
    id uuid primary key,
    name varchar(120) not null,
    stage integer not null check (stage between 0 and 4),
    blessed boolean not null default false,
    version integer not null default 0
);

create table ship_stage_requirement (
    ship_id uuid not null references ship(id),
    stage integer not null check (stage between 0 and 3),
    resource varchar(32) not null,
    quantity integer not null check (quantity > 0),
    primary key (ship_id, stage, resource)
);

create table wergild_allocation (
    id bigserial primary key,
    expedition_id uuid not null references expedition(id),
    recipient varchar(160) not null,
    category varchar(32) not null,
    gold integer not null check (gold >= 0),
    provisions integer not null check (provisions >= 0),
    thralls integer not null check (thralls >= 0)
);

create table audit_event (
    id bigserial primary key,
    happened_at timestamptz not null default now(),
    actor_role varchar(32) not null,
    event_type varchar(80) not null,
    aggregate_type varchar(80) not null,
    aggregate_id uuid not null,
    details jsonb not null default '{}'::jsonb
);

create index audit_event_aggregate_idx
    on audit_event (aggregate_type, aggregate_id, happened_at desc);

-- The database is the last line of defence for finalized results.
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

insert into app_user (id, display_name, system_role) values
    ('00000000-0000-0000-0000-000000000101', 'Бьёрн Железнобокий', 'WARRIOR'),
    ('00000000-0000-0000-0000-000000000102', 'Ивар Бескостный', 'WARRIOR'),
    ('00000000-0000-0000-0000-000000000103', 'Флоки', 'SHIPBUILDER'),
    ('00000000-0000-0000-0000-000000000104', 'Хальвдан', 'WARRIOR'),
    ('00000000-0000-0000-0000-000000000105', 'Торстейн Красный', 'WARRIOR'),
    ('00000000-0000-0000-0000-000000000106', 'Рагнар Лодброк', 'JARL'),
    ('00000000-0000-0000-0000-000000000107', 'Годи Уппсалы', 'PRIEST');

insert into user_account(user_id, username, password_salt, password_hash) values
    ('00000000-0000-0000-0000-000000000106', 'ragnar', decode('00112233445566778899aabbccddeeff', 'hex'), decode('dbac41204804d097a13f438fcd1886481b1e0098f099415990b59c63efe59393', 'hex')),
    ('00000000-0000-0000-0000-000000000104', 'halvdan', decode('102132435465768798a9bacbdcedfe0f', 'hex'), decode('d3b7bdc12e01b7edb921a46dc36041acafac1f946bc22c29b9db2e2575e37dba', 'hex')),
    ('00000000-0000-0000-0000-000000000103', 'floki', decode('2031425364758697a8b9cadbecfd0e1f', 'hex'), decode('4d5d5d686aa869c02ffb6fff89550d6c6b1a8dc4b7e4699e45f230ecf5d2ac22', 'hex')),
    ('00000000-0000-0000-0000-000000000107', 'godi', decode('30415263748596a7b8c9daebfc0d1e2f', 'hex'), decode('ed7f7eb113babdc880f2cd8a186e284c43486eb85ff8de58c0968e6098c1b002', 'hex'));

insert into expedition (id, name, target, status, planned_departure, ship_name) values
    ('00000000-0000-0000-0000-000000000201', 'Поход к берегам Уэссекса', 'Аббатство и торговый порт', 'SAILING', '2026-09-14', 'Морской змей'),
    ('00000000-0000-0000-0000-000000000202', 'Экспедиция в Нортумбрию', 'Монастырь Линдисфарн', 'PREPARATION', '2026-10-02', 'Северный ветер');

insert into crew_assignment (id, expedition_id, user_id, expedition_role, participation_status) values
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000104', 'рулевой', 'PENDING'),
    ('00000000-0000-0000-0000-000000000311', '00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000101', 'херсир', 'CONFIRMED'),
    ('00000000-0000-0000-0000-000000000312', '00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000102', 'щитоносец', 'CONFIRMED'),
    ('00000000-0000-0000-0000-000000000313', '00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000103', 'корабельный мастер', 'CONFIRMED');

insert into warehouse_stock (resource, quantity) values
    ('WOOD', 120), ('CLOTH', 35), ('RESIN', 22),
    ('GOLD', 40), ('PROVISIONS', 90), ('THRALLS', 0);

insert into ship (id, name, stage, blessed) values
    ('00000000-0000-0000-0000-000000000401', 'Северный ветер', 1, false);

insert into ship_stage_requirement (ship_id, stage, resource, quantity) values
    ('00000000-0000-0000-0000-000000000401', 0, 'WOOD', 30),
    ('00000000-0000-0000-0000-000000000401', 1, 'WOOD', 60),
    ('00000000-0000-0000-0000-000000000401', 1, 'RESIN', 10),
    ('00000000-0000-0000-0000-000000000401', 2, 'WOOD', 25),
    ('00000000-0000-0000-0000-000000000401', 2, 'RESIN', 8),
    ('00000000-0000-0000-0000-000000000401', 3, 'CLOTH', 20),
    ('00000000-0000-0000-0000-000000000401', 3, 'RESIN', 4);
