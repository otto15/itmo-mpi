package com.drakkar.erp.application;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
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

    private record ExpeditionRow(
            Long id,
            String name,
            String target,
            String status,
            LocalDate plannedDeparture,
            int requiredCapacity,
            int version,
            boolean immutable,
            ApiModels.LootRequest loot
    ) {
    }

    private final JdbcTemplate jdbc;

    public DemoQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ApiModels.DemoState state(AuthenticatedUser actor) {
        Long settlementId = actor.settlementId();
        List<ApiModels.ExpeditionView> expeditions = expeditionRows(settlementId).stream()
                .map(row -> toExpeditionView(settlementId, row))
                .toList();
        List<ApiModels.CrewView> crew = crew(settlementId);
        List<ApiModels.UserView> availableUsers = availableUsers(settlementId);
        List<ApiModels.ShipView> ships = ships(settlementId);
        List<ApiModels.ShipTypeView> shipTypes = shipTypes();
        List<ApiModels.StockView> stock = stock(settlementId);
        List<ApiModels.AllocationView> allocations = allocations(settlementId);

        if (actor.role() == Role.JARL) {
            return new ApiModels.DemoState(
                    expeditions, crew, availableUsers, ships, shipTypes, stock, allocations,
                    actor.settlementName(), DemoResetService.DEFAULT_SETTLEMENT_ID.equals(settlementId));
        }
        if (actor.role() == Role.WARRIOR) {
            List<ApiModels.CrewView> ownCrew = crew.stream()
                    .filter(item -> item.userId().equals(actor.id()))
                    .toList();
            Set<Long> ownExpeditionIds = ownCrew.stream()
                    .map(ApiModels.CrewView::expeditionId)
                    .collect(Collectors.toSet());
            List<ApiModels.ExpeditionView> ownExpeditions = expeditions.stream()
                    .filter(item -> ownExpeditionIds.contains(item.id()))
                    .toList();
            return new ApiModels.DemoState(
                    ownExpeditions, ownCrew, List.of(), List.of(), List.of(), List.of(), List.of(),
                    actor.settlementName(), false);
        }
        if (actor.role() == Role.SHIPBUILDER) {
            List<ApiModels.ShipView> work = ships.stream()
                    .filter(item -> item.stage() < 4 || "IN_CONSTRUCTION".equals(item.requestStatus()))
                    .toList();
            Set<Long> expeditionIds = work.stream()
                    .map(ApiModels.ShipView::expeditionId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            List<ApiModels.StockView> constructionStock = stock.stream()
                    .filter(item -> List.of("WOOD", "CLOTH", "RESIN").contains(item.resource()))
                    .toList();
            return new ApiModels.DemoState(
                    expeditions.stream().filter(item -> expeditionIds.contains(item.id())).toList(),
                    List.of(), List.of(), work, shipTypes, constructionStock, List.of(),
                    actor.settlementName(), false);
        }

        List<ApiModels.ShipView> awaitingBlessing = ships.stream()
                .filter(item -> item.stage() == 3 && !item.blessed())
                .toList();
        Set<Long> expeditionIds = awaitingBlessing.stream()
                .map(ApiModels.ShipView::expeditionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return new ApiModels.DemoState(
                expeditions.stream().filter(item -> expeditionIds.contains(item.id())).toList(),
                List.of(), List.of(), awaitingBlessing, List.of(), List.of(), List.of(),
                actor.settlementName(), false);
    }

    private List<ExpeditionRow> expeditionRows(Long settlementId) {
        return jdbc.query("""
                select id, name, target, status, planned_departure, required_capacity, version,
                       finalized_at is not null as immutable, loot_gold, loot_provisions, loot_thralls
                  from expedition
                 where settlement_id = ?
                 order by planned_departure desc
                """, (rs, rowNum) -> new ExpeditionRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("target"),
                rs.getString("status"),
                rs.getObject("planned_departure", LocalDate.class),
                rs.getInt("required_capacity"),
                rs.getInt("version"),
                rs.getBoolean("immutable"),
                rs.getObject("loot_gold") == null ? null : new ApiModels.LootRequest(
                        rs.getInt("loot_gold"), rs.getInt("loot_provisions"), rs.getInt("loot_thralls"))
        ), settlementId);
    }

    private ApiModels.ExpeditionView toExpeditionView(Long settlementId, ExpeditionRow row) {
        List<ApiModels.FleetShipView> fleet = jdbc.query("""
                select s.id, s.name, st.name as type_name, st.capacity, s.stage,
                       coalesce(br.status, case when s.stage = 4 then 'READY' else 'IN_CONSTRUCTION' end) as request_status
                  from expedition_ship es
                  join ship s on s.id = es.ship_id and s.settlement_id = ?
                  join ship_type st on st.code = s.ship_type_code
                  left join ship_build_request br on br.ship_id = s.id and br.expedition_id = es.expedition_id
                 where es.expedition_id = ?
                 order by s.stage desc, s.name
                """, (rs, rowNum) -> new ApiModels.FleetShipView(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("type_name"),
                rs.getInt("capacity"),
                rs.getInt("stage"),
                rs.getInt("stage") == 4,
                rs.getString("request_status")
        ), settlementId, row.id());
        int readyCapacity = fleet.stream().filter(ApiModels.FleetShipView::ready)
                .mapToInt(ApiModels.FleetShipView::capacity).sum();
        int plannedCapacity = fleet.stream().mapToInt(ApiModels.FleetShipView::capacity).sum();
        return new ApiModels.ExpeditionView(
                row.id(), row.name(), row.target(), row.status(), row.plannedDeparture(),
                row.requiredCapacity(), readyCapacity, plannedCapacity, fleet,
                auditForExpedition(settlementId, row.id()), row.version(), row.immutable(), row.loot());
    }

    private List<ApiModels.AuditView> auditForExpedition(Long settlementId, Long expeditionId) {
        return jdbc.query("""
                select distinct ae.id, ae.happened_at, ae.actor_role, ae.event_type,
                       ae.aggregate_type, ae.aggregate_id, ae.details::text
                  from audit_event ae
                 where ae.settlement_id = ?
                   and (
                       (ae.aggregate_type = 'EXPEDITION' and ae.aggregate_id = ?)
                       or (ae.aggregate_type = 'CREW_ASSIGNMENT' and exists (
                           select 1 from crew_assignment ca
                            where ca.id = ae.aggregate_id and ca.expedition_id = ?
                       ))
                       or (ae.aggregate_type = 'SHIP' and exists (
                           select 1 from expedition_ship es
                            where es.ship_id = ae.aggregate_id and es.expedition_id = ?
                       ))
                   )
                 order by ae.happened_at desc, ae.id desc
                 limit 10
                """, (rs, rowNum) -> new ApiModels.AuditView(
                rs.getLong("id"),
                rs.getTimestamp("happened_at").toInstant(),
                rs.getString("actor_role"),
                rs.getString("event_type"),
                rs.getString("aggregate_type"),
                rs.getLong("aggregate_id"),
                rs.getString("details")
        ), settlementId, expeditionId, expeditionId, expeditionId);
    }

    private List<ApiModels.CrewView> crew(Long settlementId) {
        return jdbc.query("""
                select ca.id, ca.expedition_id, ca.user_id, u.display_name, ca.expedition_role,
                       ca.participation_status, ca.alive, ca.version
                  from crew_assignment ca
                  join app_user u on u.id = ca.user_id
                  join expedition e on e.id = ca.expedition_id
                 where e.settlement_id = ? and ca.participation_status <> 'REMOVED'
                 order by ca.expedition_id, u.display_name
                """, (rs, rowNum) -> new ApiModels.CrewView(
                rs.getLong("id"),
                rs.getLong("expedition_id"),
                rs.getLong("user_id"),
                rs.getString("display_name"),
                rs.getString("expedition_role"),
                rs.getString("participation_status"),
                rs.getBoolean("alive"),
                rs.getInt("version")
        ), settlementId);
    }

    private List<ApiModels.UserView> availableUsers(Long settlementId) {
        return jdbc.query("""
                select u.id, u.display_name, sm.member_role
                  from settlement_membership sm
                  join app_user u on u.id = sm.user_id
                 where sm.settlement_id = ? and sm.member_role = 'WARRIOR'
                   and not exists (
                       select 1 from crew_assignment ca
                       join expedition e on e.id = ca.expedition_id
                        where ca.user_id = u.id
                          and e.settlement_id = sm.settlement_id
                          and ca.participation_status in ('PENDING', 'CONFIRMED')
                          and e.status in ('PREPARATION', 'SAILING')
                   )
                 order by u.display_name
                """, (rs, rowNum) -> new ApiModels.UserView(
                rs.getLong("id"), rs.getString("display_name"), rs.getString("member_role")
        ), settlementId);
    }

    private List<ApiModels.ShipView> ships(Long settlementId) {
        return jdbc.query("""
                select s.id, s.name, s.ship_type_code, st.name as type_name, st.capacity,
                       s.stage, s.blessed, s.version,
                       not exists (
                           select 1 from expedition_ship active_es
                           join expedition active_e on active_e.id = active_es.expedition_id
                            where active_es.ship_id = s.id
                              and active_e.status in ('PREPARATION', 'SAILING')
                       ) as available,
                       br.expedition_id, e.name as expedition_name, br.status as request_status
                  from ship s
                  join ship_type st on st.code = s.ship_type_code
                  left join ship_build_request br on br.ship_id = s.id
                  left join expedition e on e.id = br.expedition_id
                 where s.settlement_id = ?
                 order by s.stage, s.name
                """, (rs, rowNum) -> {
            Long id = rs.getLong("id");
            int stage = rs.getInt("stage");
            List<ApiModels.RequirementView> requirements = jdbc.query("""
                    select r.resource, r.quantity, coalesce(ws.quantity, 0) as available
                      from ship_stage_requirement r
                      left join warehouse_stock ws
                        on ws.resource = r.resource and ws.settlement_id = ?
                     where r.ship_id = ? and r.stage = ?
                     order by r.resource
                    """, (req, reqRow) -> new ApiModels.RequirementView(
                    req.getString("resource"), req.getInt("quantity"), req.getInt("available")
            ), settlementId, id, stage);
            return new ApiModels.ShipView(
                    id, rs.getString("name"), rs.getString("ship_type_code"), rs.getString("type_name"),
                    rs.getInt("capacity"), stage, STAGE_NAMES.get(stage), stage * 25,
                    rs.getBoolean("blessed"), rs.getInt("version"), rs.getBoolean("available"),
                    rs.getObject("expedition_id", Long.class), rs.getString("expedition_name"),
                    rs.getString("request_status"), requirements);
        }, settlementId);
    }

    private List<ApiModels.ShipTypeView> shipTypes() {
        return jdbc.query("select code, name, capacity from ship_type order by capacity", (rs, rowNum) -> {
            String code = rs.getString("code");
            List<ApiModels.RecipeResourceView> recipe = jdbc.query("""
                    select resource, sum(quantity)::integer as quantity
                      from ship_type_requirement
                     where ship_type_code = ?
                     group by resource
                     order by resource
                    """, (req, reqRow) -> new ApiModels.RecipeResourceView(
                    req.getString("resource"), req.getInt("quantity")), code);
            return new ApiModels.ShipTypeView(
                    code, rs.getString("name"), rs.getInt("capacity"), recipe);
        });
    }

    private List<ApiModels.StockView> stock(Long settlementId) {
        return jdbc.query("""
                select resource, quantity, version
                  from warehouse_stock where settlement_id = ? order by resource
                """, (rs, rowNum) -> new ApiModels.StockView(
                rs.getString("resource"), rs.getInt("quantity"), rs.getInt("version")
        ), settlementId);
    }

    private List<ApiModels.AllocationView> allocations(Long settlementId) {
        return jdbc.query("""
                select recipient, category, gold, provisions, thralls
                  from wergild_allocation wa
                  join expedition e on e.id = wa.expedition_id
                 where e.settlement_id = ? order by wa.id
                """, (rs, rowNum) -> new ApiModels.AllocationView(
                rs.getString("recipient"), rs.getString("category"),
                new ApiModels.LootRequest(rs.getInt("gold"), rs.getInt("provisions"), rs.getInt("thralls"))
        ), settlementId);
    }
}
