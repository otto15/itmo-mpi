insert into expedition(
    id, settlement_id, name, target, status, planned_departure, ship_name,
    version, finalized_at, loot_gold, loot_provisions, loot_thralls
)
values
    (
        '00000000-0000-0000-0000-000000000203',
        '00000000-0000-0000-0000-000000000001',
        'Поход к берегам Фризии', 'Торговая гавань Дорестада', 'COMPLETED',
        '2026-07-18', 'Ворон', 1, now() - interval '45 days', 80, 35, 6
    ),
    (
        '00000000-0000-0000-0000-000000000204',
        '00000000-0000-0000-0000-000000000001',
        'Поход на остров Мэн', 'Крепость у Дугласа', 'COMPLETED',
        '2026-08-09', 'Морской волк', 1, now() - interval '20 days', 120, 60, 12
    )
on conflict (id) do nothing;

insert into audit_event(
    settlement_id, happened_at, actor_role, event_type,
    aggregate_type, aggregate_id, details
)
values
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '45 days',
        'JARL', 'EXPEDITION_FINALIZED', 'EXPEDITION',
        '00000000-0000-0000-0000-000000000203',
        '{"summary":"Первый успешный поход сезона"}'::jsonb
    ),
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '25 days',
        'PRIEST', 'SHIP_BLESSED', 'SHIP',
        '00000000-0000-0000-0000-000000000402',
        '{"shipName":"Морской волк"}'::jsonb
    ),
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '20 days',
        'JARL', 'EXPEDITION_FINALIZED', 'EXPEDITION',
        '00000000-0000-0000-0000-000000000204',
        '{"summary":"Итоги похода утверждены"}'::jsonb
    ),
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '72 hours',
        'SHIPBUILDER', 'SHIP_STAGE_COMPLETED', 'SHIP',
        '00000000-0000-0000-0000-000000000401',
        '{"completedStage":0}'::jsonb
    ),
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '36 hours',
        'WARRIOR', 'PARTICIPATION_CONFIRMED', 'CREW_ASSIGNMENT',
        '00000000-0000-0000-0000-000000000311',
        '{"expedition":"Поход к берегам Уэссекса"}'::jsonb
    ),
    (
        '00000000-0000-0000-0000-000000000001', now() - interval '12 hours',
        'JARL', 'CREW_MEMBER_ASSIGNED', 'EXPEDITION',
        '00000000-0000-0000-0000-000000000202',
        '{"assignmentId":"00000000-0000-0000-0000-000000000301"}'::jsonb
    );
