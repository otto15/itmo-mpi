package com.drakkar.erp.dao;

import com.drakkar.erp.domain.ShipRequirement;
import com.drakkar.erp.domain.ShipState;
import com.drakkar.erp.domain.ShipTypeDefinition;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ShipyardDao {
    private final NamedParameterJdbcTemplate jdbc;

    public ShipyardDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ShipState findShipForUpdate(Long settlementId, Long shipId) {
        return jdbc.query("""
                select stage, blessed, version
                  from ship
                 where id = :shipId and settlement_id = :settlementId
                   for update
                """, Map.of(
                "shipId", shipId,
                "settlementId", settlementId), rs -> rs.next() ? new ShipState(
                rs.getInt("stage"), rs.getBoolean("blessed"), rs.getInt("version")) : null);
    }

    public List<ShipRequirement> lockStageRequirements(Long settlementId, Long shipId, int stage) {
        return jdbc.query("""
                select r.resource, r.quantity as required, s.quantity as available
                  from ship_stage_requirement r
                  join warehouse_stock s
                    on s.resource = r.resource and s.settlement_id = :settlementId
                 where r.ship_id = :shipId and r.stage = :stage
                 order by r.resource
                   for update of s
                """, Map.of(
                "settlementId", settlementId,
                "shipId", shipId,
                "stage", stage), (rs, rowNum) -> new ShipRequirement(
                rs.getString("resource"), rs.getInt("required"), rs.getInt("available")));
    }

    public void deductStock(Long settlementId, ShipRequirement requirement) {
        jdbc.update("""
                update warehouse_stock
                   set quantity = quantity - :quantity, version = version + 1
                 where resource = :resource and settlement_id = :settlementId
                """, Map.of(
                "quantity", requirement.required(),
                "resource", requirement.resource(),
                "settlementId", settlementId));
    }

    public void advanceShip(Long settlementId, Long shipId) {
        jdbc.update("""
                update ship set stage = stage + 1, version = version + 1
                 where id = :shipId and settlement_id = :settlementId
                """, Map.of(
                "shipId", shipId,
                "settlementId", settlementId));
    }

    public void markBuildRequestReady(Long settlementId, Long shipId) {
        jdbc.update("""
                update ship_build_request set status = 'READY'
                 where ship_id = :shipId and settlement_id = :settlementId
                   and status = 'IN_CONSTRUCTION'
                """, Map.of(
                "shipId", shipId,
                "settlementId", settlementId));
    }

    public boolean lockReadyShip(Long settlementId, Long shipId) {
        Integer found = jdbc.query("""
                select 1 from ship
                 where id = :shipId and settlement_id = :settlementId and stage = 4
                   for update
                """, Map.of(
                "shipId", shipId,
                "settlementId", settlementId), rs -> rs.next() ? 1 : null);
        return found != null;
    }

    public Long findActiveExpeditionForShip(Long shipId) {
        return jdbc.query("""
                select e.id
                  from expedition_ship es
                  join expedition e on e.id = es.expedition_id
                 where es.ship_id = :shipId and e.status in ('PREPARATION', 'SAILING')
                 limit 1
                """, Map.of("shipId", shipId), rs -> rs.next() ? rs.getLong("id") : null);
    }

    public void addShipToExpedition(Long expeditionId, Long shipId) {
        jdbc.update("""
                insert into expedition_ship(expedition_id, ship_id)
                values (:expeditionId, :shipId)
                """, Map.of(
                "expeditionId", expeditionId,
                "shipId", shipId));
    }

    public boolean lockAssignedShip(Long settlementId, Long expeditionId, Long shipId) {
        Integer found = jdbc.query("""
                select 1
                  from expedition_ship es
                  join ship s on s.id = es.ship_id
                 where es.expedition_id = :expeditionId and es.ship_id = :shipId
                   and s.settlement_id = :settlementId
                   for update of es, s
                """, Map.of(
                "expeditionId", expeditionId,
                "shipId", shipId,
                "settlementId", settlementId), rs -> rs.next() ? 1 : null);
        return found != null;
    }

    public void removeShipFromExpedition(Long expeditionId, Long shipId) {
        jdbc.update("""
                delete from expedition_ship
                 where expedition_id = :expeditionId and ship_id = :shipId
                """, Map.of(
                "expeditionId", expeditionId,
                "shipId", shipId));
    }

    public void detachBuildRequest(Long settlementId, Long expeditionId, Long shipId) {
        jdbc.update("""
                update ship_build_request set expedition_id = null
                 where settlement_id = :settlementId and expedition_id = :expeditionId
                   and ship_id = :shipId
                """, Map.of(
                "settlementId", settlementId,
                "expeditionId", expeditionId,
                "shipId", shipId));
    }

    public int fleetSeatShortage(Long settlementId, Long expeditionId) {
        Integer shortage = jdbc.queryForObject("""
                select (select count(*)::integer
                          from crew_assignment ca
                         where ca.expedition_id = e.id
                           and ca.participation_status in ('PENDING', 'CONFIRMED'))
                       - coalesce((select sum(st.capacity)::integer
                                    from expedition_ship es
                                    join ship s on s.id = es.ship_id and s.settlement_id = e.settlement_id
                                    join ship_type st on st.code = s.ship_type_code
                                   where es.expedition_id = e.id), 0)
                  from expedition e
                 where e.id = :expeditionId and e.settlement_id = :settlementId
                """, Map.of(
                "expeditionId", expeditionId,
                "settlementId", settlementId), Integer.class);
        return shortage == null ? 0 : shortage;
    }

    public ShipTypeDefinition findShipType(String typeCode) {
        return jdbc.query("""
                select code, capacity from ship_type where code = :typeCode
                """, Map.of("typeCode", typeCode), rs -> rs.next() ? new ShipTypeDefinition(
                rs.getString("code"), rs.getInt("capacity")) : null);
    }

    public boolean shipNameExists(Long settlementId, String name) {
        Integer found = jdbc.query("""
                select 1 from ship
                 where settlement_id = :settlementId and lower(name) = lower(:name)
                """, Map.of(
                "settlementId", settlementId,
                "name", name), rs -> rs.next() ? 1 : null);
        return found != null;
    }

    public Long createShip(Long settlementId, String name, String typeCode) {
        return jdbc.queryForObject("""
                insert into ship(settlement_id, name, ship_type_code, stage, blessed)
                values (:settlementId, :name, :typeCode, 0, false)
                returning id
                """, Map.of(
                "settlementId", settlementId,
                "name", name,
                "typeCode", typeCode), Long.class);
    }

    public void snapshotTypeRequirements(Long shipId, String typeCode) {
        jdbc.update("""
                insert into ship_stage_requirement(ship_id, stage, resource, quantity)
                select :shipId, stage, resource, quantity
                  from ship_type_requirement where ship_type_code = :typeCode
                """, Map.of(
                "shipId", shipId,
                "typeCode", typeCode));
    }

    public Long createBuildRequest(
            Long settlementId,
            Long expeditionId,
            String typeCode,
            Long shipId,
            Long requestedBy
    ) {
        return jdbc.queryForObject("""
                insert into ship_build_request(
                    settlement_id, expedition_id, ship_type_code, ship_id, requested_by, status
                ) values (:settlementId, :expeditionId, :typeCode, :shipId, :requestedBy, 'IN_CONSTRUCTION')
                returning id
                """, Map.of(
                "settlementId", settlementId,
                "expeditionId", expeditionId,
                "typeCode", typeCode,
                "shipId", shipId,
                "requestedBy", requestedBy), Long.class);
    }

    public boolean blessShip(Long settlementId, Long shipId) {
        int changed = jdbc.update("""
                update ship set blessed = true, version = version + 1
                 where id = :shipId and settlement_id = :settlementId
                   and blessed = false and stage = 3
                """, Map.of(
                "shipId", shipId,
                "settlementId", settlementId));
        return changed == 1;
    }

    public String lockExpeditionStatus(Long settlementId, Long expeditionId) {
        return jdbc.query("""
                select status from expedition
                 where id = :expeditionId and settlement_id = :settlementId
                   for update
                """, Map.of(
                "expeditionId", expeditionId,
                "settlementId", settlementId), rs -> rs.next() ? rs.getString("status") : null);
    }
}
