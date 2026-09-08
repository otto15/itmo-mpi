package com.drakkar.erp.service;

import com.drakkar.erp.dao.AuditDao;
import com.drakkar.erp.dao.ExpeditionDao;
import com.drakkar.erp.dao.PreparationDao;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.DomainException;
import com.drakkar.erp.dto.ApiModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;

@Service
public class ReservationService {
    private final PreparationDao dao;
    private final ExpeditionDao expeditions;
    private final AuditDao audit;
    private final Duration ttl;

    public ReservationService(PreparationDao dao, ExpeditionDao expeditions, AuditDao audit,
                              @Value("${drakkar.reservations.ttl}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Reservation TTL must be positive");
        this.dao = dao;
        this.expeditions = expeditions;
        this.audit = audit;
        this.ttl = ttl;
    }

    @Transactional
    public void savePlan(AuthenticatedUser actor, Long expeditionId, PreparationRequest request) {
        lockPreparation(actor, expeditionId, request.expectedVersion());
        if (request.route().get(0).distanceKm() != 0 || request.route().stream().skip(1).anyMatch(p -> p.distanceKm() <= 0))
            throw DomainException.conflict("INVALID_ROUTE", "У первой точки расстояние 0, у следующих — расстояние от предыдущей точки");
        var allowed = Set.of("PROVISIONS", "WOOD", "CLOTH", "RESIN", "GOLD", "THRALLS");
        if (request.resources().stream().anyMatch(r -> !allowed.contains(r.resource())) ||
                request.resources().stream().map(SupplyRequest::resource).distinct().count() != request.resources().size())
            throw DomainException.conflict("INVALID_SUPPLIES", "Укажите каждый ресурс один раз");
        dao.releaseExpedition(expeditionId, dao.databaseTime());
        dao.savePlan(expeditionId, request);
        dao.bumpVersion(expeditionId);
        audit.append(actor, "PREPARATION_UPDATED", "EXPEDITION", expeditionId, "{}");
    }

    @Transactional
    public Long reserve(AuthenticatedUser actor, Long expeditionId, ReserveRequest request) {
        lockPreparation(actor, expeditionId, request.expectedVersion());
        dao.lockStock(actor.settlementId());
        var at = dao.databaseTime();
        var items = dao.requirements(expeditionId);
        if (items.isEmpty()) throw DomainException.conflict("SUPPLIES_REQUIRED", "Сначала укажите припасы");
        dao.releaseExpedition(expeditionId, at);
        for (var item : items) {
            if (dao.available(actor.settlementId(), item.resource(), at) < item.quantity())
                throw DomainException.conflict("INSUFFICIENT_STOCK", "Недостаточно свободного ресурса " + item.resource());
        }
        Long id = dao.reserve(actor.settlementId(), expeditionId, actor.id(), at, at.plus(ttl));
        items.forEach(item -> dao.addItem(id, item));
        dao.bumpVersion(expeditionId);
        audit.append(actor, "RESOURCES_RESERVED", "EXPEDITION", expeditionId, "{\"reservationId\":" + id + "}");
        return id;
    }

    @Transactional
    public void expire(PreparationDao.ExpiredReservation candidate) {
        dao.lockSettlement(candidate.settlementId());
        dao.lockStock(candidate.settlementId());
        var at = dao.databaseTime();
        if (dao.expire(candidate.id(), at)) {
            if (candidate.expeditionId() != null) dao.bumpVersion(candidate.expeditionId());
            audit.appendSystem(candidate.settlementId(), "RESERVATION_EXPIRED",
                    candidate.expeditionId() == null ? "SHIP" : "EXPEDITION",
                    candidate.expeditionId() == null ? candidate.shipId() : candidate.expeditionId(),
                    "{\"reservationId\":" + candidate.id() + "}");
        }
    }

    private void lockPreparation(AuthenticatedUser actor, Long id, int version) {
        dao.lockSettlement(actor.settlementId());
        var state = expeditions.findState(actor.settlementId(), id, true);
        if (state == null) throw DomainException.notFound("Поход");
        if (!state.isInPreparation()) throw DomainException.conflict("EXPEDITION_NOT_IN_PREPARATION", "Поход уже не находится в подготовке");
        if (state.version() != version) throw DomainException.conflict("STALE_EXPEDITION", "Данные похода устарели");
    }
}
