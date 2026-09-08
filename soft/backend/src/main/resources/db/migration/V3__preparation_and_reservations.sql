alter table expedition add column started_at timestamptz;
alter table expedition add column departure_snapshot jsonb;
alter table audit_event add column actor_user_id bigint references app_user(id);
alter table audit_event drop constraint audit_event_actor_role_check;
alter table audit_event add constraint audit_event_actor_role_check
    check (actor_role in ('JARL', 'WARRIOR', 'SHIPBUILDER', 'PRIEST', 'SYSTEM'));

create table expedition_route_point (
    expedition_id bigint not null references expedition(id) on delete cascade,
    position integer not null check (position >= 0),
    name varchar(160) not null,
    distance_km integer not null check (distance_km >= 0),
    primary key (expedition_id, position)
);

create table expedition_resource_requirement (
    expedition_id bigint not null references expedition(id) on delete cascade,
    resource varchar(32) not null,
    quantity integer not null check (quantity > 0),
    primary key (expedition_id, resource)
);

create table resource_reservation (
    id bigserial primary key,
    settlement_id bigint not null references settlement(id),
    expedition_id bigint references expedition(id) on delete cascade,
    ship_id bigint references ship(id) on delete cascade,
    ship_stage integer check (ship_stage between 0 and 3),
    status varchar(16) not null default 'ACTIVE' check (status in ('ACTIVE', 'CONSUMED', 'RELEASED')),
    created_by bigint not null references app_user(id),
    created_at timestamptz not null default clock_timestamp(),
    expires_at timestamptz not null,
    closed_at timestamptz,
    check ((expedition_id is not null and ship_id is null and ship_stage is null)
        or (expedition_id is null and ship_id is not null and ship_stage is not null)),
    check (expires_at > created_at)
);
create index reservation_expiry on resource_reservation(expires_at) where status = 'ACTIVE';
create index reservation_settlement on resource_reservation(settlement_id) where status = 'ACTIVE';
create table resource_reservation_item (
    reservation_id bigint not null references resource_reservation(id) on delete cascade,
    resource varchar(32) not null,
    quantity integer not null check (quantity > 0),
    primary key (reservation_id, resource)
);

create function expedition_departure_data(expedition_key bigint) returns jsonb
language sql stable as $$
    select jsonb_build_object(
        'crew', coalesce((select jsonb_agg(jsonb_build_object('id', c.id, 'userId', c.user_id,
            'name', u.display_name, 'role', c.expedition_role) order by c.id)
            from crew_assignment c join app_user u on u.id = c.user_id
            where c.expedition_id = expedition_key and c.participation_status = 'CONFIRMED'), '[]'::jsonb),
        'fleet', coalesce((select jsonb_agg(jsonb_build_object('id', s.id, 'name', s.name,
            'type', t.name, 'capacity', t.capacity) order by s.id)
            from expedition_ship es join ship s on s.id = es.ship_id
            join ship_type t on t.code = s.ship_type_code where es.expedition_id = expedition_key), '[]'::jsonb),
        'route', coalesce((select jsonb_agg(jsonb_build_object('name', name, 'distanceKm', distance_km)
            order by position) from expedition_route_point where expedition_id = expedition_key), '[]'::jsonb),
        'resources', coalesce((select jsonb_agg(jsonb_build_object('resource', resource, 'quantity', quantity)
            order by resource) from expedition_resource_requirement where expedition_id = expedition_key), '[]'::jsonb))
$$;

-- Existing demonstration voyages keep their data; old departure times are not invented.
insert into expedition_route_point
select e.id, 0, s.name, 0 from expedition e join settlement s on s.id = e.settlement_id;
insert into expedition_route_point
select id, 1, target, case id when 205 then 240 when 207 then 650 when 208 then 950 else 800 end
from expedition;
insert into expedition_resource_requirement select id, 'PROVISIONS', 20 from expedition where status = 'PREPARATION';
update expedition set departure_snapshot = expedition_departure_data(id)
where status in ('SAILING', 'COMPLETED');

create function protect_departure_snapshot() returns trigger language plpgsql as $$
begin
    if old.departure_snapshot is not null and
       (new.departure_snapshot is distinct from old.departure_snapshot or new.started_at is distinct from old.started_at) then
        raise exception 'Departure snapshot is immutable';
    end if;
    return new;
end $$;
create trigger departure_snapshot_immutable before update on expedition
for each row execute function protect_departure_snapshot();

create function protect_finalized_details() returns trigger language plpgsql as $$
declare old_id bigint; new_id bigint;
begin
    if current_setting('drakkar.demo_reset', true) = 'on' then
        return case when tg_op = 'DELETE' then old else new end;
    end if;
    if tg_op <> 'INSERT' then old_id := old.expedition_id; end if;
    if tg_op <> 'DELETE' then new_id := new.expedition_id; end if;
    perform 1 from expedition where id in (old_id, new_id) order by id for update;
    if exists (select 1 from expedition where id in (old_id, new_id) and finalized_at is not null) then
        raise exception 'Finalized expedition details are immutable';
    end if;
    return case when tg_op = 'DELETE' then old else new end;
end $$;
create trigger finalized_losses_immutable before insert or update or delete on crew_assignment
for each row execute function protect_finalized_details();
create trigger finalized_allocation_immutable before insert or update or delete on wergild_allocation
for each row execute function protect_finalized_details();
