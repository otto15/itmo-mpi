package com.drakkar.erp.application;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DemoQueryService {
    private static final List<String> STAGE_NAMES = List.of(
            "Заготовка леса",
            "Сборка каркаса",
            "Обшивка корпуса",
            "Оснастка и благословение",
            "Готов к спуску"
    );

    private final JdbcTemplate jdbc;

    public DemoQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ApiModels.DemoState state(AuthenticatedUser actor) {
        UUID settlementId = actor.settlementId();
        List<ApiModels.ExpeditionView> expeditions = jdbc.query("""
                select id, name, target, status, planned_departure, ship_name, version,
                       finalized_at is not null as immutable, loot_gold, loot_provisions, loot_thralls
                  from expedition
                 where settlement_id = ?
                 order by planned_departure
                """, (rs, rowNum) -> new ApiModels.ExpeditionView(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("name"),
                rs.getString("target"),
                rs.getString("status"),
                rs.getObject("planned_departure", java.time.LocalDate.class),
                rs.getString("ship_name"),
                rs.getInt("version"),
                rs.getBoolean("immutable"),
                rs.getObject("loot_gold") == null ? null : new ApiModels.LootRequest(
                        rs.getInt("loot_gold"), rs.getInt("loot_provisions"), rs.getInt("loot_thralls"))
        ), settlementId);

        List<ApiModels.CrewView> crew = jdbc.query("""
                select ca.id, ca.expedition_id, ca.user_id, u.display_name, ca.expedition_role,
                       ca.participation_status, ca.alive, ca.version
                from crew_assignment ca
                join app_user u on u.id = ca.user_id
                join expedition e on e.id = ca.expedition_id
                where e.settlement_id = ?
                  and ca.participation_status <> 'REMOVED'
                order by ca.expedition_id, u.display_name
                """, (rs, rowNum) -> new ApiModels.CrewView(
                rs.getObject("id", java.util.UUID.class),
                rs.getObject("expedition_id", java.util.UUID.class),
                rs.getObject("user_id", java.util.UUID.class),
                rs.getString("display_name"),
                rs.getString("expedition_role"),
                rs.getString("participation_status"),
                rs.getBoolean("alive"),
                rs.getInt("version")
        ), settlementId);

        List<ApiModels.UserView> availableUsers = jdbc.query("""
                select u.id, u.display_name, sm.member_role
                from settlement_membership sm
                join app_user u on u.id = sm.user_id
                where sm.settlement_id = ?
                  and sm.member_role = 'WARRIOR'
                  and not exists (
                    select 1 from crew_assignment ca
                    join expedition e on e.id = ca.expedition_id
                    where ca.user_id = u.id
                      and e.settlement_id = sm.settlement_id
                      and ca.participation_status <> 'REMOVED'
                      and e.status in ('PREPARATION', 'SAILING')
                )
                order by u.display_name
                """, (rs, rowNum) -> new ApiModels.UserView(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("display_name"),
                rs.getString("member_role")
        ), settlementId);

        ApiModels.ShipView ship = jdbc.query("""
                select id, name, stage, blessed, version
                  from ship
                 where settlement_id = ?
                 order by name
                 limit 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            var id = rs.getObject("id", java.util.UUID.class);
            int stage = rs.getInt("stage");
            List<ApiModels.RequirementView> requirements = jdbc.query("""
                    select r.resource, r.quantity, s.quantity as available
                    from ship_stage_requirement r
                    join warehouse_stock s
                      on s.resource = r.resource and s.settlement_id = ?
                    where r.ship_id = ? and r.stage = ?
                    order by r.resource
                    """, (req, reqRow) -> new ApiModels.RequirementView(
                    req.getString("resource"), req.getInt("quantity"), req.getInt("available")), settlementId, id, stage);
            return new ApiModels.ShipView(
                    id,
                    rs.getString("name"),
                    stage,
                    STAGE_NAMES.get(stage),
                    stage * 25,
                    rs.getBoolean("blessed"),
                    rs.getInt("version"),
                    requirements
            );
        }, settlementId);

        List<ApiModels.StockView> stock = jdbc.query("""
                select resource, quantity, version
                  from warehouse_stock
                 where settlement_id = ?
                 order by resource
                """, (rs, rowNum) -> new ApiModels.StockView(
                rs.getString("resource"), rs.getInt("quantity"), rs.getInt("version")), settlementId);

        List<ApiModels.AllocationView> allocations = jdbc.query("""
                select recipient, category, gold, provisions, thralls
                  from wergild_allocation wa
                  join expedition e on e.id = wa.expedition_id
                 where e.settlement_id = ?
                 order by wa.id
                """, (rs, rowNum) -> new ApiModels.AllocationView(
                rs.getString("recipient"),
                rs.getString("category"),
                new ApiModels.LootRequest(rs.getInt("gold"), rs.getInt("provisions"), rs.getInt("thralls"))
        ), settlementId);

        List<ApiModels.AuditView> audit = jdbc.query("""
                select id, happened_at, actor_role, event_type, aggregate_type, aggregate_id, details::text
                  from audit_event
                 where settlement_id = ?
                 order by happened_at desc, id desc
                 limit 12
                """, (rs, rowNum) -> new ApiModels.AuditView(
                rs.getLong("id"),
                rs.getTimestamp("happened_at").toInstant(),
                rs.getString("actor_role"),
                rs.getString("event_type"),
                rs.getString("aggregate_type"),
                rs.getObject("aggregate_id", java.util.UUID.class),
                rs.getString("details")
        ), settlementId);

        ApiModels.DemoState full = new ApiModels.DemoState(
                expeditions, crew, availableUsers, ship, stock, allocations, audit,
                actor.settlementName(), DemoResetService.DEFAULT_SETTLEMENT_ID.equals(settlementId));
        if (actor.role() == Role.JARL) {
            return full;
        }
        if (actor.role() == Role.WARRIOR) {
            List<ApiModels.CrewView> ownCrew = crew.stream()
                    .filter(item -> item.userId().equals(actor.id()))
                    .toList();
            var ownExpeditionIds = ownCrew.stream()
                    .map(ApiModels.CrewView::expeditionId)
                    .collect(java.util.stream.Collectors.toSet());
            List<ApiModels.ExpeditionView> ownExpeditions = expeditions.stream()
                    .filter(item -> ownExpeditionIds.contains(item.id()))
                    .toList();
            return new ApiModels.DemoState(
                    ownExpeditions, ownCrew, List.of(), null, List.of(), List.of(), List.of(),
                    actor.settlementName(), false);
        }
        if (actor.role() == Role.SHIPBUILDER) {
            List<ApiModels.StockView> constructionStock = stock.stream()
                    .filter(item -> List.of("WOOD", "CLOTH", "RESIN").contains(item.resource()))
                    .toList();
            return new ApiModels.DemoState(
                    List.of(), List.of(), List.of(), ship, constructionStock, List.of(), List.of(),
                    actor.settlementName(), false);
        }
        return new ApiModels.DemoState(
                List.of(), List.of(), List.of(), ship, List.of(), List.of(), List.of(),
                actor.settlementName(), false);
    }
}
