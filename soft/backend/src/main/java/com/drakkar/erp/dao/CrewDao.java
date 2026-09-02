package com.drakkar.erp.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class CrewDao {
    private final NamedParameterJdbcTemplate jdbc;

    public CrewDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean lockPreparationExpedition(Long settlementId, Long expeditionId) {
        Integer found = jdbc.query("""
                select 1 from expedition
                 where id = :expeditionId and settlement_id = :settlementId and status = 'PREPARATION'
                   for update
                """, Map.of(
                "expeditionId", expeditionId,
                "settlementId", settlementId), rs -> rs.next() ? 1 : null);
        return found != null;
    }

    public boolean lockWarriorMembership(Long settlementId, Long userId) {
        Integer found = jdbc.query("""
                select 1 from settlement_membership
                 where settlement_id = :settlementId and user_id = :userId and member_role = 'WARRIOR'
                   for update
                """, Map.of(
                "settlementId", settlementId,
                "userId", userId), rs -> rs.next() ? 1 : null);
        return found != null;
    }

    public boolean isOccupied(Long settlementId, Long userId) {
        Integer found = jdbc.query("""
                select 1
                  from crew_assignment ca
                  join expedition e on e.id = ca.expedition_id
                 where ca.user_id = :userId
                   and e.settlement_id = :settlementId
                   and ca.participation_status in ('PENDING', 'CONFIRMED')
                   and e.status in ('PREPARATION', 'SAILING')
                 limit 1
                """, Map.of(
                "userId", userId,
                "settlementId", settlementId), rs -> rs.next() ? 1 : null);
        return found != null;
    }

    public Long createAssignment(Long expeditionId, Long userId, String expeditionRole) {
        return jdbc.queryForObject("""
                insert into crew_assignment(expedition_id, user_id, expedition_role, participation_status)
                values (:expeditionId, :userId, :expeditionRole, 'PENDING')
                returning id
                """, Map.of(
                "expeditionId", expeditionId,
                "userId", userId,
                "expeditionRole", expeditionRole), Long.class);
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
                   set participation_status = :decision, version = ca.version + 1
                  from expedition e
                 where ca.id = :assignmentId
                   and ca.expedition_id = e.id
                   and ca.user_id = :actorId
                   and e.settlement_id = :settlementId
                   and e.status <> 'CANCELLED'
                   and ca.participation_status = 'PENDING'
                   and ca.version = :expectedVersion
                """, Map.of(
                "decision", decision,
                "assignmentId", assignmentId,
                "actorId", actorId,
                "settlementId", settlementId,
                "expectedVersion", expectedVersion));
        return changed == 1;
    }
}
