alter table ship_build_request alter column expedition_id drop not null;

insert into app_user(id, display_name) values
    (109, 'Ульф Белый'),
    (110, 'Астрид Эйриксдоттир');

insert into settlement_membership(settlement_id, user_id, member_role) values
    (1, 109, 'WARRIOR'),
    (1, 110, 'WARRIOR');

insert into expedition(
    id, settlement_id, name, target, status, planned_departure, required_capacity
) values
    (
        206,
        1,
        'Поход к Оркнейским островам', 'Гавань на острове Мейнленд',
        'SAILING', '2026-09-22', 40
    ),
    (
        207,
        1,
        'Поход к Шетландским островам', 'Поселение у пролива Брессей',
        'PREPARATION', '2026-10-12', 20
    );

insert into ship(id, settlement_id, name, ship_type_code, stage, blessed) values
    (407, 1, 'Буревестник', 'DRAKKAR', 4, true),
    (408, 1, 'Ледяная чайка', 'KNOERR', 4, true);

insert into expedition_ship(expedition_id, ship_id) values
    (206, 407),
    (207, 408);

insert into crew_assignment(id, expedition_id, user_id, expedition_role, participation_status) values
    (323, 206, 109, 'рулевой', 'CONFIRMED'),
    (324, 207, 110, 'херсир', 'CONFIRMED');

insert into audit_event(
    settlement_id, happened_at, actor_role, event_type, aggregate_type, aggregate_id, details
) values
    (
        1, now() - interval '6 days',
        'JARL', 'EXPEDITION_STARTED', 'EXPEDITION',
        206, '{"readyCapacity":40,"confirmedCrew":1}'::jsonb
    ),
    (
        1, now() - interval '2 days',
        'JARL', 'EXPEDITION_PLANNED', 'EXPEDITION',
        207, '{"target":"Поселение у пролива Брессей"}'::jsonb
    ),
    (
        1, now() - interval '1 day',
        'WARRIOR', 'PARTICIPATION_CONFIRMED', 'CREW_ASSIGNMENT',
        324, '{"expedition":"Поход к Шетландским островам"}'::jsonb
    );

-- Seed rows use readable fixed identifiers; continue every sequence after them.
select setval(pg_get_serial_sequence('settlement', 'id'), (select max(id) from settlement));
select setval(pg_get_serial_sequence('app_user', 'id'), (select max(id) from app_user));
select setval(pg_get_serial_sequence('expedition', 'id'), (select max(id) from expedition));
select setval(pg_get_serial_sequence('crew_assignment', 'id'), (select max(id) from crew_assignment));
select setval(pg_get_serial_sequence('ship', 'id'), (select max(id) from ship));
select setval(pg_get_serial_sequence('ship_build_request', 'id'), (select max(id) from ship_build_request));
