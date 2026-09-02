package com.drakkar.erp.application;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.DomainException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ShipyardService {
    private record ShipRow(int stage, boolean blessed, int version) {
    }

    private record Requirement(String resource, int required, int available) {
    }

    private record ShipType(String code, int capacity) {
    }

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public ShipyardService(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void completeStage(AuthenticatedUser actor, Long shipId, ApiModels.CompleteStageRequest request) {
        ShipRow ship = jdbc.query("""
                select stage, blessed, version
                  from ship
                 where id = ? and settlement_id = ?
                   for update
                """, rs -> rs.next()
                ? new ShipRow(rs.getInt("stage"), rs.getBoolean("blessed"), rs.getInt("version"))
                : null, shipId, actor.settlementId());
        if (ship == null) {
            throw DomainException.notFound("Корабль");
        }
        if (ship.version() != request.expectedVersion()) {
            throw DomainException.conflict("STALE_SHIP_STAGE", "Этап корабля уже был изменён");
        }
        if (ship.stage() == 4) {
            throw DomainException.conflict("SHIP_ALREADY_COMPLETED", "Корабль уже готов к спуску");
        }
        if (ship.stage() == 3 && !ship.blessed()) {
            throw DomainException.conflict("BLESSING_REQUIRED", "Ожидается благословение Жреца");
        }

        List<Requirement> requirements = jdbc.query("""
                select r.resource, r.quantity as required, s.quantity as available
                  from ship_stage_requirement r
                  join warehouse_stock s
                    on s.resource = r.resource and s.settlement_id = ?
                 where r.ship_id = ? and r.stage = ?
                 order by r.resource
                   for update of s
                """, (rs, rowNum) -> new Requirement(
                rs.getString("resource"), rs.getInt("required"), rs.getInt("available")),
                actor.settlementId(), shipId, ship.stage());

        for (Requirement requirement : requirements) {
            if (requirement.available() < requirement.required()) {
                throw DomainException.conflict("INSUFFICIENT_STOCK",
                        "Недостаточно ресурса " + requirement.resource()
                                + ": нужно " + requirement.required()
                                + ", доступно " + requirement.available());
            }
        }

        for (Requirement requirement : requirements) {
            jdbc.update("""
                    update warehouse_stock
                       set quantity = quantity - ?, version = version + 1
                     where resource = ?
                       and settlement_id = ?
                    """, requirement.required(), requirement.resource(), actor.settlementId());
        }
        jdbc.update("""
                update ship set stage = stage + 1, version = version + 1
                 where id = ? and settlement_id = ?
                """, shipId, actor.settlementId());
        if (ship.stage() == 3) {
            jdbc.update("""
                    update ship_build_request set status = 'READY'
                     where ship_id = ? and settlement_id = ? and status = 'IN_CONSTRUCTION'
                    """, shipId, actor.settlementId());
        }
        audit.append(actor.settlementId(), actor.role(), "SHIP_STAGE_COMPLETED", "SHIP", shipId,
                "{\"completedStage\":" + ship.stage() + ",\"resourcesWrittenOff\":" + requirements.size() + "}");
    }

    @Transactional
    public void assignReadyShip(AuthenticatedUser actor, Long expeditionId, Long shipId) {
        requirePreparation(actor.settlementId(), expeditionId);
        Integer ready = jdbc.query("""
                select 1 from ship
                 where id = ? and settlement_id = ? and stage = 4
                   for update
                """, rs -> rs.next() ? 1 : null, shipId, actor.settlementId());
        if (ready == null) {
            throw DomainException.conflict("SHIP_NOT_READY", "Выбранный корабль ещё не готов");
        }
        Long occupiedBy = jdbc.query("""
                select e.id
                  from expedition_ship es
                  join expedition e on e.id = es.expedition_id
                 where es.ship_id = ? and e.status in ('PREPARATION', 'SAILING')
                 limit 1
                """, rs -> rs.next() ? rs.getLong("id") : null, shipId);
        if (occupiedBy != null) {
            throw DomainException.conflict("SHIP_ALREADY_ASSIGNED", "Корабль уже назначен в активный поход");
        }
        jdbc.update("""
                insert into expedition_ship(expedition_id, ship_id) values (?, ?)
                """, expeditionId, shipId);
        audit.append(actor.settlementId(), actor.role(), "SHIP_ASSIGNED", "EXPEDITION", expeditionId,
                "{\"shipId\":" + shipId + "}");
    }

    @Transactional
    public void removeShip(AuthenticatedUser actor, Long expeditionId, Long shipId) {
        requirePreparation(actor.settlementId(), expeditionId);
        Integer assigned = jdbc.query("""
                select 1
                  from expedition_ship es
                  join ship s on s.id = es.ship_id
                 where es.expedition_id = ? and es.ship_id = ? and s.settlement_id = ?
                   for update of es, s
                """, rs -> rs.next() ? 1 : null, expeditionId, shipId, actor.settlementId());
        if (assigned == null) {
            throw DomainException.notFound("Корабль во флоте похода");
        }

        jdbc.update("delete from expedition_ship where expedition_id = ? and ship_id = ?",
                expeditionId, shipId);
        jdbc.update("""
                update ship_build_request
                   set expedition_id = null
                 where settlement_id = ? and expedition_id = ? and ship_id = ?
                """, actor.settlementId(), expeditionId, shipId);
        audit.append(actor.settlementId(), actor.role(), "SHIP_REMOVED", "EXPEDITION", expeditionId,
                "{\"shipId\":" + shipId + "}");
    }

    @Transactional
    public Long requestShip(
            AuthenticatedUser actor,
            Long expeditionId,
            ApiModels.RequestShipRequest request
    ) {
        requirePreparation(actor.settlementId(), expeditionId);
        Integer shortage = jdbc.queryForObject("""
                select e.required_capacity - coalesce(sum(st.capacity), 0)::integer
                  from expedition e
                  left join expedition_ship es on es.expedition_id = e.id
                  left join ship s on s.id = es.ship_id and s.settlement_id = e.settlement_id
                  left join ship_type st on st.code = s.ship_type_code
                 where e.id = ? and e.settlement_id = ?
                 group by e.required_capacity
                """, Integer.class, expeditionId, actor.settlementId());
        if (shortage == null || shortage <= 0) {
            throw DomainException.conflict(
                    "FLEET_CAPACITY_SUFFICIENT",
                    "Плановая вместимость флота уже набрана");
        }
        String typeCode = request.shipTypeCode().trim().toUpperCase(java.util.Locale.ROOT);
        ShipType type = jdbc.query("""
                select code, capacity from ship_type where code = ?
                """, rs -> rs.next() ? new ShipType(rs.getString("code"), rs.getInt("capacity")) : null, typeCode);
        if (type == null) {
            throw DomainException.notFound("Тип корабля");
        }
        String name = request.shipName().trim();
        Integer duplicateName = jdbc.query("""
                select 1 from ship where settlement_id = ? and lower(name) = lower(?)
                """, rs -> rs.next() ? 1 : null, actor.settlementId(), name);
        if (duplicateName != null) {
            throw DomainException.conflict("SHIP_NAME_ALREADY_EXISTS", "Корабль с таким именем уже существует");
        }

        Long shipId = jdbc.queryForObject("""
                insert into ship(settlement_id, name, ship_type_code, stage, blessed)
                values (?, ?, ?, 0, false)
                returning id
                """, Long.class, actor.settlementId(), name, type.code());
        jdbc.update("""
                insert into ship_stage_requirement(ship_id, stage, resource, quantity)
                select ?, stage, resource, quantity
                  from ship_type_requirement where ship_type_code = ?
                """, shipId, type.code());
        jdbc.update("""
                insert into expedition_ship(expedition_id, ship_id) values (?, ?)
                """, expeditionId, shipId);
        Long requestId = jdbc.queryForObject("""
                insert into ship_build_request(
                    settlement_id, expedition_id, ship_type_code, ship_id, requested_by, status
                ) values (?, ?, ?, ?, ?, 'IN_CONSTRUCTION')
                returning id
                """, Long.class, actor.settlementId(), expeditionId, type.code(), shipId, actor.id());
        audit.append(actor.settlementId(), actor.role(), "SHIP_BUILD_REQUESTED", "EXPEDITION", expeditionId,
                "{\"requestId\":" + requestId + ",\"shipName\":\""
                        + escapeJson(name) + "\",\"capacity\":" + type.capacity() + "}");
        return requestId;
    }

    @Transactional
    public void bless(AuthenticatedUser actor, Long shipId) {
        int changed = jdbc.update("""
                update ship set blessed = true, version = version + 1
                 where id = ? and settlement_id = ? and blessed = false and stage = 3
                """, shipId, actor.settlementId());
        if (changed == 0) {
            throw DomainException.conflict("BLESSING_NOT_APPLICABLE",
                    "Благословение доступно перед финальным этапом");
        }
        audit.append(actor.settlementId(), actor.role(), "SHIP_BLESSED", "SHIP", shipId, "{}");
    }

    private void requirePreparation(Long settlementId, Long expeditionId) {
        String status = jdbc.query("""
                select status from expedition
                 where id = ? and settlement_id = ?
                   for update
                """, rs -> rs.next() ? rs.getString("status") : null, expeditionId, settlementId);
        if (status == null) {
            throw DomainException.notFound("Поход");
        }
        if (!"PREPARATION".equals(status)) {
            throw DomainException.conflict(
                    "EXPEDITION_NOT_IN_PREPARATION",
                    "Флот можно менять только на этапе подготовки");
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
