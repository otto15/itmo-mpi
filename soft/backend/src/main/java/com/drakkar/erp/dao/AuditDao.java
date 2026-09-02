package com.drakkar.erp.dao;

import com.drakkar.erp.domain.Role;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class AuditDao {
    private final NamedParameterJdbcTemplate jdbc;

    public AuditDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(
            Long settlementId,
            Role actor,
            String eventType,
            String aggregateType,
            Long aggregateId,
            String jsonDetails
    ) {
        jdbc.update("""
                insert into audit_event(settlement_id, actor_role, event_type, aggregate_type, aggregate_id, details)
                values (:settlementId, :actorRole, :eventType, :aggregateType, :aggregateId, cast(:details as jsonb))
                """, Map.of(
                "settlementId", settlementId,
                "actorRole", actor.name(),
                "eventType", eventType,
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "details", jsonDetails));
    }
}
