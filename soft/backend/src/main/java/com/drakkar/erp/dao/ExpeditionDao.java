package com.drakkar.erp.dao;

import com.drakkar.erp.domain.CrewCounts;
import com.drakkar.erp.domain.CrewMember;
import com.drakkar.erp.domain.ExpeditionState;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ExpeditionDao {
    private final NamedParameterJdbcTemplate jdbc;

    public ExpeditionDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ExpeditionState findState(Long settlementId, Long expeditionId, boolean lock) {
        String lockClause = lock ? " for update" : "";
        return jdbc.query("""
                select status, version, finalized_at is not null as immutable
                  from expedition
                 where id = :expeditionId and settlement_id = :settlementId
                """ + lockClause, Map.of(
                "expeditionId", expeditionId,
                "settlementId", settlementId), rs -> rs.next()
                ? new ExpeditionState(
                rs.getString("status"),
                rs.getInt("version"),
                rs.getBoolean("immutable"))
                : null);
    }

    public int readyCapacity(Long settlementId, Long expeditionId) {
        Integer capacity = jdbc.queryForObject("""
                select coalesce(sum(st.capacity), 0)::integer
                  from expedition_ship es
                  join ship s on s.id = es.ship_id and s.settlement_id = :settlementId
                  join ship_type st on st.code = s.ship_type_code
                 where es.expedition_id = :expeditionId and s.stage = 4
                """, Map.of(
                "settlementId", settlementId,
                "expeditionId", expeditionId), Integer.class);
        return capacity == null ? 0 : capacity;
    }

    public int unfinishedShipCount(Long settlementId, Long expeditionId) {
        Integer count = jdbc.queryForObject("""
                select count(*)::integer
                  from expedition_ship es
                  join ship s on s.id = es.ship_id and s.settlement_id = :settlementId
                 where es.expedition_id = :expeditionId and s.stage < 4
                """, Map.of(
                "settlementId", settlementId,
                "expeditionId", expeditionId), Integer.class);
        return count == null ? 0 : count;
    }

    public CrewCounts crewCounts(Long expeditionId) {
        CrewCounts counts = jdbc.queryForObject("""
                select (count(*) filter (where participation_status = 'CONFIRMED'))::integer as confirmed,
                       (count(*) filter (where participation_status = 'PENDING'))::integer as pending
                  from crew_assignment
                 where expedition_id = :expeditionId
                """, Map.of("expeditionId", expeditionId), (rs, rowNum) -> new CrewCounts(
                rs.getInt("confirmed"), rs.getInt("pending")));
        return counts == null ? new CrewCounts(0, 0) : counts;
    }

    public boolean markSailing(Long settlementId, Long expeditionId, int expectedVersion) {
        int changed = jdbc.update("""
                update expedition set status = 'SAILING', version = version + 1
                 where id = :expeditionId and settlement_id = :settlementId
                   and status = 'PREPARATION' and version = :expectedVersion
                """, Map.of(
                "expeditionId", expeditionId,
                "settlementId", settlementId,
                "expectedVersion", expectedVersion));
        return changed == 1;
    }

    public List<CrewMember> confirmedCrew(Long expeditionId) {
        return jdbc.query("""
                select ca.id, u.display_name
                  from crew_assignment ca
                  join app_user u on u.id = ca.user_id
                 where ca.expedition_id = :expeditionId and ca.participation_status = 'CONFIRMED'
                 order by u.display_name
                """, Map.of("expeditionId", expeditionId), (rs, rowNum) -> new CrewMember(
                rs.getLong("id"), rs.getString("display_name")));
    }

    public boolean markFallen(Long expeditionId, Long assignmentId) {
        int changed = jdbc.update("""
                update crew_assignment
                   set alive = false, version = version + 1
                 where expedition_id = :expeditionId and id = :assignmentId
                """, Map.of(
                "expeditionId", expeditionId,
                "assignmentId", assignmentId));
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
                values (:expeditionId, :recipient, :category, :gold, :provisions, :thralls)
                """, Map.of(
                "expeditionId", expeditionId,
                "recipient", recipient,
                "category", category,
                "gold", gold,
                "provisions", provisions,
                "thralls", thralls));
    }

    public void addToWarehouse(Long settlementId, String resource, int amount) {
        jdbc.update("""
                update warehouse_stock
                   set quantity = quantity + :amount, version = version + 1
                 where settlement_id = :settlementId and resource = :resource
                """, Map.of(
                "amount", amount,
                "settlementId", settlementId,
                "resource", resource));
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
                       loot_gold = :gold, loot_provisions = :provisions,
                       loot_thralls = :thralls, version = version + 1
                 where id = :expeditionId and settlement_id = :settlementId
                   and version = :expectedVersion and finalized_at is null
                """, Map.of(
                "gold", gold,
                "provisions", provisions,
                "thralls", thralls,
                "expeditionId", expeditionId,
                "settlementId", settlementId,
                "expectedVersion", expectedVersion));
        return changed == 1;
    }
}
