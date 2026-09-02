package com.drakkar.erp.application;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.DomainException;
import com.drakkar.erp.domain.Loot;
import com.drakkar.erp.domain.WergildCalculator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ExpeditionService {
    private record ExpeditionRow(String status, int version, boolean immutable) {
    }

    private record CrewRow(UUID id, String name) {
    }

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final WergildCalculator calculator = new WergildCalculator();

    public ExpeditionService(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void start(
            AuthenticatedUser actor,
            UUID expeditionId,
            ApiModels.StartExpeditionRequest request
    ) {
        ExpeditionRow expedition = findExpedition(actor.settlementId(), expeditionId, true);
        if (!"PREPARATION".equals(expedition.status())) {
            throw DomainException.conflict(
                    "EXPEDITION_NOT_IN_PREPARATION",
                    "Начать можно только поход на этапе подготовки");
        }
        if (expedition.version() != request.expectedVersion()) {
            throw DomainException.conflict("STALE_EXPEDITION", "Данные похода устарели");
        }

        Integer requiredCapacity = jdbc.queryForObject("""
                select required_capacity from expedition
                 where id = ? and settlement_id = ?
                """, Integer.class, expeditionId, actor.settlementId());
        Integer readyCapacity = jdbc.queryForObject("""
                select coalesce(sum(st.capacity), 0)::integer
                  from expedition_ship es
                  join ship s on s.id = es.ship_id and s.settlement_id = ?
                  join ship_type st on st.code = s.ship_type_code
                 where es.expedition_id = ? and s.stage = 4
                """, Integer.class, actor.settlementId(), expeditionId);
        Integer unfinishedShips = jdbc.queryForObject("""
                select count(*)::integer
                  from expedition_ship es
                  join ship s on s.id = es.ship_id and s.settlement_id = ?
                 where es.expedition_id = ? and s.stage < 4
                """, Integer.class, actor.settlementId(), expeditionId);
        if (unfinishedShips != null && unfinishedShips > 0) {
            throw DomainException.conflict("FLEET_NOT_READY", "Во флоте есть недостроенные корабли");
        }
        if (readyCapacity == null || requiredCapacity == null || readyCapacity < requiredCapacity) {
            throw DomainException.conflict(
                    "FLEET_CAPACITY_INSUFFICIENT",
                    "Вместимости готового флота недостаточно для выхода");
        }

        Integer confirmedCrew = jdbc.queryForObject("""
                select count(*)::integer from crew_assignment
                 where expedition_id = ? and participation_status = 'CONFIRMED'
                """, Integer.class, expeditionId);
        Integer pendingCrew = jdbc.queryForObject("""
                select count(*)::integer from crew_assignment
                 where expedition_id = ? and participation_status = 'PENDING'
                """, Integer.class, expeditionId);
        if (confirmedCrew == null || confirmedCrew == 0) {
            throw DomainException.conflict("CREW_NOT_CONFIRMED", "Нужен хотя бы один подтверждённый участник");
        }
        if (pendingCrew != null && pendingCrew > 0) {
            throw DomainException.conflict("CREW_DECISIONS_PENDING", "Не все участники ответили на назначение");
        }

        int changed = jdbc.update("""
                update expedition set status = 'SAILING', version = version + 1
                 where id = ? and settlement_id = ? and status = 'PREPARATION' and version = ?
                """, expeditionId, actor.settlementId(), request.expectedVersion());
        if (changed != 1) {
            throw DomainException.conflict("STALE_EXPEDITION", "Данные похода устарели");
        }
        audit.append(actor.settlementId(), actor.role(), "EXPEDITION_STARTED", "EXPEDITION", expeditionId,
                "{\"readyCapacity\":" + readyCapacity + ",\"confirmedCrew\":" + confirmedCrew + "}");
    }

    public List<ApiModels.AllocationView> preview(
            AuthenticatedUser actor,
            UUID expeditionId,
            ApiModels.FinalizeRequest request
    ) {
        ExpeditionRow expedition = findExpedition(actor.settlementId(), expeditionId, false);
        validateCanFinalize(expedition, request.expectedVersion());
        return toViews(calculate(expeditionId, request));
    }

    @Transactional
    public List<ApiModels.AllocationView> finalizeExpedition(
            AuthenticatedUser actor,
            UUID expeditionId,
            ApiModels.FinalizeRequest request
    ) {
        ExpeditionRow expedition = findExpedition(actor.settlementId(), expeditionId, true);
        validateCanFinalize(expedition, request.expectedVersion());
        List<WergildCalculator.Allocation> allocations = calculate(expeditionId, request);

        Set<UUID> fallen = new HashSet<>(request.fallenAssignmentIds());
        for (UUID assignmentId : fallen) {
            int marked = jdbc.update("""
                    update crew_assignment
                       set alive = false, version = version + 1
                     where expedition_id = ? and id = ?
                    """, expeditionId, assignmentId);
            if (marked != 1) {
                throw DomainException.conflict("INVALID_FALLEN_LIST",
                        "Список потерь содержит участника из другого похода");
            }
        }

        for (WergildCalculator.Allocation allocation : allocations) {
            jdbc.update("""
                    insert into wergild_allocation(expedition_id, recipient, category, gold, provisions, thralls)
                    values (?, ?, ?, ?, ?, ?)
                    """, expeditionId, allocation.recipient(), allocation.category(),
                    allocation.loot().gold(), allocation.loot().provisions(), allocation.loot().thralls());
        }

        addToWarehouse(actor.settlementId(), "GOLD", request.loot().gold());
        addToWarehouse(actor.settlementId(), "PROVISIONS", request.loot().provisions());
        addToWarehouse(actor.settlementId(), "THRALLS", request.loot().thralls());

        int changed = jdbc.update("""
                update expedition
                   set status = 'COMPLETED', finalized_at = now(),
                       loot_gold = ?, loot_provisions = ?, loot_thralls = ?, version = version + 1
                 where id = ? and settlement_id = ? and version = ? and finalized_at is null
                """, request.loot().gold(), request.loot().provisions(), request.loot().thralls(),
                expeditionId, actor.settlementId(), request.expectedVersion());
        if (changed != 1) {
            throw DomainException.conflict("STALE_EXPEDITION", "Итоги похода уже были изменены");
        }

        audit.append(actor.settlementId(), actor.role(), "EXPEDITION_FINALIZED", "EXPEDITION", expeditionId,
                "{\"allocations\":" + allocations.size() + ",\"fallen\":" + fallen.size() + "}");
        return toViews(allocations);
    }

    private ExpeditionRow findExpedition(UUID settlementId, UUID expeditionId, boolean lock) {
        String suffix = lock ? " for update" : "";
        ExpeditionRow row = jdbc.query("""
                select status, version, finalized_at is not null as immutable
                  from expedition where id = ? and settlement_id = ?
                """ + suffix, rs -> rs.next()
                ? new ExpeditionRow(rs.getString("status"), rs.getInt("version"), rs.getBoolean("immutable"))
                : null, expeditionId, settlementId);
        if (row == null) {
            throw DomainException.notFound("Поход");
        }
        return row;
    }

    private void validateCanFinalize(ExpeditionRow expedition, int expectedVersion) {
        if (expedition.immutable()) {
            throw DomainException.conflict("RESULTS_IMMUTABLE", "Итоги уже утверждены и доступны только для чтения");
        }
        if (!expedition.status().equals("SAILING")) {
            throw DomainException.conflict("EXPEDITION_NOT_SAILING", "Завершить можно только поход со статусом «В плавании»");
        }
        if (expedition.version() != expectedVersion) {
            throw DomainException.conflict("STALE_EXPEDITION", "Данные похода устарели");
        }
    }

    private List<WergildCalculator.Allocation> calculate(UUID expeditionId, ApiModels.FinalizeRequest request) {
        List<CrewRow> crew = jdbc.query("""
                select ca.id, u.display_name
                  from crew_assignment ca
                  join app_user u on u.id = ca.user_id
                 where ca.expedition_id = ? and ca.participation_status = 'CONFIRMED'
                 order by u.display_name
                """, (rs, rowNum) -> new CrewRow(
                rs.getObject("id", UUID.class), rs.getString("display_name")), expeditionId);
        Set<UUID> ids = crew.stream().map(CrewRow::id).collect(java.util.stream.Collectors.toSet());
        if (!ids.containsAll(request.fallenAssignmentIds())) {
            throw DomainException.conflict("INVALID_FALLEN_LIST",
                    "Потери можно отмечать только среди подтверждённых участников");
        }
        Set<UUID> fallen = new HashSet<>(request.fallenAssignmentIds());
        List<WergildCalculator.Claimant> claimants = crew.stream()
                .map(member -> new WergildCalculator.Claimant(member.name(), !fallen.contains(member.id())))
                .toList();
        Loot loot = new Loot(request.loot().gold(), request.loot().provisions(), request.loot().thralls());
        return calculator.calculate(loot, claimants);
    }

    private List<ApiModels.AllocationView> toViews(List<WergildCalculator.Allocation> allocations) {
        return allocations.stream().map(allocation -> new ApiModels.AllocationView(
                allocation.recipient(), allocation.category(),
                new ApiModels.LootRequest(allocation.loot().gold(), allocation.loot().provisions(), allocation.loot().thralls())
        )).toList();
    }

    private void addToWarehouse(UUID settlementId, String resource, int amount) {
        jdbc.update("""
                update warehouse_stock
                   set quantity = quantity + ?, version = version + 1
                 where settlement_id = ? and resource = ?
                """, amount, settlementId, resource);
    }
}
