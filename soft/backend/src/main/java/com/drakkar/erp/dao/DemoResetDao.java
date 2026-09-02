package com.drakkar.erp.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class DemoResetDao {
    private static final Long HALVDAN = 104L;
    private static final Long BJORN = 101L;
    private static final Long IVAR = 102L;
    private static final Long FLOKI = 103L;
    private static final Long ULF = 109L;
    private static final Long ASTRID = 110L;
    private static final Long SIGURD = 111L;

    private final JdbcTemplate jdbc;

    public DemoResetDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void reset(Long settlementId) {
        clearSettlement(settlementId);

        Long sailing = 201L;
        Long preparation = 202L;
        Long completedFrisia = 203L;
        Long completedMan = 204L;
        Long sailingOrkney = 206L;
        Long readyPreparation = 207L;
        Long faroePreparation = 208L;
        Long buildingShip = 401L;
        Long seaWolf = 402L;
        Long seaSerpent = 403L;
        Long wolfFang = 404L;
        Long raven = 405L;
        Long stormPetrel = 407L;
        Long iceGull = 408L;

        addExpedition(sailing, settlementId, "Поход к берегам Уэссекса",
                "Аббатство и торговый порт", "SAILING", LocalDate.of(2026, 9, 14));
        addExpedition(preparation, settlementId, "Экспедиция в Нортумбрию",
                "Монастырь Линдисфарн", "PREPARATION", LocalDate.of(2026, 10, 2));
        addExpedition(sailingOrkney, settlementId, "Поход к Оркнейским островам",
                "Гавань на острове Мейнленд", "SAILING", LocalDate.of(2026, 9, 22));
        addExpedition(readyPreparation, settlementId, "Поход к Шетландским островам",
                "Поселение у пролива Брессей", "PREPARATION", LocalDate.of(2026, 10, 12));
        addExpedition(faroePreparation, settlementId, "Разведка Фарерских островов",
                "Бухта Торсхавна", "PREPARATION", LocalDate.of(2026, 10, 20));
        addCompletedExpedition(completedFrisia, settlementId,
                "Поход к берегам Фризии", "Торговая гавань Дорестада",
                LocalDate.of(2026, 7, 18), 80, 35, 6, 45);
        addCompletedExpedition(completedMan, settlementId,
                "Поход на остров Мэн", "Крепость у Дугласа",
                LocalDate.of(2026, 8, 9), 120, 60, 12, 20);

        addCrew(301L, preparation, HALVDAN, "рулевой", "PENDING");
        addCrew(311L, sailing, BJORN, "херсир", "CONFIRMED");
        addCrew(312L, sailing, IVAR, "щитоносец", "CONFIRMED");
        addCrew(313L, sailing, FLOKI, "корабельный мастер", "CONFIRMED");
        addCrew(321L, completedFrisia, HALVDAN, "разведчик", "CONFIRMED");
        addCrew(322L, completedMan, BJORN, "херсир", "CONFIRMED");
        addCrew(323L, sailingOrkney, ULF, "рулевой", "CONFIRMED");
        addCrew(324L, readyPreparation, ASTRID, "херсир", "CONFIRMED");
        addCrew(325L, faroePreparation, SIGURD, "разведчик", "PENDING");

        for (var stock : List.of(
                new Stock("WOOD", 120), new Stock("CLOTH", 35), new Stock("RESIN", 22),
                new Stock("GOLD", 40), new Stock("PROVISIONS", 90), new Stock("THRALLS", 0))) {
            addStock(settlementId, stock);
        }

        jdbc.update("""
                insert into ship(id, settlement_id, name, ship_type_code, stage, blessed)
                values (?, ?, 'Северный ветер', 'DRAKKAR', 1, false)
                """, buildingShip, settlementId);
        addReadyShip(seaWolf, settlementId, "Морской волк", "DRAKKAR");
        addReadyShip(seaSerpent, settlementId, "Морской змей", "DRAKKAR");
        addReadyShip(wolfFang, settlementId, "Волчий клык", "KNOERR");
        addReadyShip(raven, settlementId, "Ворон", "KNOERR");
        addReadyShip(stormPetrel, settlementId, "Буревестник", "DRAKKAR");
        addReadyShip(iceGull, settlementId, "Ледяная чайка", "KNOERR");
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
        addShipToExpedition(sailingOrkney, stormPetrel);
        addShipToExpedition(readyPreparation, iceGull);
        jdbc.update("""
                insert into ship_build_request(
                    id, settlement_id, expedition_id, ship_type_code, ship_id, requested_by, status, created_at
                ) values (?, ?, ?, 'DRAKKAR', ?, ?, 'IN_CONSTRUCTION', now() - interval '4 days')
                """, 501L, settlementId, preparation, buildingShip, 106L);

        addAudit(settlementId, "JARL", "EXPEDITION_FINALIZED", "EXPEDITION", completedFrisia, 1080,
                "{\"summary\":\"Первый успешный поход сезона\"}");
        addAudit(settlementId, "WARRIOR", "PARTICIPATION_CONFIRMED", "CREW_ASSIGNMENT",
                321L, 1200, "{\"expedition\":\"Поход к берегам Фризии\"}");
        addAudit(settlementId, "SHIPBUILDER", "SHIP_STAGE_COMPLETED", "SHIP", seaWolf, 768,
                "{\"completedStage\":3}");
        addAudit(settlementId, "PRIEST", "SHIP_BLESSED", "SHIP", 402L, 600,
                "{\"shipName\":\"Морской волк\"}");
        addAudit(settlementId, "JARL", "EXPEDITION_FINALIZED", "EXPEDITION", completedMan, 480,
                "{\"summary\":\"Итоги похода утверждены\"}");
        addAudit(settlementId, "SHIPBUILDER", "SHIP_STAGE_COMPLETED", "SHIP", buildingShip, 72,
                "{\"completedStage\":0}");
        addAudit(settlementId, "WARRIOR", "PARTICIPATION_CONFIRMED", "CREW_ASSIGNMENT", 311L, 36,
                "{\"expedition\":\"Поход к берегам Уэссекса\"}");
        addAudit(settlementId, "JARL", "CREW_MEMBER_ASSIGNED", "EXPEDITION", preparation, 12,
                "{\"assignmentId\":301}");
        addAudit(settlementId, "JARL", "EXPEDITION_STARTED", "EXPEDITION", sailingOrkney, 144,
                "{\"readyCapacity\":40,\"crewSize\":1}");
        addAudit(settlementId, "JARL", "EXPEDITION_PLANNED", "EXPEDITION", readyPreparation, 48,
                "{\"target\":\"Поселение у пролива Брессей\"}");
        addAudit(settlementId, "WARRIOR", "PARTICIPATION_CONFIRMED", "CREW_ASSIGNMENT", 324L, 24,
                "{\"expedition\":\"Поход к Шетландским островам\"}");
        addAudit(settlementId, "JARL", "EXPEDITION_PLANNED", "EXPEDITION", faroePreparation, 12,
                "{\"target\":\"Бухта Торсхавна\",\"invitedCrew\":1}");
    }

    private void clearSettlement(Long settlementId) {
        jdbc.update("delete from audit_event where settlement_id = ?", settlementId);
        jdbc.update("""
                delete from wergild_allocation wa using expedition e
                 where wa.expedition_id = e.id and e.settlement_id = ?
                """, settlementId);
        jdbc.update("""
                delete from crew_assignment ca using expedition e
                 where ca.expedition_id = e.id and e.settlement_id = ?
                """, settlementId);
        jdbc.update("delete from ship_build_request where settlement_id = ?", settlementId);
        jdbc.update("""
                delete from expedition_ship es using expedition e
                 where es.expedition_id = e.id and e.settlement_id = ?
                """, settlementId);
        jdbc.update("""
                delete from ship_stage_requirement sr using ship s
                 where sr.ship_id = s.id and s.settlement_id = ?
                """, settlementId);
        jdbc.update("delete from ship where settlement_id = ?", settlementId);
        jdbc.update("delete from expedition where settlement_id = ?", settlementId);
        jdbc.update("delete from warehouse_stock where settlement_id = ?", settlementId);
    }

    private void addExpedition(
            Long id,
            Long settlementId,
            String name,
            String target,
            String status,
            LocalDate departure
    ) {
        jdbc.update("""
                insert into expedition(id, settlement_id, name, target, status, planned_departure)
                values (?, ?, ?, ?, ?, ?)
                """, id, settlementId, name, target, status, departure);
    }

    private void addCompletedExpedition(
            Long id,
            Long settlementId,
            String name,
            String target,
            LocalDate departure,
            int gold,
            int provisions,
            int thralls,
            int finalizedDaysAgo
    ) {
        jdbc.update("""
                insert into expedition(
                    id, settlement_id, name, target, status, planned_departure,
                    version, finalized_at, loot_gold, loot_provisions, loot_thralls
                )
                values (?, ?, ?, ?, 'COMPLETED', ?, 1,
                        now() - (? * interval '1 day'), ?, ?, ?)
                """, id, settlementId, name, target, departure,
                finalizedDaysAgo, gold, provisions, thralls);
    }

    private void addCrew(Long id, Long expeditionId, Long userId, String role, String status) {
        jdbc.update("""
                insert into crew_assignment(id, expedition_id, user_id, expedition_role, participation_status)
                values (?, ?, ?, ?, ?)
                """, id, expeditionId, userId, role, status);
    }

    private void addStock(Long settlementId, Stock stock) {
        jdbc.update("""
                insert into warehouse_stock(settlement_id, resource, quantity)
                values (?, ?, ?)
                """, settlementId, stock.resource(), stock.quantity());
    }

    private void addReadyShip(Long shipId, Long settlementId, String name, String typeCode) {
        jdbc.update("""
                insert into ship(id, settlement_id, name, ship_type_code, stage, blessed)
                values (?, ?, ?, ?, 4, true)
                """, shipId, settlementId, name, typeCode);
    }

    private void addShipToExpedition(Long expeditionId, Long shipId) {
        jdbc.update("""
                insert into expedition_ship(expedition_id, ship_id) values (?, ?)
                """, expeditionId, shipId);
    }

    private void addAudit(
            Long settlementId,
            String actorRole,
            String eventType,
            String aggregateType,
            Long aggregateId,
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

    private record Stock(String resource, int quantity) {
    }
}
