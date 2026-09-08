package com.drakkar.erp.service;

import com.drakkar.erp.dao.PreparationDao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.drakkar.erp.dto.ApiModels;
import com.drakkar.erp.dao.DemoQueryDao;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.Role;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DemoQueryService {
    private static final List<String> STAGE_NAMES = List.of(
            "Заготовка леса",
            "Сборка каркаса",
            "Обшивка корпуса",
            "Оснастка и благословение",
            "Готов к спуску"
    );

    private final DemoQueryDao dao;
    private final PreparationDao preparation;
    private final ReadinessService readiness;
    private final ObjectMapper json;

    public DemoQueryService(DemoQueryDao dao, PreparationDao preparation,
                            ReadinessService readiness, ObjectMapper json) {
        this.dao = dao;
        this.preparation = preparation;
        this.readiness = readiness;
        this.json = json;
    }

    @Transactional(readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public ApiModels.DemoState state(AuthenticatedUser actor) {
        Long settlementId = actor.settlementId();
        var at = preparation.databaseTime();
        List<ApiModels.ExpeditionView> expeditions = dao.expeditions(settlementId).stream()
                .map(row -> toExpeditionView(actor, row, at))
                .toList();
        List<ApiModels.CrewView> crew = dao.crew(settlementId).stream()
                .map(this::toCrewView)
                .toList();
        List<ApiModels.UserView> availableUsers = dao.availableWarriors(settlementId).stream()
                .map(row -> new ApiModels.UserView(row.id(), row.displayName(), row.role()))
                .toList();
        List<ApiModels.ShipView> ships = dao.ships(settlementId).stream()
                .map(row -> toShipView(settlementId, row, at))
                .toList();
        List<ApiModels.ShipTypeView> shipTypes = dao.shipTypes().stream()
                .map(this::toShipTypeView)
                .toList();
        List<ApiModels.StockView> stock = dao.stock(settlementId).stream()
                .map(row -> {
                    int reserved = preparation.reserved(settlementId, row.resource(), at);
                    return new ApiModels.StockView(row.resource(), row.quantity(), row.version(), reserved, row.quantity() - reserved);
                })
                .toList();
        List<ApiModels.AllocationView> allocations = dao.allocations(settlementId).stream()
                .map(row -> new ApiModels.AllocationView(
                        row.expeditionId(),
                        row.recipient(),
                        row.category(),
                        new ApiModels.LootRequest(row.gold(), row.provisions(), row.thralls())))
                .toList();

        if (actor.role() == Role.JARL) {
            return new ApiModels.DemoState(
                    expeditions, crew, availableUsers, ships, shipTypes, stock, allocations,
                    actor.settlementName(), DemoResetService.DEFAULT_SETTLEMENT_ID.equals(settlementId));
        }
        if (actor.role() == Role.WARRIOR) {
            return warriorState(actor, expeditions, crew);
        }
        if (actor.role() == Role.SHIPBUILDER) {
            return shipbuilderState(actor, expeditions, ships, shipTypes, stock);
        }
        return priestState(actor, expeditions, ships);
    }

    private ApiModels.DemoState warriorState(
            AuthenticatedUser actor,
            List<ApiModels.ExpeditionView> expeditions,
            List<ApiModels.CrewView> crew
    ) {
        List<ApiModels.CrewView> ownCrew = crew.stream()
                .filter(item -> item.userId().equals(actor.id()))
                .toList();
        Set<Long> ownExpeditionIds = ownCrew.stream()
                .map(ApiModels.CrewView::expeditionId)
                .collect(Collectors.toSet());
        List<ApiModels.ExpeditionView> ownExpeditions = expeditions.stream()
                .filter(item -> ownExpeditionIds.contains(item.id()))
                .toList();
        return emptyPrivilegedState(actor, ownExpeditions, ownCrew, List.of());
    }

    private ApiModels.DemoState shipbuilderState(
            AuthenticatedUser actor,
            List<ApiModels.ExpeditionView> expeditions,
            List<ApiModels.ShipView> ships,
            List<ApiModels.ShipTypeView> shipTypes,
            List<ApiModels.StockView> stock
    ) {
        List<ApiModels.ShipView> work = ships.stream()
                .filter(item -> item.stage() < 4 || "IN_CONSTRUCTION".equals(item.requestStatus()))
                .toList();
        Set<Long> expeditionIds = work.stream()
                .map(ApiModels.ShipView::expeditionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<ApiModels.ExpeditionView> relatedExpeditions = expeditions.stream()
                .filter(item -> expeditionIds.contains(item.id()))
                .toList();
        List<ApiModels.StockView> constructionStock = stock.stream()
                .filter(item -> List.of("WOOD", "CLOTH", "RESIN").contains(item.resource()))
                .toList();
        return new ApiModels.DemoState(
                relatedExpeditions, List.of(), List.of(), work, shipTypes, constructionStock, List.of(),
                actor.settlementName(), false);
    }

    private ApiModels.DemoState priestState(
            AuthenticatedUser actor,
            List<ApiModels.ExpeditionView> expeditions,
            List<ApiModels.ShipView> ships
    ) {
        List<ApiModels.ShipView> awaitingBlessing = ships.stream()
                .filter(item -> item.stage() == 3 && !item.blessed())
                .toList();
        Set<Long> expeditionIds = awaitingBlessing.stream()
                .map(ApiModels.ShipView::expeditionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<ApiModels.ExpeditionView> relatedExpeditions = expeditions.stream()
                .filter(item -> expeditionIds.contains(item.id()))
                .toList();
        return emptyPrivilegedState(actor, relatedExpeditions, List.of(), awaitingBlessing);
    }

    private ApiModels.DemoState emptyPrivilegedState(
            AuthenticatedUser actor,
            List<ApiModels.ExpeditionView> expeditions,
            List<ApiModels.CrewView> crew,
            List<ApiModels.ShipView> ships
    ) {
        return new ApiModels.DemoState(
                expeditions, crew, List.of(), ships, List.of(), List.of(), List.of(),
                actor.settlementName(), false);
    }

    private ApiModels.ExpeditionView toExpeditionView(
            AuthenticatedUser actor,
            DemoQueryDao.ExpeditionRow row,
            Instant at
    ) {
        Long settlementId = actor.settlementId();
        List<ApiModels.FleetShipView> fleet = dao.fleet(settlementId, row.id()).stream()
                .map(ship -> new ApiModels.FleetShipView(
                        ship.id(),
                        ship.name(),
                        ship.typeName(),
                        ship.capacity(),
                        ship.stage(),
                        ship.stage() == 4,
                        ship.requestStatus()))
                .toList();
        int readyCapacity = fleet.stream()
                .filter(ApiModels.FleetShipView::ready)
                .mapToInt(ApiModels.FleetShipView::capacity)
                .sum();
        int plannedCapacity = fleet.stream()
                .mapToInt(ApiModels.FleetShipView::capacity)
                .sum();
        ApiModels.LootRequest loot = row.lootGold() == null
                ? null
                : new ApiModels.LootRequest(
                row.lootGold(), row.lootProvisions(), row.lootThralls());
        List<ApiModels.AuditView> audit = dao.expeditionAudit(settlementId, row.id()).stream()
                .map(event -> new ApiModels.AuditView(
                        event.id(),
                        event.happenedAt(),
                        event.actorRole(),
                        event.actorName(),
                        event.eventType(),
                        event.aggregateType(),
                        event.aggregateId(),
                        event.details()))
                .toList();
        return new ApiModels.ExpeditionView(
                row.id(), row.name(), row.target(), row.status(), row.plannedDeparture(),
                row.crewSize(), readyCapacity, plannedCapacity, fleet, audit,
                row.version(), row.immutable(), loot,
                actor.role() == Role.JARL ? preparationView(settlementId, row.id(), at) : null);
    }

    private ApiModels.PreparationView preparationView(Long settlementId, Long id, Instant at) {
        var departure = preparation.departure(id);
        JsonNode snapshot = null;
        if (departure.get("snapshot") != null) {
            try {
                snapshot = json.readTree(departure.get("snapshot").toString());
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Invalid departure snapshot", ex);
            }
        }
        var started = (Timestamp) departure.get("started_at");
        return new ApiModels.PreparationView(preparation.route(id), preparation.requirements(id),
                preparation.reservations(id, at), readiness.check(settlementId, id, at),
                started == null ? null : started.toInstant(), snapshot);
    }

    private ApiModels.CrewView toCrewView(DemoQueryDao.CrewRow row) {
        return new ApiModels.CrewView(
                row.id(),
                row.expeditionId(),
                row.userId(),
                row.userName(),
                row.expeditionRole(),
                row.participationStatus(),
                row.alive(),
                row.version());
    }

    private ApiModels.ShipView toShipView(Long settlementId, DemoQueryDao.ShipRow row, Instant at) {
        List<ApiModels.RequirementView> requirements = row.requirements().stream()
                .map(requirement -> new ApiModels.RequirementView(
                        requirement.resource(), requirement.quantity(),
                        preparation.available(settlementId, requirement.resource(), at)
                                + preparation.ownStageReserved(row.id(), row.stage(), requirement.resource(), at)))
                .toList();
        return new ApiModels.ShipView(
                row.id(),
                row.name(),
                row.typeCode(),
                row.typeName(),
                row.capacity(),
                row.stage(),
                STAGE_NAMES.get(row.stage()),
                row.stage() * 25,
                row.blessed(),
                row.version(),
                row.available(),
                row.expeditionId(),
                row.expeditionName(),
                row.requestStatus(),
                requirements);
    }

    private ApiModels.ShipTypeView toShipTypeView(DemoQueryDao.ShipTypeRow row) {
        List<ApiModels.RecipeResourceView> recipe = row.recipe().stream()
                .map(item -> new ApiModels.RecipeResourceView(item.resource(), item.quantity()))
                .toList();
        return new ApiModels.ShipTypeView(row.code(), row.name(), row.capacity(), recipe);
    }
}
