package com.drakkar.erp.application;

import com.drakkar.erp.domain.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuditWriter {
    private final JdbcTemplate jdbc;

    public AuditWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(
            UUID settlementId,
            Role actor,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String jsonDetails
    ) {
        jdbc.update("""
                insert into audit_event(settlement_id, actor_role, event_type, aggregate_type, aggregate_id, details)
                values (?, ?, ?, ?, ?, cast(? as jsonb))
                """, settlementId, actor.name(), eventType, aggregateType, aggregateId, jsonDetails);
    }
}
