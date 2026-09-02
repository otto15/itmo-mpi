alter table ship_build_request alter column expedition_id drop not null;

insert into app_user(id, display_name) values
    ('00000000-0000-0000-0000-000000000109', 'Ульф Белый'),
    ('00000000-0000-0000-0000-000000000110', 'Астрид Эйриксдоттир');

insert into settlement_membership(settlement_id, user_id, member_role) values
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000109', 'WARRIOR'),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000110', 'WARRIOR');

insert into expedition(
    id, settlement_id, name, target, status, planned_departure, required_capacity
) values
    (
        '00000000-0000-0000-0000-000000000206',
        '00000000-0000-0000-0000-000000000001',
        'Поход к Оркнейским островам', 'Гавань на острове Мейнленд',
        'SAILING', '2026-09-22', 40
    ),
    (
        '00000000-0000-0000-0000-000000000207',
        '00000000-0000-0000-0000-000000000001',
        'Поход к Шетландским островам', 'Поселение у пролива Брессей',
        'PREPARATION', '2026-10-12', 20
    );

insert into ship(id, settlement_id, name, ship_type_code, stage, blessed) values
    ('00000000-0000-0000-0000-000000000407', '00000000-0000-0000-0000-000000000001', 'Буревестник', 'DRAKKAR', 4, true),
    ('00000000-0000-0000-0000-000000000408', '00000000-0000-0000-0000-000000000001', 'Ледяная чайка', 'KNOERR', 4, true);

insert into expedition_ship(expedition_id, ship_id) values
    ('00000000-0000-0000-0000-000000000206', '00000000-0000-0000-0000-000000000407'),
    ('00000000-0000-0000-0000-000000000207', '00000000-0000-0000-0000-000000000408');

insert into crew_assignment(id, expedition_id, user_id, expedition_role, participation_status) values
    ('00000000-0000-0000-0000-000000000323', '00000000-0000-0000-0000-000000000206', '00000000-0000-0000-0000-000000000109', 'рулевой', 'CONFIRMED'),
    ('00000000-0000-0000-0000-000000000324', '00000000-0000-0000-0000-000000000207', '00000000-0000-0000-0000-000000000110', 'херсир', 'CONFIRMED');

insert into audit_event(
    settlement_id, happened_at, actor_role, event_type, aggregate_type, aggregate_id, details
) values
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '6 days',
        'JARL', 'EXPEDITION_STARTED', 'EXPEDITION',
        '00000000-0000-0000-0000-000000000206', '{"readyCapacity":40,"confirmedCrew":1}'::jsonb
    ),
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '2 days',
        'JARL', 'EXPEDITION_PLANNED', 'EXPEDITION',
        '00000000-0000-0000-0000-000000000207', '{"target":"Поселение у пролива Брессей"}'::jsonb
    ),
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '1 day',
        'WARRIOR', 'PARTICIPATION_CONFIRMED', 'CREW_ASSIGNMENT',
        '00000000-0000-0000-0000-000000000324', '{"expedition":"Поход к Шетландским островам"}'::jsonb
    );
