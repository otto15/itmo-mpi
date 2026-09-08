package com.drakkar.erp.dao;

import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.StateChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditDao {
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public AuditDao(NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public void append(AuthenticatedUser actor, String eventType, String aggregateType, Long aggregateId, String details) {
        append(actor.settlementId(), actor.id(), actor.role().name(), eventType, aggregateType, aggregateId, details);
    }

    public void appendSystem(Long settlementId, String eventType, String aggregateType, Long aggregateId, String details) {
        append(settlementId, null, "SYSTEM", eventType, aggregateType, aggregateId, details);
    }

    private void append(
            Long settlementId,
            Long userId,
            String role,
            String eventType,
            String aggregateType,
            Long aggregateId,
            String jsonDetails
    ) {
        jdbc.update("""
                insert into audit_event(settlement_id, actor_user_id, actor_role, event_type, aggregate_type, aggregate_id, details)
                values (:settlement, :user, :role, :event, :type, :id, cast(:details as jsonb))
                """, new MapSqlParameterSource().addValue("settlement", settlementId).addValue("user", userId)
                .addValue("role", role).addValue("event", eventType).addValue("type", aggregateType)
                .addValue("id", aggregateId).addValue("details", jsonDetails));
        events.publishEvent(new StateChanged(settlementId, aggregateType, aggregateId));
    }
}
