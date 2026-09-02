insert into settlement(id, name) values
    (1, 'Каттегат'),
    (2, 'Бирка');

insert into app_user(
    id, display_name, username, password_salt, password_hash
) values
    (
        101, 'Бьёрн Железнобокий', 'bjorn',
        decode('415263748596a7b8c9daebfc0d1e2f30', 'hex'),
        decode('5b9c0f6cb929743cd49d1e792c099fcb798ae2e7371cbdaf110b5086af0ff115', 'hex')
    ),
    (
        102, 'Ивар Бескостный', 'ivar',
        decode('5263748596a7b8c9daebfc0d1e2f3041', 'hex'),
        decode('d0435ffa2ed80a3e49de5ee3bb3224d3488b4576ed94618aa2eb7c75b51421a3', 'hex')
    ),
    (
        103, 'Флоки', 'floki',
        decode('2031425364758697a8b9cadbecfd0e1f', 'hex'),
        decode('4d5d5d686aa869c02ffb6fff89550d6c6b1a8dc4b7e4699e45f230ecf5d2ac22', 'hex')
    ),
    (
        104, 'Хальвдан', 'halvdan',
        decode('102132435465768798a9bacbdcedfe0f', 'hex'),
        decode('d3b7bdc12e01b7edb921a46dc36041acafac1f946bc22c29b9db2e2575e37dba', 'hex')
    ),
    (
        105, 'Торстейн Красный', 'thorstein',
        decode('63748596a7b8c9daebfc0d1e2f304152', 'hex'),
        decode('76d1ee6f413f0119d456a08e2d109ae6fce67f20db7a67a82079aacbaa3c47d2', 'hex')
    ),
    (
        106, 'Рагнар Лодброк', 'ragnar',
        decode('00112233445566778899aabbccddeeff', 'hex'),
        decode('dbac41204804d097a13f438fcd1886481b1e0098f099415990b59c63efe59393', 'hex')
    ),
    (
        107, 'Годи Уппсалы', 'godi',
        decode('30415263748596a7b8c9daebfc0d1e2f', 'hex'),
        decode('ed7f7eb113babdc880f2cd8a186e284c43486eb85ff8de58c0968e6098c1b002', 'hex')
    ),
    (
        108, 'Эрик Биркский', 'erik',
        decode('405162738495a6b7c8d9eafb0c1d2e3f', 'hex'),
        decode('750599f1b8f69019b30ca646ef8cdce6a95f51f2339e4b27bacf8a26ad500f23', 'hex')
    ),
    (
        109, 'Ульф Белый', 'ulf',
        decode('748596a7b8c9daebfc0d1e2f30415263', 'hex'),
        decode('441672ae97ac34df19e841acca46138acb8fbe1246557b57fb9f6aeed69cf8f8', 'hex')
    ),
    (
        110, 'Астрид Эйриксдоттир', 'astrid',
        decode('8596a7b8c9daebfc0d1e2f3041526374', 'hex'),
        decode('033504cf334f8c0ec05ed42cbbe76998c4a295471a3d3d211dbb3a673e8d06c3', 'hex')
    ),
    (
        111, 'Сигурд Кольценосец', 'sigurd',
        decode('96a7b8c9daebfc0d1e2f304152637485', 'hex'),
        decode('6f8d9570e8d6d5030fd284286f8e943c6276c7e9f7f31b2400727415c13350e1', 'hex')
    ),
    (
        112, 'Эйнар Топор', 'einar',
        decode('a7b8c9daebfc0d1e2f30415263748596', 'hex'),
        decode('636311773730f03027474b0e4cdc1dc6b88fc911ebcb23e14b540e402073df5f', 'hex')
    );

insert into settlement_membership(settlement_id, user_id, member_role) values
    (1, 101, 'WARRIOR'),
    (1, 102, 'WARRIOR'),
    (1, 103, 'SHIPBUILDER'),
    (1, 104, 'WARRIOR'),
    (1, 105, 'WARRIOR'),
    (1, 106, 'JARL'),
    (1, 107, 'PRIEST'),
    (2, 108, 'JARL'),
    (1, 109, 'WARRIOR'),
    (1, 110, 'WARRIOR'),
    (1, 111, 'WARRIOR'),
    (1, 112, 'WARRIOR');

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

insert into warehouse_stock(settlement_id, resource, quantity) values
    (1, 'WOOD', 120),
    (1, 'CLOTH', 35),
    (1, 'RESIN', 22),
    (1, 'GOLD', 40),
    (1, 'PROVISIONS', 90),
    (1, 'THRALLS', 0),
    (2, 'WOOD', 45),
    (2, 'CLOTH', 12),
    (2, 'RESIN', 8),
    (2, 'GOLD', 10),
    (2, 'PROVISIONS', 35),
    (2, 'THRALLS', 0);

insert into expedition(
    id, settlement_id, name, target, status, planned_departure,
    version, finalized_at, loot_gold, loot_provisions, loot_thralls
) values
    (201, 1, 'Поход к берегам Уэссекса', 'Аббатство и торговый порт',
        'SAILING', '2026-09-14', 0, null, null, null, null),
    (202, 1, 'Экспедиция в Нортумбрию', 'Монастырь Линдисфарн',
        'PREPARATION', '2026-10-02', 0, null, null, null, null),
    (203, 1, 'Поход к берегам Фризии', 'Торговая гавань Дорестада',
        'COMPLETED', '2026-07-18', 1, now() - interval '45 days', 80, 35, 6),
    (204, 1, 'Поход на остров Мэн', 'Крепость у Дугласа',
        'COMPLETED', '2026-08-09', 1, now() - interval '20 days', 120, 60, 12),
    (205, 2, 'Поход к Готланду', 'Торговая гавань Висбю',
        'PREPARATION', '2026-10-18', 0, null, null, null, null),
    (206, 1, 'Поход к Оркнейским островам', 'Гавань на острове Мейнленд',
        'SAILING', '2026-09-22', 0, null, null, null, null),
    (207, 1, 'Поход к Шетландским островам', 'Поселение у пролива Брессей',
        'PREPARATION', '2026-10-12', 0, null, null, null, null),
    (208, 1, 'Разведка Фарерских островов', 'Бухта Торсхавна',
        'PREPARATION', '2026-10-20', 0, null, null, null, null);

insert into crew_assignment(
    id, expedition_id, user_id, expedition_role, participation_status
) values
    (301, 202, 104, 'рулевой', 'PENDING'),
    (311, 201, 101, 'херсир', 'CONFIRMED'),
    (312, 201, 102, 'щитоносец', 'CONFIRMED'),
    (313, 201, 105, 'корабельный мастер', 'CONFIRMED'),
    (321, 203, 104, 'разведчик', 'CONFIRMED'),
    (322, 204, 101, 'херсир', 'CONFIRMED'),
    (323, 206, 109, 'рулевой', 'CONFIRMED'),
    (324, 207, 110, 'херсир', 'CONFIRMED'),
    (325, 208, 111, 'разведчик', 'PENDING');

insert into ship(id, settlement_id, name, ship_type_code, stage, blessed) values
    (401, 1, 'Северный ветер', 'DRAKKAR', 1, false),
    (402, 1, 'Морской волк', 'DRAKKAR', 4, true),
    (403, 1, 'Морской змей', 'DRAKKAR', 4, true),
    (404, 1, 'Волчий клык', 'KNOERR', 4, true),
    (405, 1, 'Ворон', 'KNOERR', 4, true),
    (406, 2, 'Ледяной сокол', 'KNOERR', 4, true),
    (407, 1, 'Буревестник', 'DRAKKAR', 4, true),
    (408, 1, 'Ледяная чайка', 'KNOERR', 4, true);

insert into ship_stage_requirement(ship_id, stage, resource, quantity)
select 401, stage, resource, quantity
  from ship_type_requirement
 where ship_type_code = 'DRAKKAR';

insert into expedition_ship(expedition_id, ship_id, assigned_at) values
    (201, 403, now() - interval '30 days'),
    (201, 404, now() - interval '30 days'),
    (202, 405, now() - interval '8 days'),
    (202, 401, now() - interval '4 days'),
    (203, 405, now() - interval '80 days'),
    (204, 402, now() - interval '50 days'),
    (205, 406, now() - interval '5 days'),
    (206, 407, now() - interval '6 days'),
    (207, 408, now() - interval '2 days');

insert into ship_build_request(
    id, settlement_id, expedition_id, ship_type_code, ship_id, requested_by, status, created_at
) values (
    501, 1, 202, 'DRAKKAR', 401, 106, 'IN_CONSTRUCTION', now() - interval '4 days'
);

insert into audit_event(
    settlement_id, happened_at, actor_role, event_type,
    aggregate_type, aggregate_id, details
) values
    (1, now() - interval '45 days', 'JARL', 'EXPEDITION_FINALIZED',
        'EXPEDITION', 203, '{"summary":"Первый успешный поход сезона"}'::jsonb),
    (1, now() - interval '50 days', 'WARRIOR', 'PARTICIPATION_CONFIRMED',
        'CREW_ASSIGNMENT', 321, '{"expedition":"Поход к берегам Фризии"}'::jsonb),
    (1, now() - interval '32 days', 'SHIPBUILDER', 'SHIP_STAGE_COMPLETED',
        'SHIP', 402, '{"completedStage":3}'::jsonb),
    (1, now() - interval '25 days', 'PRIEST', 'SHIP_BLESSED',
        'SHIP', 402, '{"shipName":"Морской волк"}'::jsonb),
    (1, now() - interval '20 days', 'JARL', 'EXPEDITION_FINALIZED',
        'EXPEDITION', 204, '{"summary":"Итоги похода утверждены"}'::jsonb),
    (1, now() - interval '72 hours', 'SHIPBUILDER', 'SHIP_STAGE_COMPLETED',
        'SHIP', 401, '{"completedStage":0}'::jsonb),
    (1, now() - interval '36 hours', 'WARRIOR', 'PARTICIPATION_CONFIRMED',
        'CREW_ASSIGNMENT', 311, '{"expedition":"Поход к берегам Уэссекса"}'::jsonb),
    (1, now() - interval '12 hours', 'JARL', 'CREW_MEMBER_ASSIGNED',
        'EXPEDITION', 202, '{"assignmentId":301}'::jsonb),
    (1, now() - interval '6 days', 'JARL', 'EXPEDITION_STARTED',
        'EXPEDITION', 206, '{"readyCapacity":40,"crewSize":1}'::jsonb),
    (1, now() - interval '2 days', 'JARL', 'EXPEDITION_PLANNED',
        'EXPEDITION', 207, '{"target":"Поселение у пролива Брессей"}'::jsonb),
    (1, now() - interval '1 day', 'WARRIOR', 'PARTICIPATION_CONFIRMED',
        'CREW_ASSIGNMENT', 324, '{"expedition":"Поход к Шетландским островам"}'::jsonb),
    (1, now() - interval '12 hours', 'JARL', 'EXPEDITION_PLANNED',
        'EXPEDITION', 208, '{"target":"Бухта Торсхавна","invitedCrew":1}'::jsonb),
    (2, now() - interval '2 days', 'JARL', 'EXPEDITION_PLANNED',
        'EXPEDITION', 205, '{"target":"Торговая гавань Висбю"}'::jsonb);

select setval(pg_get_serial_sequence('settlement', 'id'), (select max(id) from settlement));
select setval(pg_get_serial_sequence('app_user', 'id'), (select max(id) from app_user));
select setval(pg_get_serial_sequence('expedition', 'id'), (select max(id) from expedition));
select setval(pg_get_serial_sequence('crew_assignment', 'id'), (select max(id) from crew_assignment));
select setval(pg_get_serial_sequence('ship', 'id'), (select max(id) from ship));
select setval(pg_get_serial_sequence('ship_build_request', 'id'), (select max(id) from ship_build_request));
