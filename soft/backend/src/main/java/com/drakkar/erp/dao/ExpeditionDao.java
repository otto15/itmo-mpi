package com.drakkar.erp.dao;

import com.drakkar.erp.domain.CrewCounts;
import com.drakkar.erp.domain.CrewMember;
import com.drakkar.erp.domain.ExpeditionState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ExpeditionDao {
    private final JdbcTemplate jdbc;

    public ExpeditionDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ExpeditionState findState(Long settlementId, Long expeditionId, boolean lock) {
        String lockClause = lock ? " for update" : "";
        return jdbc.query("""
                select status, version, finalized_at is not null as immutable
                  from expedition
                 where id = ? and settlement_id = ?
                """ + lockClause, rs -> rs.next()
                ? new ExpeditionState(
                rs.getString("status"),
                rs.getInt("version"),
                rs.getBoolean("immutable"))
                : null, expeditionId, settlementId);
    }

    public int readyCapacity(Long settlementId, Long expeditionId) {
        Integer capacity = jdbc.queryForObject("""
                select coalesce(sum(st.capacity), 0)::integer
                  from expedition_ship es
                  join ship s on s.id = es.ship_id and s.settlement_id = ?
                  join ship_type st on st.code = s.ship_type_code
                 where es.expedition_id = ? and s.stage = 4
                """, Integer.class, settlementId, expeditionId);
        return capacity == null ? 0 : capacity;
    }

    public int unfinishedShipCount(Long settlementId, Long expeditionId) {
        Integer count = jdbc.queryForObject("""
                select count(*)::integer
                  from expedition_ship es
                  join ship s on s.id = es.ship_id and s.settlement_id = ?
                 where es.expedition_id = ? and s.stage < 4
                """, Integer.class, settlementId, expeditionId);
        return count == null ? 0 : count;
    }

    public CrewCounts crewCounts(Long expeditionId) {
        CrewCounts counts = jdbc.queryForObject("""
                select (count(*) filter (where participation_status = 'CONFIRMED'))::integer as confirmed,
                       (count(*) filter (where participation_status = 'PENDING'))::integer as pending
                  from crew_assignment
                 where expedition_id = ?
                """, (rs, rowNum) -> new CrewCounts(
                rs.getInt("confirmed"), rs.getInt("pending")), expeditionId);
        return counts == null ? new CrewCounts(0, 0) : counts;
    }

    public boolean markSailing(Long settlementId, Long expeditionId, int expectedVersion) {
        int changed = jdbc.update("""
                update expedition set status = 'SAILING', version = version + 1
                 where id = ? and settlement_id = ? and status = 'PREPARATION' and version = ?
                """, expeditionId, settlementId, expectedVersion);
        return changed == 1;
    }

    public List<CrewMember> confirmedCrew(Long expeditionId) {
        return jdbc.query("""
                select ca.id, u.display_name
                  from crew_assignment ca
                  join app_user u on u.id = ca.user_id
                 where ca.expedition_id = ? and ca.participation_status = 'CONFIRMED'
                 order by u.display_name
                """, (rs, rowNum) -> new CrewMember(
                rs.getLong("id"), rs.getString("display_name")), expeditionId);
    }

    public boolean markFallen(Long expeditionId, Long assignmentId) {
        int changed = jdbc.update("""
                update crew_assignment
                   set alive = false, version = version + 1
                 where expedition_id = ? and id = ?
                """, expeditionId, assignmentId);
        return changed == 1;
    }

    public void addAllocation(
            Long expeditionId,
            String recipient,
            String category,
            int gold,
            int provisions,
            int thralls
    ) {
        jdbc.update("""
                insert into wergild_allocation(expedition_id, recipient, category, gold, provisions, thralls)
                values (?, ?, ?, ?, ?, ?)
                """, expeditionId, recipient, category, gold, provisions, thralls);
    }

    public void addToWarehouse(Long settlementId, String resource, int amount) {
        jdbc.update("""
                update warehouse_stock
                   set quantity = quantity + ?, version = version + 1
                 where settlement_id = ? and resource = ?
                """, amount, settlementId, resource);
    }

    public boolean complete(
            Long settlementId,
            Long expeditionId,
            int expectedVersion,
            int gold,
            int provisions,
            int thralls
    ) {
        int changed = jdbc.update("""
                update expedition
                   set status = 'COMPLETED', finalized_at = now(),
                       loot_gold = ?, loot_provisions = ?, loot_thralls = ?, version = version + 1
                 where id = ? and settlement_id = ? and version = ? and finalized_at is null
                """, gold, provisions, thralls, expeditionId, settlementId, expectedVersion);
        return changed == 1;
    }
}
