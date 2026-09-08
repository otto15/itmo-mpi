package com.drakkar.erp.service;

import com.drakkar.erp.dao.ExpeditionDao;
import com.drakkar.erp.dao.PreparationDao;
import com.drakkar.erp.domain.CrewCounts;
import com.drakkar.erp.dto.ApiModels.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;

@Service
public class ReadinessService {
    private final ExpeditionDao expeditions;
    private final PreparationDao preparation;

    public ReadinessService(ExpeditionDao expeditions, PreparationDao preparation) {
        this.expeditions = expeditions;
        this.preparation = preparation;
    }

    public ReadinessView check(Long settlementId, Long expeditionId, Instant at) {
        var blockers = new ArrayList<Blocker>();
        var state = expeditions.findState(settlementId, expeditionId, false);
        if (state == null || !state.isInPreparation())
            blockers.add(new Blocker("EXPEDITION_NOT_IN_PREPARATION", "Поход уже не находится в подготовке"));
        if (expeditions.unfinishedShipCount(settlementId, expeditionId) > 0)
            blockers.add(new Blocker("FLEET_NOT_READY", "Во флоте есть недостроенные корабли"));
        CrewCounts crew = expeditions.crewCounts(expeditionId);
        int capacity = expeditions.readyCapacity(settlementId, expeditionId);
        if (capacity == 0 || capacity < crew.invited())
            blockers.add(new Blocker("FLEET_CAPACITY_INSUFFICIENT", "Недостаточно мест в готовом флоте"));
        if (!crew.hasConfirmedMembers())
            blockers.add(new Blocker("CREW_NOT_CONFIRMED", "Нужен хотя бы один подтверждённый участник"));
        if (crew.hasPendingDecisions())
            blockers.add(new Blocker("CREW_DECISIONS_PENDING", "Не все участники ответили на назначение"));
        var route = preparation.route(expeditionId);
        if (route.size() < 2 || route.get(0).distanceKm() != 0 || route.stream().skip(1).anyMatch(p -> p.distanceKm() <= 0))
            blockers.add(new Blocker("ROUTE_REQUIRED", "Укажите маршрут и расстояния между точками"));
        var requirements = preparation.requirements(expeditionId);
        if (requirements.isEmpty())
            blockers.add(new Blocker("SUPPLIES_REQUIRED", "Укажите необходимые припасы"));
        for (var item : requirements) {
            if (preparation.expeditionReserved(expeditionId, item.resource(), at) < item.quantity()) {
                blockers.add(new Blocker("RESERVATION_REQUIRED", "Нет действующего резерва: " + item.resource()));
            } else if (preparation.available(settlementId, item.resource(), at) < 0) {
                blockers.add(new Blocker("INSUFFICIENT_STOCK", "На складе недостаточно ресурса " + item.resource()));
            }
        }
        return new ReadinessView(blockers.isEmpty(), blockers);
    }
}
