package com.drakkar.erp.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class DemoQueryDao {
    public record ExpeditionRow(
            Long id,
            String name,
            String target,
            String status,
            LocalDate plannedDeparture,
            int crewSize,
            int version,
            boolean immutable,
            Integer lootGold,
            Integer lootProvisions,
            Integer lootThralls
    ) {
    }

    public record FleetShipRow(
            Long id,
            String name,
            String typeName,
            int capacity,
            int stage,
            String requestStatus
    ) {
    }

    public record AuditRow(
            long id,
            Instant happenedAt,
            String actorRole,
            String eventType,
            String aggregateType,
            Long aggregateId,
            String details
    ) {
    }

    public record CrewRow(
            Long id,
            Long expeditionId,
            Long userId,
            String userName,
            String expeditionRole,
            String participationStatus,
            boolean alive,
            int version
    ) {
    }

    public record UserRow(Long id, String displayName, String role) {
    }

    public record RequirementRow(String resource, int quantity, int available) {
    }

    public record ShipRow(
            Long id,
            String name,
            String typeCode,
            String typeName,
            int capacity,
            int stage,
            boolean blessed,
            int version,
            boolean available,
            Long expeditionId,
            String expeditionName,
            String requestStatus,
            List<RequirementRow> requirements
    ) {
    }

    public record RecipeRow(String resource, int quantity) {
    }

    public record ShipTypeRow(String code, String name, int capacity, List<RecipeRow> recipe) {
    }

    public record StockRow(String resource, int quantity, int version) {
    }

    public record AllocationRow(
            String recipient,
            String category,
            int gold,
            int provisions,
            int thralls
    ) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public DemoQueryDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ExpeditionRow> expeditions(Long settlementId) {
        return jdbc.query("""
                select e.id, e.name, e.target, e.status, e.planned_departure,
                       (select count(*)::integer
                          from crew_assignment ca
                         where ca.expedition_id = e.id
                           and ca.participation_status in ('PENDING', 'CONFIRMED')) as crew_size,
                       e.version, e.finalized_at is not null as immutable,
                       e.loot_gold, e.loot_provisions, e.loot_thralls
                  from expedition e
                 where e.settlement_id = :settlementId
                 order by e.planned_departure desc
                """, Map.of("settlementId", settlementId), (rs, rowNum) -> new ExpeditionRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("target"),
                rs.getString("status"),
                rs.getObject("planned_departure", LocalDate.class),
                rs.getInt("crew_size"),
                rs.getInt("version"),
                rs.getBoolean("immutable"),
                rs.getObject("loot_gold", Integer.class),
                rs.getObject("loot_provisions", Integer.class),
                rs.getObject("loot_thralls", Integer.class)
        ));
    }

    public List<FleetShipRow> fleet(Long settlementId, Long expeditionId) {
        return jdbc.query("""
                select s.id, s.name, st.name as type_name, st.capacity, s.stage,
                       coalesce(br.status, case when s.stage = 4 then 'READY' else 'IN_CONSTRUCTION' end) as request_status
                  from expedition_ship es
                  join ship s on s.id = es.ship_id and s.settlement_id = :settlementId
                  join ship_type st on st.code = s.ship_type_code
                  left join ship_build_request br
                    on br.ship_id = s.id and br.expedition_id = es.expedition_id
                 where es.expedition_id = :expeditionId
                 order by s.stage desc, s.name
                """, Map.of(
                "settlementId", settlementId,
                "expeditionId", expeditionId), (rs, rowNum) -> new FleetShipRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("type_name"),
                rs.getInt("capacity"),
                rs.getInt("stage"),
                rs.getString("request_status")
        ));
    }

    public List<AuditRow> expeditionAudit(Long settlementId, Long expeditionId) {
        return jdbc.query("""
                select distinct ae.id, ae.happened_at, ae.actor_role, ae.event_type,
                       ae.aggregate_type, ae.aggregate_id, ae.details::text
                  from audit_event ae
                 where ae.settlement_id = :settlementId
                   and (
                       (ae.aggregate_type = 'EXPEDITION' and ae.aggregate_id = :expeditionId)
                       or (ae.aggregate_type = 'CREW_ASSIGNMENT' and exists (
                           select 1 from crew_assignment ca
                            where ca.id = ae.aggregate_id and ca.expedition_id = :expeditionId
                       ))
                       or (ae.aggregate_type = 'SHIP' and exists (
                           select 1 from expedition_ship es
                            where es.ship_id = ae.aggregate_id and es.expedition_id = :expeditionId
                       ))
                   )
                 order by ae.happened_at desc, ae.id desc
                 limit 10
                """, Map.of(
                "settlementId", settlementId,
                "expeditionId", expeditionId), (rs, rowNum) -> new AuditRow(
                rs.getLong("id"),
                rs.getTimestamp("happened_at").toInstant(),
                rs.getString("actor_role"),
                rs.getString("event_type"),
                rs.getString("aggregate_type"),
                rs.getLong("aggregate_id"),
                rs.getString("details")
        ));
    }

    public List<CrewRow> crew(Long settlementId) {
        return jdbc.query("""
                select ca.id, ca.expedition_id, ca.user_id, u.display_name, ca.expedition_role,
                       ca.participation_status, ca.alive, ca.version
                  from crew_assignment ca
                  join app_user u on u.id = ca.user_id
                  join expedition e on e.id = ca.expedition_id
                 where e.settlement_id = :settlementId and ca.participation_status <> 'REMOVED'
                 order by ca.expedition_id, u.display_name
                """, Map.of("settlementId", settlementId), (rs, rowNum) -> new CrewRow(
                rs.getLong("id"),
                rs.getLong("expedition_id"),
                rs.getLong("user_id"),
                rs.getString("display_name"),
                rs.getString("expedition_role"),
                rs.getString("participation_status"),
                rs.getBoolean("alive"),
                rs.getInt("version")
        ));
    }

    public List<UserRow> availableWarriors(Long settlementId) {
        return jdbc.query("""
                select u.id, u.display_name, sm.member_role
                  from settlement_membership sm
                  join app_user u on u.id = sm.user_id
                 where sm.settlement_id = :settlementId and sm.member_role = 'WARRIOR'
                   and not exists (
                       select 1 from crew_assignment ca
                       join expedition e on e.id = ca.expedition_id
                        where ca.user_id = u.id
                          and e.settlement_id = sm.settlement_id
                          and ca.participation_status in ('PENDING', 'CONFIRMED')
                          and e.status in ('PREPARATION', 'SAILING')
                   )
                 order by u.display_name
                """, Map.of("settlementId", settlementId), (rs, rowNum) -> new UserRow(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getString("member_role")
        ));
    }

    public List<ShipRow> ships(Long settlementId) {
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
                 where s.settlement_id = :settlementId
                 order by s.stage, s.name
                """, Map.of("settlementId", settlementId), (rs, rowNum) -> {
            Long id = rs.getLong("id");
            int stage = rs.getInt("stage");
            return new ShipRow(
                    id,
                    rs.getString("name"),
                    rs.getString("ship_type_code"),
                    rs.getString("type_name"),
                    rs.getInt("capacity"),
                    stage,
                    rs.getBoolean("blessed"),
                    rs.getInt("version"),
                    rs.getBoolean("available"),
                    rs.getObject("expedition_id", Long.class),
                    rs.getString("expedition_name"),
                    rs.getString("request_status"),
                    requirements(settlementId, id, stage));
        });
    }

    public List<ShipTypeRow> shipTypes() {
        return jdbc.query("""
                select code, name, capacity from ship_type order by capacity
                """, Map.of(), (rs, rowNum) -> {
            String code = rs.getString("code");
            return new ShipTypeRow(
                    code,
                    rs.getString("name"),
                    rs.getInt("capacity"),
                    recipe(code));
        });
    }

    public List<StockRow> stock(Long settlementId) {
        return jdbc.query("""
                select resource, quantity, version
                  from warehouse_stock
                 where settlement_id = :settlementId
                 order by resource
                """, Map.of("settlementId", settlementId), (rs, rowNum) -> new StockRow(
                rs.getString("resource"), rs.getInt("quantity"), rs.getInt("version")
        ));
    }

    public List<AllocationRow> allocations(Long settlementId) {
        return jdbc.query("""
                select recipient, category, gold, provisions, thralls
                  from wergild_allocation wa
                  join expedition e on e.id = wa.expedition_id
                 where e.settlement_id = :settlementId
                 order by wa.id
                """, Map.of("settlementId", settlementId), (rs, rowNum) -> new AllocationRow(
                rs.getString("recipient"),
                rs.getString("category"),
                rs.getInt("gold"),
                rs.getInt("provisions"),
                rs.getInt("thralls")
        ));
    }

    private List<RequirementRow> requirements(Long settlementId, Long shipId, int stage) {
        return jdbc.query("""
                select r.resource, r.quantity, coalesce(ws.quantity, 0) as available
                  from ship_stage_requirement r
                  left join warehouse_stock ws
                    on ws.resource = r.resource and ws.settlement_id = :settlementId
                 where r.ship_id = :shipId and r.stage = :stage
                 order by r.resource
                """, Map.of(
                "settlementId", settlementId,
                "shipId", shipId,
                "stage", stage), (rs, rowNum) -> new RequirementRow(
                rs.getString("resource"), rs.getInt("quantity"), rs.getInt("available")
        ));
    }

    private List<RecipeRow> recipe(String typeCode) {
        return jdbc.query("""
                select resource, sum(quantity)::integer as quantity
                  from ship_type_requirement
                 where ship_type_code = :typeCode
                 group by resource
                 order by resource
                """, Map.of("typeCode", typeCode), (rs, rowNum) -> new RecipeRow(
                rs.getString("resource"), rs.getInt("quantity")
        ));
    }
}
