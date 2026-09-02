package com.drakkar.erp.application;

import com.drakkar.erp.domain.DomainException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DemoResetService {
    public static final UUID DEFAULT_SETTLEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HALVDAN = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final UUID BJORN = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID IVAR = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID FLOKI = UUID.fromString("00000000-0000-0000-0000-000000000103");

    private final JdbcTemplate jdbc;

    public DemoResetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void reset() {
        reset(DEFAULT_SETTLEMENT_ID);
    }

    @Transactional
    public void reset(UUID settlementId) {
        if (!DEFAULT_SETTLEMENT_ID.equals(settlementId)) {
            throw DomainException.conflict(
                    "DEMO_RESET_NOT_AVAILABLE",
                    "Исходный набор данных доступен только для демонстрационного поселения");
        }
        jdbc.update("delete from audit_event where settlement_id = ?", settlementId);
        jdbc.update("""
                delete from wergild_allocation wa
                 using expedition e
                 where wa.expedition_id = e.id and e.settlement_id = ?
                """, settlementId);
        jdbc.update("""
                delete from crew_assignment ca
                 using expedition e
                 where ca.expedition_id = e.id and e.settlement_id = ?
                """, settlementId);
        jdbc.update("delete from ship_build_request where settlement_id = ?", settlementId);
        jdbc.update("""
                delete from expedition_ship es
                 using expedition e
                 where es.expedition_id = e.id and e.settlement_id = ?
                """, settlementId);
        jdbc.update("""
                delete from ship_stage_requirement sr
                 using ship s
                 where sr.ship_id = s.id and s.settlement_id = ?
                """, settlementId);
        jdbc.update("delete from ship where settlement_id = ?", settlementId);
        jdbc.update("delete from expedition where settlement_id = ?", settlementId);
        jdbc.update("delete from warehouse_stock where settlement_id = ?", settlementId);

        UUID sailing = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID preparation = UUID.fromString("00000000-0000-0000-0000-000000000202");
        UUID completedFrisia = UUID.fromString("00000000-0000-0000-0000-000000000203");
        UUID completedMan = UUID.fromString("00000000-0000-0000-0000-000000000204");
        UUID buildingShip = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID seaWolf = UUID.fromString("00000000-0000-0000-0000-000000000402");
        UUID seaSerpent = UUID.fromString("00000000-0000-0000-0000-000000000403");
        UUID wolfFang = UUID.fromString("00000000-0000-0000-0000-000000000404");
        UUID raven = UUID.fromString("00000000-0000-0000-0000-000000000405");

        jdbc.update("""
                insert into expedition(id, settlement_id, name, target, status, planned_departure, required_capacity)
                values (?, ?, ?, ?, 'SAILING', ?, 55)
                """, sailing, settlementId, "Поход к берегам Уэссекса", "Аббатство и торговый порт",
                LocalDate.of(2026, 9, 14));
        jdbc.update("""
                insert into expedition(id, settlement_id, name, target, status, planned_departure, required_capacity)
                values (?, ?, ?, ?, 'PREPARATION', ?, 70)
                """, preparation, settlementId, "Экспедиция в Нортумбрию", "Монастырь Линдисфарн",
                LocalDate.of(2026, 10, 2));
        addCompletedExpedition(completedFrisia, settlementId,
                "Поход к берегам Фризии", "Торговая гавань Дорестада",
                LocalDate.of(2026, 7, 18), 20, 80, 35, 6, 45);
        addCompletedExpedition(completedMan, settlementId,
                "Поход на остров Мэн", "Крепость у Дугласа",
                LocalDate.of(2026, 8, 9), 40, 120, 60, 12, 20);

        addCrew(UUID.fromString("00000000-0000-0000-0000-000000000301"),
                preparation, HALVDAN, "рулевой", "PENDING");
        addCrew(UUID.fromString("00000000-0000-0000-0000-000000000311"),
                sailing, BJORN, "херсир", "CONFIRMED");
        addCrew(UUID.fromString("00000000-0000-0000-0000-000000000312"),
                sailing, IVAR, "щитоносец", "CONFIRMED");
        addCrew(UUID.fromString("00000000-0000-0000-0000-000000000313"),
                sailing, FLOKI, "корабельный мастер", "CONFIRMED");
        addCrew(UUID.fromString("00000000-0000-0000-0000-000000000321"),
                completedFrisia, HALVDAN, "разведчик", "CONFIRMED");
        addCrew(UUID.fromString("00000000-0000-0000-0000-000000000322"),
                completedMan, BJORN, "херсир", "CONFIRMED");

        for (var stock : List.of(
                new Stock("WOOD", 120), new Stock("CLOTH", 35), new Stock("RESIN", 22),
                new Stock("GOLD", 40), new Stock("PROVISIONS", 90), new Stock("THRALLS", 0))) {
            jdbc.update("""
                    insert into warehouse_stock(settlement_id, resource, quantity)
                    values (?, ?, ?)
                    """, settlementId, stock.resource(), stock.quantity());
        }

        jdbc.update("""
                insert into ship(id, settlement_id, name, ship_type_code, stage, blessed)
                values (?, ?, 'Северный ветер', 'DRAKKAR', 1, false)
                """, buildingShip, settlementId);
        addReadyShip(seaWolf, settlementId, "Морской волк", "DRAKKAR");
        addReadyShip(seaSerpent, settlementId, "Морской змей", "DRAKKAR");
        addReadyShip(wolfFang, settlementId, "Волчий клык", "KNOERR");
        addReadyShip(raven, settlementId, "Ворон", "KNOERR");
        jdbc.update("""
                insert into ship_stage_requirement(ship_id, stage, resource, quantity)
                select ?, stage, resource, quantity
                  from ship_type_requirement where ship_type_code = 'DRAKKAR'
                """, buildingShip);

        addShipToExpedition(sailing, seaSerpent);
        addShipToExpedition(sailing, wolfFang);
        addShipToExpedition(preparation, raven);
        addShipToExpedition(preparation, buildingShip);
        addShipToExpedition(completedFrisia, raven);
        addShipToExpedition(completedMan, seaWolf);
        jdbc.update("""
                insert into ship_build_request(
                    id, settlement_id, expedition_id, ship_type_code, ship_id, requested_by, status, created_at
                ) values (?, ?, ?, 'DRAKKAR', ?, ?, 'IN_CONSTRUCTION', now() - interval '4 days')
                """, UUID.fromString("00000000-0000-0000-0000-000000000501"), settlementId,
                preparation, buildingShip, UUID.fromString("00000000-0000-0000-0000-000000000106"));

        addAudit(settlementId, "JARL", "EXPEDITION_FINALIZED", "EXPEDITION", completedFrisia, 1080,
                "{\"summary\":\"Первый успешный поход сезона\"}");
        addAudit(settlementId, "WARRIOR", "PARTICIPATION_CONFIRMED", "CREW_ASSIGNMENT",
                UUID.fromString("00000000-0000-0000-0000-000000000321"), 1200,
                "{\"expedition\":\"Поход к берегам Фризии\"}");
        addAudit(settlementId, "SHIPBUILDER", "SHIP_STAGE_COMPLETED", "SHIP", seaWolf, 768,
                "{\"completedStage\":3}");
        addAudit(settlementId, "PRIEST", "SHIP_BLESSED", "SHIP",
                UUID.fromString("00000000-0000-0000-0000-000000000402"), 600,
                "{\"shipName\":\"Морской волк\"}");
        addAudit(settlementId, "JARL", "EXPEDITION_FINALIZED", "EXPEDITION", completedMan, 480,
                "{\"summary\":\"Итоги похода утверждены\"}");
        addAudit(settlementId, "SHIPBUILDER", "SHIP_STAGE_COMPLETED", "SHIP", buildingShip, 72,
                "{\"completedStage\":0}");
        addAudit(settlementId, "WARRIOR", "PARTICIPATION_CONFIRMED", "CREW_ASSIGNMENT",
                UUID.fromString("00000000-0000-0000-0000-000000000311"), 36,
                "{\"expedition\":\"Поход к берегам Уэссекса\"}");
        addAudit(settlementId, "JARL", "CREW_MEMBER_ASSIGNED", "EXPEDITION", preparation, 12,
                "{\"assignmentId\":\"00000000-0000-0000-0000-000000000301\"}");
    }

    private void addCompletedExpedition(
            UUID id,
            UUID settlementId,
            String name,
            String target,
            LocalDate departure,
            int requiredCapacity,
            int gold,
            int provisions,
            int thralls,
            int finalizedDaysAgo
    ) {
        jdbc.update("""
                insert into expedition(
                    id, settlement_id, name, target, status, planned_departure, required_capacity,
                    version, finalized_at, loot_gold, loot_provisions, loot_thralls
                )
                values (?, ?, ?, ?, 'COMPLETED', ?, ?, 1,
                        now() - (? * interval '1 day'), ?, ?, ?)
                """, id, settlementId, name, target, departure, requiredCapacity,
                finalizedDaysAgo, gold, provisions, thralls);
    }

    private void addAudit(
            UUID settlementId,
            String actorRole,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            int hoursAgo,
            String details
    ) {
        jdbc.update("""
                insert into audit_event(
                    settlement_id, happened_at, actor_role, event_type,
                    aggregate_type, aggregate_id, details
                )
                values (?, now() - (? * interval '1 hour'), ?, ?, ?, ?, cast(? as jsonb))
                """, settlementId, hoursAgo, actorRole, eventType, aggregateType, aggregateId, details);
    }

    private void addCrew(UUID id, UUID expeditionId, UUID userId, String role, String status) {
        jdbc.update("""
                insert into crew_assignment(id, expedition_id, user_id, expedition_role, participation_status)
                values (?, ?, ?, ?, ?)
                """, id, expeditionId, userId, role, status);
    }

    private void addReadyShip(UUID shipId, UUID settlementId, String name, String typeCode) {
        jdbc.update("""
                insert into ship(id, settlement_id, name, ship_type_code, stage, blessed)
                values (?, ?, ?, ?, 4, true)
                """, shipId, settlementId, name, typeCode);
    }

    private void addShipToExpedition(UUID expeditionId, UUID shipId) {
        jdbc.update("""
                insert into expedition_ship(expedition_id, ship_id) values (?, ?)
                """, expeditionId, shipId);
    }

    private record Stock(String resource, int quantity) {
    }
}
