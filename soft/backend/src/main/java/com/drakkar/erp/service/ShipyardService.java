package com.drakkar.erp.service;

import com.drakkar.erp.dto.ApiModels;
import com.drakkar.erp.dao.AuditDao;
import com.drakkar.erp.dao.ShipyardDao;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.DomainException;
import com.drakkar.erp.domain.ShipRequirement;
import com.drakkar.erp.domain.ShipState;
import com.drakkar.erp.domain.ShipTypeDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ShipyardService {
    private final ShipyardDao dao;
    private final AuditDao audit;

    public ShipyardService(ShipyardDao dao, AuditDao audit) {
        this.dao = dao;
        this.audit = audit;
    }

    @Transactional
    public void completeStage(
            AuthenticatedUser actor,
            Long shipId,
            ApiModels.CompleteStageRequest request
    ) {
        ShipState ship = dao.findShipForUpdate(actor.settlementId(), shipId);
        if (ship == null) {
            throw DomainException.notFound("Корабль");
        }
        if (ship.version() != request.expectedVersion()) {
            throw DomainException.conflict(
                    "STALE_SHIP_STAGE",
                    "Этап корабля уже был изменён");
        }
        if (ship.isCompleted()) {
            throw DomainException.conflict(
                    "SHIP_ALREADY_COMPLETED",
                    "Корабль уже готов к спуску");
        }
        if (ship.needsBlessing()) {
            throw DomainException.conflict(
                    "BLESSING_REQUIRED",
                    "Ожидается благословение Жреца");
        }

        List<ShipRequirement> requirements = dao.lockStageRequirements(
                actor.settlementId(), shipId, ship.stage());
        for (ShipRequirement requirement : requirements) {
            if (!requirement.isSatisfied()) {
                throw DomainException.conflict(
                        "INSUFFICIENT_STOCK",
                        "Недостаточно ресурса " + requirement.resource()
                                + ": нужно " + requirement.required()
                                + ", доступно " + requirement.available());
            }
        }

        requirements.forEach(requirement -> dao.deductStock(actor.settlementId(), requirement));
        dao.advanceShip(actor.settlementId(), shipId);
        if (ship.stage() == 3) {
            dao.markBuildRequestReady(actor.settlementId(), shipId);
        }
        audit.append(
                actor.settlementId(), actor.role(), "SHIP_STAGE_COMPLETED", "SHIP", shipId,
                "{\"completedStage\":" + ship.stage()
                        + ",\"resourcesWrittenOff\":" + requirements.size() + "}");
    }

    @Transactional
    public void assignReadyShip(AuthenticatedUser actor, Long expeditionId, Long shipId) {
        requirePreparation(actor.settlementId(), expeditionId);
        if (!dao.lockReadyShip(actor.settlementId(), shipId)) {
            throw DomainException.conflict("SHIP_NOT_READY", "Выбранный корабль ещё не готов");
        }
        if (dao.findActiveExpeditionForShip(shipId) != null) {
            throw DomainException.conflict(
                    "SHIP_ALREADY_ASSIGNED",
                    "Корабль уже назначен в активный поход");
        }
        dao.addShipToExpedition(expeditionId, shipId);
        audit.append(
                actor.settlementId(), actor.role(), "SHIP_ASSIGNED", "EXPEDITION", expeditionId,
                "{\"shipId\":" + shipId + "}");
    }

    @Transactional
    public void removeShip(AuthenticatedUser actor, Long expeditionId, Long shipId) {
        requirePreparation(actor.settlementId(), expeditionId);
        if (!dao.lockAssignedShip(actor.settlementId(), expeditionId, shipId)) {
            throw DomainException.notFound("Корабль во флоте похода");
        }

        dao.removeShipFromExpedition(expeditionId, shipId);
        dao.detachBuildRequest(actor.settlementId(), expeditionId, shipId);
        audit.append(
                actor.settlementId(), actor.role(), "SHIP_REMOVED", "EXPEDITION", expeditionId,
                "{\"shipId\":" + shipId + "}");
    }

    @Transactional
    public Long requestShip(
            AuthenticatedUser actor,
            Long expeditionId,
            ApiModels.RequestShipRequest request
    ) {
        requirePreparation(actor.settlementId(), expeditionId);
        if (dao.fleetSeatShortage(actor.settlementId(), expeditionId) <= 0) {
            throw DomainException.conflict(
                    "FLEET_CAPACITY_SUFFICIENT",
                    "Плановая вместимость флота уже набрана");
        }

        String typeCode = request.shipTypeCode().trim().toUpperCase(Locale.ROOT);
        ShipTypeDefinition type = dao.findShipType(typeCode);
        if (type == null) {
            throw DomainException.notFound("Тип корабля");
        }
        String name = request.shipName().trim();
        if (dao.shipNameExists(actor.settlementId(), name)) {
            throw DomainException.conflict(
                    "SHIP_NAME_ALREADY_EXISTS",
                    "Корабль с таким именем уже существует");
        }

        Long shipId = dao.createShip(actor.settlementId(), name, type.code());
        dao.snapshotTypeRequirements(shipId, type.code());
        dao.addShipToExpedition(expeditionId, shipId);
        Long requestId = dao.createBuildRequest(
                actor.settlementId(), expeditionId, type.code(), shipId, actor.id());
        audit.append(
                actor.settlementId(), actor.role(), "SHIP_BUILD_REQUESTED", "EXPEDITION", expeditionId,
                "{\"requestId\":" + requestId + ",\"shipName\":\""
                        + escapeJson(name) + "\",\"capacity\":" + type.capacity() + "}");
        return requestId;
    }

    @Transactional
    public void bless(AuthenticatedUser actor, Long shipId) {
        if (!dao.blessShip(actor.settlementId(), shipId)) {
            throw DomainException.conflict(
                    "BLESSING_NOT_APPLICABLE",
                    "Благословение доступно перед финальным этапом");
        }
        audit.append(
                actor.settlementId(), actor.role(), "SHIP_BLESSED", "SHIP", shipId, "{}");
    }

    private void requirePreparation(Long settlementId, Long expeditionId) {
        String status = dao.lockExpeditionStatus(settlementId, expeditionId);
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
