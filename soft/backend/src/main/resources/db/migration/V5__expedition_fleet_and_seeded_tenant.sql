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

insert into ship_type(code, name, capacity) values
    ('KNOERR', 'Кнорр', 20),
    ('DRAKKAR', 'Драккар', 40);

insert into ship_type_requirement(ship_type_code, stage, resource, quantity) values
    ('KNOERR', 0, 'WOOD', 20),
    ('KNOERR', 1, 'WOOD', 30),
    ('KNOERR', 1, 'RESIN', 5),
    ('KNOERR', 2, 'WOOD', 15),
    ('KNOERR', 2, 'RESIN', 4),
    ('KNOERR', 3, 'CLOTH', 10),
    ('KNOERR', 3, 'RESIN', 3),
    ('DRAKKAR', 0, 'WOOD', 30),
    ('DRAKKAR', 1, 'WOOD', 60),
    ('DRAKKAR', 1, 'RESIN', 10),
    ('DRAKKAR', 2, 'WOOD', 25),
    ('DRAKKAR', 2, 'RESIN', 8),
    ('DRAKKAR', 3, 'CLOTH', 20),
    ('DRAKKAR', 3, 'RESIN', 4);

alter table ship add column ship_type_code varchar(32);
update ship set ship_type_code = 'DRAKKAR';
alter table ship alter column ship_type_code set not null;
alter table ship add constraint ship_type_fk foreign key (ship_type_code) references ship_type(code);

alter table expedition add column required_capacity integer;
update expedition set required_capacity = case id
    when '00000000-0000-0000-0000-000000000201' then 55
    when '00000000-0000-0000-0000-000000000202' then 70
    when '00000000-0000-0000-0000-000000000203' then 20
    when '00000000-0000-0000-0000-000000000204' then 40
    else 20
end;
alter table expedition alter column required_capacity set not null;
alter table expedition add constraint expedition_required_capacity_positive check (required_capacity > 0);

create table expedition_ship (
    expedition_id uuid not null references expedition(id),
    ship_id uuid not null references ship(id),
    assigned_at timestamptz not null default now(),
    primary key (expedition_id, ship_id)
);

create index expedition_ship_ship_idx on expedition_ship(ship_id, expedition_id);

create table ship_build_request (
    id uuid primary key,
    settlement_id uuid not null references settlement(id),
    expedition_id uuid not null references expedition(id),
    ship_type_code varchar(32) not null references ship_type(code),
    ship_id uuid not null unique references ship(id),
    requested_by uuid not null references app_user(id),
    status varchar(32) not null,
    created_at timestamptz not null default now()
);

create index ship_build_request_settlement_idx
    on ship_build_request(settlement_id, status, created_at desc);

insert into ship(id, settlement_id, name, stage, blessed, ship_type_code) values
    ('00000000-0000-0000-0000-000000000402', '00000000-0000-0000-0000-000000000001', 'Морской волк', 4, true, 'DRAKKAR'),
    ('00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000001', 'Морской змей', 4, true, 'DRAKKAR'),
    ('00000000-0000-0000-0000-000000000404', '00000000-0000-0000-0000-000000000001', 'Волчий клык', 4, true, 'KNOERR'),
    ('00000000-0000-0000-0000-000000000405', '00000000-0000-0000-0000-000000000001', 'Ворон', 4, true, 'KNOERR');

insert into expedition_ship(expedition_id, ship_id, assigned_at) values
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000403', now() - interval '30 days'),
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000404', now() - interval '30 days'),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000405', now() - interval '8 days'),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000401', now() - interval '4 days'),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000405', now() - interval '80 days'),
    ('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000402', now() - interval '50 days');

insert into ship_build_request(
    id, settlement_id, expedition_id, ship_type_code, ship_id, requested_by, status, created_at
) values (
    '00000000-0000-0000-0000-000000000501',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000202',
    'DRAKKAR',
    '00000000-0000-0000-0000-000000000401',
    '00000000-0000-0000-0000-000000000106',
    'IN_CONSTRUCTION',
    now() - interval '4 days'
);

insert into crew_assignment(id, expedition_id, user_id, expedition_role, participation_status, alive) values
    ('00000000-0000-0000-0000-000000000321', '00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000104', 'разведчик', 'CONFIRMED', true),
    ('00000000-0000-0000-0000-000000000322', '00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000101', 'херсир', 'CONFIRMED', true);

insert into audit_event(
    settlement_id, happened_at, actor_role, event_type, aggregate_type, aggregate_id, details
) values
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '50 days',
        'WARRIOR', 'PARTICIPATION_CONFIRMED', 'CREW_ASSIGNMENT',
        '00000000-0000-0000-0000-000000000321',
        '{"expedition":"Поход к берегам Фризии"}'::jsonb
    ),
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '32 days',
        'SHIPBUILDER', 'SHIP_STAGE_COMPLETED', 'SHIP',
        '00000000-0000-0000-0000-000000000402',
        '{"completedStage":3}'::jsonb
    );

insert into settlement(id, name) values
    ('00000000-0000-0000-0000-000000000002', 'Бирка');

insert into app_user(id, display_name) values
    ('00000000-0000-0000-0000-000000000108', 'Эрик Биркский');

insert into user_account(user_id, username, password_salt, password_hash) values
    (
        '00000000-0000-0000-0000-000000000108',
        'erik',
        decode('405162738495a6b7c8d9eafb0c1d2e3f', 'hex'),
        decode('750599f1b8f69019b30ca646ef8cdce6a95f51f2339e4b27bacf8a26ad500f23', 'hex')
    );

insert into settlement_membership(settlement_id, user_id, member_role) values
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000108', 'JARL');

insert into warehouse_stock(settlement_id, resource, quantity) values
    ('00000000-0000-0000-0000-000000000002', 'WOOD', 45),
    ('00000000-0000-0000-0000-000000000002', 'CLOTH', 12),
    ('00000000-0000-0000-0000-000000000002', 'RESIN', 8),
    ('00000000-0000-0000-0000-000000000002', 'GOLD', 10),
    ('00000000-0000-0000-0000-000000000002', 'PROVISIONS', 35),
    ('00000000-0000-0000-0000-000000000002', 'THRALLS', 0);

insert into expedition(
    id, settlement_id, name, target, status, planned_departure, ship_name, required_capacity
) values (
    '00000000-0000-0000-0000-000000000205',
    '00000000-0000-0000-0000-000000000002',
    'Поход к Готланду', 'Торговая гавань Висбю', 'PREPARATION', '2026-10-18', 'Ледяной сокол', 20
);

insert into ship(id, settlement_id, name, stage, blessed, ship_type_code) values
    ('00000000-0000-0000-0000-000000000406', '00000000-0000-0000-0000-000000000002', 'Ледяной сокол', 4, true, 'KNOERR');

insert into expedition_ship(expedition_id, ship_id) values
    ('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000406');

insert into audit_event(
    settlement_id, happened_at, actor_role, event_type, aggregate_type, aggregate_id, details
) values (
    '00000000-0000-0000-0000-000000000002', now() - interval '2 days',
    'JARL', 'EXPEDITION_PLANNED', 'EXPEDITION',
    '00000000-0000-0000-0000-000000000205',
    '{"target":"Торговая гавань Висбю"}'::jsonb
);

alter table expedition drop column ship_name;
