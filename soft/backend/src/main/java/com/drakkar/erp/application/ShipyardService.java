package com.drakkar.erp.application;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.DomainException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ShipyardService {
    private record ShipRow(int stage, boolean blessed, int version) {
    }

    private record Requirement(String resource, int required, int available) {
    }

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public ShipyardService(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void completeStage(AuthenticatedUser actor, UUID shipId, ApiModels.CompleteStageRequest request) {
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
        audit.append(actor.settlementId(), actor.role(), "SHIP_STAGE_COMPLETED", "SHIP", shipId,
                "{\"completedStage\":" + ship.stage() + ",\"resourcesWrittenOff\":" + requirements.size() + "}");
    }

    @Transactional
    public void bless(AuthenticatedUser actor, UUID shipId) {
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
}
