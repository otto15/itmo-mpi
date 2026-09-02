package com.drakkar.erp.dao;

import com.drakkar.erp.domain.ShipRequirement;
import com.drakkar.erp.domain.ShipState;
import com.drakkar.erp.domain.ShipTypeDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ShipyardDao {
    private final JdbcTemplate jdbc;

    public ShipyardDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ShipState findShipForUpdate(Long settlementId, Long shipId) {
        return jdbc.query("""
                select stage, blessed, version
                  from ship
                 where id = ? and settlement_id = ?
                   for update
                """, rs -> rs.next() ? new ShipState(
                rs.getInt("stage"), rs.getBoolean("blessed"), rs.getInt("version")) : null,
                shipId, settlementId);
    }

    public List<ShipRequirement> lockStageRequirements(Long settlementId, Long shipId, int stage) {
        return jdbc.query("""
                select r.resource, r.quantity as required, s.quantity as available
                  from ship_stage_requirement r
                  join warehouse_stock s
                    on s.resource = r.resource and s.settlement_id = ?
                 where r.ship_id = ? and r.stage = ?
                 order by r.resource
                   for update of s
                """, (rs, rowNum) -> new ShipRequirement(
                rs.getString("resource"), rs.getInt("required"), rs.getInt("available")),
                settlementId, shipId, stage);
    }

    public void deductStock(Long settlementId, ShipRequirement requirement) {
        jdbc.update("""
                update warehouse_stock
                   set quantity = quantity - ?, version = version + 1
                 where resource = ? and settlement_id = ?
                """, requirement.required(), requirement.resource(), settlementId);
    }

    public void advanceShip(Long settlementId, Long shipId) {
        jdbc.update("""
                update ship set stage = stage + 1, version = version + 1
                 where id = ? and settlement_id = ?
                """, shipId, settlementId);
    }

    public void markBuildRequestReady(Long settlementId, Long shipId) {
        jdbc.update("""
                update ship_build_request set status = 'READY'
                 where ship_id = ? and settlement_id = ? and status = 'IN_CONSTRUCTION'
                """, shipId, settlementId);
    }

    public boolean lockReadyShip(Long settlementId, Long shipId) {
        Integer found = jdbc.query("""
                select 1 from ship
                 where id = ? and settlement_id = ? and stage = 4
                   for update
                """, rs -> rs.next() ? 1 : null, shipId, settlementId);
        return found != null;
    }

    public Long findActiveExpeditionForShip(Long shipId) {
        return jdbc.query("""
                select e.id
                  from expedition_ship es
                  join expedition e on e.id = es.expedition_id
                 where es.ship_id = ? and e.status in ('PREPARATION', 'SAILING')
                 limit 1
                """, rs -> rs.next() ? rs.getLong("id") : null, shipId);
    }

    public void addShipToExpedition(Long expeditionId, Long shipId) {
        jdbc.update("""
                insert into expedition_ship(expedition_id, ship_id) values (?, ?)
                """, expeditionId, shipId);
    }

    public boolean lockAssignedShip(Long settlementId, Long expeditionId, Long shipId) {
        Integer found = jdbc.query("""
                select 1
                  from expedition_ship es
                  join ship s on s.id = es.ship_id
                 where es.expedition_id = ? and es.ship_id = ? and s.settlement_id = ?
                   for update of es, s
                """, rs -> rs.next() ? 1 : null, expeditionId, shipId, settlementId);
        return found != null;
    }

    public void removeShipFromExpedition(Long expeditionId, Long shipId) {
        jdbc.update("""
                delete from expedition_ship where expedition_id = ? and ship_id = ?
                """, expeditionId, shipId);
    }

    public void detachBuildRequest(Long settlementId, Long expeditionId, Long shipId) {
        jdbc.update("""
                update ship_build_request set expedition_id = null
                 where settlement_id = ? and expedition_id = ? and ship_id = ?
                """, settlementId, expeditionId, shipId);
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
                 where e.id = ? and e.settlement_id = ?
                """, Integer.class, expeditionId, settlementId);
        return shortage == null ? 0 : shortage;
    }

    public ShipTypeDefinition findShipType(String typeCode) {
        return jdbc.query("""
                select code, capacity from ship_type where code = ?
                """, rs -> rs.next() ? new ShipTypeDefinition(
                rs.getString("code"), rs.getInt("capacity")) : null, typeCode);
    }

    public boolean shipNameExists(Long settlementId, String name) {
        Integer found = jdbc.query("""
                select 1 from ship where settlement_id = ? and lower(name) = lower(?)
                """, rs -> rs.next() ? 1 : null, settlementId, name);
        return found != null;
    }

    public Long createShip(Long settlementId, String name, String typeCode) {
        return jdbc.queryForObject("""
                insert into ship(settlement_id, name, ship_type_code, stage, blessed)
                values (?, ?, ?, 0, false)
                returning id
                """, Long.class, settlementId, name, typeCode);
    }

    public void snapshotTypeRequirements(Long shipId, String typeCode) {
        jdbc.update("""
                insert into ship_stage_requirement(ship_id, stage, resource, quantity)
                select ?, stage, resource, quantity
                  from ship_type_requirement where ship_type_code = ?
                """, shipId, typeCode);
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
                ) values (?, ?, ?, ?, ?, 'IN_CONSTRUCTION')
                returning id
                """, Long.class, settlementId, expeditionId, typeCode, shipId, requestedBy);
    }

    public boolean blessShip(Long settlementId, Long shipId) {
        int changed = jdbc.update("""
                update ship set blessed = true, version = version + 1
                 where id = ? and settlement_id = ? and blessed = false and stage = 3
                """, shipId, settlementId);
        return changed == 1;
    }

    public String lockExpeditionStatus(Long settlementId, Long expeditionId) {
        return jdbc.query("""
                select status from expedition
                 where id = ? and settlement_id = ?
                   for update
                """, rs -> rs.next() ? rs.getString("status") : null, expeditionId, settlementId);
    }
}
