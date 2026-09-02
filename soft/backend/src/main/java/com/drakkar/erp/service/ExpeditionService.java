package com.drakkar.erp.service;

import com.drakkar.erp.dto.ApiModels;
import com.drakkar.erp.dao.AuditDao;
import com.drakkar.erp.dao.ExpeditionDao;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.CrewCounts;
import com.drakkar.erp.domain.CrewMember;
import com.drakkar.erp.domain.DomainException;
import com.drakkar.erp.domain.ExpeditionState;
import com.drakkar.erp.domain.Loot;
import com.drakkar.erp.domain.WergildCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExpeditionService {
    private final ExpeditionDao dao;
    private final AuditDao audit;
    private final WergildCalculator calculator = new WergildCalculator();

    public ExpeditionService(ExpeditionDao dao, AuditDao audit) {
        this.dao = dao;
        this.audit = audit;
    }

    @Transactional
    public void start(
            AuthenticatedUser actor,
            Long expeditionId,
            ApiModels.StartExpeditionRequest request
    ) {
        ExpeditionState expedition = findExpedition(actor.settlementId(), expeditionId, true);
        if (!expedition.isInPreparation()) {
            throw DomainException.conflict(
                    "EXPEDITION_NOT_IN_PREPARATION",
                    "Начать можно только поход на этапе подготовки");
        }
        if (expedition.version() != request.expectedVersion()) {
            throw DomainException.conflict("STALE_EXPEDITION", "Данные похода устарели");
        }

        int readyCapacity = dao.readyCapacity(actor.settlementId(), expeditionId);
        if (dao.unfinishedShipCount(actor.settlementId(), expeditionId) > 0) {
            throw DomainException.conflict("FLEET_NOT_READY", "Во флоте есть недостроенные корабли");
        }

        CrewCounts crew = dao.crewCounts(expeditionId);
        if (readyCapacity < crew.invited()) {
            throw DomainException.conflict(
                    "FLEET_CAPACITY_INSUFFICIENT",
                    "Вместимости готового флота недостаточно для приглашённой команды");
        }
        if (!crew.hasConfirmedMembers()) {
            throw DomainException.conflict(
                    "CREW_NOT_CONFIRMED",
                    "Нужен хотя бы один подтверждённый участник");
        }
        if (crew.hasPendingDecisions()) {
            throw DomainException.conflict(
                    "CREW_DECISIONS_PENDING",
                    "Не все участники ответили на назначение");
        }

        if (!dao.markSailing(actor.settlementId(), expeditionId, request.expectedVersion())) {
            throw DomainException.conflict("STALE_EXPEDITION", "Данные похода устарели");
        }
        audit.append(
                actor.settlementId(), actor.role(), "EXPEDITION_STARTED", "EXPEDITION", expeditionId,
                "{\"readyCapacity\":" + readyCapacity + ",\"crewSize\":" + crew.invited() + "}");
    }

    public List<ApiModels.AllocationView> preview(
            AuthenticatedUser actor,
            Long expeditionId,
            ApiModels.FinalizeRequest request
    ) {
        ExpeditionState expedition = findExpedition(actor.settlementId(), expeditionId, false);
        validateCanFinalize(expedition, request.expectedVersion());
        return toViews(calculate(expeditionId, request));
    }

    @Transactional
    public List<ApiModels.AllocationView> finalizeExpedition(
            AuthenticatedUser actor,
            Long expeditionId,
            ApiModels.FinalizeRequest request
    ) {
        ExpeditionState expedition = findExpedition(actor.settlementId(), expeditionId, true);
        validateCanFinalize(expedition, request.expectedVersion());
        List<WergildCalculator.Allocation> allocations = calculate(expeditionId, request);

        Set<Long> fallen = new HashSet<>(request.fallenAssignmentIds());
        for (Long assignmentId : fallen) {
            if (!dao.markFallen(expeditionId, assignmentId)) {
                throw DomainException.conflict(
                        "INVALID_FALLEN_LIST",
                        "Список потерь содержит участника из другого похода");
            }
        }

        for (WergildCalculator.Allocation allocation : allocations) {
            dao.addAllocation(
                    expeditionId,
                    allocation.recipient(),
                    allocation.category(),
                    allocation.loot().gold(),
                    allocation.loot().provisions(),
                    allocation.loot().thralls());
        }

        dao.addToWarehouse(actor.settlementId(), "GOLD", request.loot().gold());
        dao.addToWarehouse(actor.settlementId(), "PROVISIONS", request.loot().provisions());
        dao.addToWarehouse(actor.settlementId(), "THRALLS", request.loot().thralls());

        if (!dao.complete(
                actor.settlementId(),
                expeditionId,
                request.expectedVersion(),
                request.loot().gold(),
                request.loot().provisions(),
                request.loot().thralls())) {
            throw DomainException.conflict("STALE_EXPEDITION", "Итоги похода уже были изменены");
        }

        audit.append(
                actor.settlementId(), actor.role(), "EXPEDITION_FINALIZED", "EXPEDITION", expeditionId,
                "{\"allocations\":" + allocations.size() + ",\"fallen\":" + fallen.size() + "}");
        return toViews(allocations);
    }

    private ExpeditionState findExpedition(Long settlementId, Long expeditionId, boolean lock) {
        ExpeditionState expedition = dao.findState(settlementId, expeditionId, lock);
        if (expedition == null) {
            throw DomainException.notFound("Поход");
        }
        return expedition;
    }

    private void validateCanFinalize(ExpeditionState expedition, int expectedVersion) {
        if (expedition.immutable()) {
            throw DomainException.conflict(
                    "RESULTS_IMMUTABLE",
                    "Итоги уже утверждены и доступны только для чтения");
        }
        if (!expedition.isSailing()) {
            throw DomainException.conflict(
                    "EXPEDITION_NOT_SAILING",
                    "Завершить можно только поход со статусом «В плавании»");
        }
        if (expedition.version() != expectedVersion) {
            throw DomainException.conflict("STALE_EXPEDITION", "Данные похода устарели");
        }
    }

    private List<WergildCalculator.Allocation> calculate(
            Long expeditionId,
            ApiModels.FinalizeRequest request
    ) {
        List<CrewMember> crew = dao.confirmedCrew(expeditionId);
        Set<Long> crewIds = crew.stream()
                .map(CrewMember::assignmentId)
                .collect(Collectors.toSet());
        if (!crewIds.containsAll(request.fallenAssignmentIds())) {
            throw DomainException.conflict(
                    "INVALID_FALLEN_LIST",
                    "Потери можно отмечать только среди подтверждённых участников");
        }

        Set<Long> fallen = new HashSet<>(request.fallenAssignmentIds());
        List<WergildCalculator.Claimant> claimants = crew.stream()
                .map(member -> new WergildCalculator.Claimant(
                        member.name(), !fallen.contains(member.assignmentId())))
                .toList();
        Loot loot = new Loot(
                request.loot().gold(),
                request.loot().provisions(),
                request.loot().thralls());
        return calculator.calculate(loot, claimants);
    }

    private List<ApiModels.AllocationView> toViews(
            List<WergildCalculator.Allocation> allocations
    ) {
        return allocations.stream()
                .map(allocation -> new ApiModels.AllocationView(
                        allocation.recipient(),
                        allocation.category(),
                        new ApiModels.LootRequest(
                                allocation.loot().gold(),
                                allocation.loot().provisions(),
                                allocation.loot().thralls())))
                .toList();
    }
}
