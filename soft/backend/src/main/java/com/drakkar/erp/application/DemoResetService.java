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
        UUID ship = UUID.fromString("00000000-0000-0000-0000-000000000401");

        jdbc.update("""
                insert into expedition(id, settlement_id, name, target, status, planned_departure, ship_name)
                values (?, ?, ?, ?, 'SAILING', ?, ?)
                """, sailing, settlementId, "Поход к берегам Уэссекса", "Аббатство и торговый порт",
                LocalDate.of(2026, 9, 14), "Морской змей");
        jdbc.update("""
                insert into expedition(id, settlement_id, name, target, status, planned_departure, ship_name)
                values (?, ?, ?, ?, 'PREPARATION', ?, ?)
                """, preparation, settlementId, "Экспедиция в Нортумбрию", "Монастырь Линдисфарн",
                LocalDate.of(2026, 10, 2), "Северный ветер");

        addCrew(UUID.fromString("00000000-0000-0000-0000-000000000301"),
                preparation, HALVDAN, "рулевой", "PENDING");
        addCrew(UUID.fromString("00000000-0000-0000-0000-000000000311"),
                sailing, BJORN, "херсир", "CONFIRMED");
        addCrew(UUID.fromString("00000000-0000-0000-0000-000000000312"),
                sailing, IVAR, "щитоносец", "CONFIRMED");
        addCrew(UUID.fromString("00000000-0000-0000-0000-000000000313"),
                sailing, FLOKI, "корабельный мастер", "CONFIRMED");

        for (var stock : List.of(
                new Stock("WOOD", 120), new Stock("CLOTH", 35), new Stock("RESIN", 22),
                new Stock("GOLD", 40), new Stock("PROVISIONS", 90), new Stock("THRALLS", 0))) {
            jdbc.update("""
                    insert into warehouse_stock(settlement_id, resource, quantity)
                    values (?, ?, ?)
                    """, settlementId, stock.resource(), stock.quantity());
        }

        jdbc.update("""
                insert into ship(id, settlement_id, name, stage, blessed)
                values (?, ?, 'Северный ветер', 1, false)
                """, ship, settlementId);
        addRequirement(ship, 0, "WOOD", 30);
        addRequirement(ship, 1, "WOOD", 60);
        addRequirement(ship, 1, "RESIN", 10);
        addRequirement(ship, 2, "WOOD", 25);
        addRequirement(ship, 2, "RESIN", 8);
        addRequirement(ship, 3, "CLOTH", 20);
        addRequirement(ship, 3, "RESIN", 4);
    }

    private void addCrew(UUID id, UUID expeditionId, UUID userId, String role, String status) {
        jdbc.update("""
                insert into crew_assignment(id, expedition_id, user_id, expedition_role, participation_status)
                values (?, ?, ?, ?, ?)
                """, id, expeditionId, userId, role, status);
    }

    private void addRequirement(UUID shipId, int stage, String resource, int quantity) {
        jdbc.update("""
                insert into ship_stage_requirement(ship_id, stage, resource, quantity)
                values (?, ?, ?, ?)
                """, shipId, stage, resource, quantity);
    }

    private record Stock(String resource, int quantity) {
    }
}
