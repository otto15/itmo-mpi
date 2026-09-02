package com.drakkar.erp.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CrewDao {
    private final JdbcTemplate jdbc;

    public CrewDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean lockPreparationExpedition(Long settlementId, Long expeditionId) {
        Integer found = jdbc.query("""
                select 1 from expedition
                 where id = ? and settlement_id = ? and status = 'PREPARATION'
                   for update
                """, rs -> rs.next() ? 1 : null, expeditionId, settlementId);
        return found != null;
    }

    public boolean lockWarriorMembership(Long settlementId, Long userId) {
        Integer found = jdbc.query("""
                select 1 from settlement_membership
                 where settlement_id = ? and user_id = ? and member_role = 'WARRIOR'
                   for update
                """, rs -> rs.next() ? 1 : null, settlementId, userId);
        return found != null;
    }

    public boolean isOccupied(Long settlementId, Long userId) {
        Integer found = jdbc.query("""
                select 1
                  from crew_assignment ca
                  join expedition e on e.id = ca.expedition_id
                 where ca.user_id = ?
                   and e.settlement_id = ?
                   and ca.participation_status in ('PENDING', 'CONFIRMED')
                   and e.status in ('PREPARATION', 'SAILING')
                 limit 1
                """, rs -> rs.next() ? 1 : null, userId, settlementId);
        return found != null;
    }

    public Long createAssignment(Long expeditionId, Long userId, String expeditionRole) {
        return jdbc.queryForObject("""
                insert into crew_assignment(expedition_id, user_id, expedition_role, participation_status)
                values (?, ?, ?, 'PENDING')
                returning id
                """, Long.class, expeditionId, userId, expeditionRole);
    }

    public boolean updateDecision(
            Long settlementId,
            Long actorId,
            Long assignmentId,
            String decision,
            int expectedVersion
    ) {
        int changed = jdbc.update("""
                update crew_assignment ca
                   set participation_status = ?, version = ca.version + 1
                  from expedition e
                 where ca.id = ?
                   and ca.expedition_id = e.id
                   and ca.user_id = ?
                   and e.settlement_id = ?
                   and e.status <> 'CANCELLED'
                   and ca.participation_status = 'PENDING'
                   and ca.version = ?
                """, decision, assignmentId, actorId, settlementId, expectedVersion);
        return changed == 1;
    }
}
