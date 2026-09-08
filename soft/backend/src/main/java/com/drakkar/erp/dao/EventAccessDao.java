package com.drakkar.erp.dao;

import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.Role;
import com.drakkar.erp.domain.StateChanged;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class EventAccessDao {
    private final NamedParameterJdbcTemplate jdbc;

    public EventAccessDao(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean canReceive(AuthenticatedUser user, StateChanged event) {
        if (!user.settlementId().equals(event.settlementId())) return false;
        if (event.aggregateType().equals("SETTLEMENT") || user.role() == Role.JARL) return true;
        if (user.role() == Role.SHIPBUILDER)
            return event.aggregateType().equals("SHIP") || event.aggregateType().equals("EXPEDITION");
        if (user.role() == Role.PRIEST) return event.aggregateType().equals("SHIP");
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from crew_assignment c join expedition e on e.id = c.expedition_id
                    where c.user_id = :user and e.settlement_id = :settlement and (
                        (:type = 'EXPEDITION' and e.id = :id) or
                        (:type = 'CREW_ASSIGNMENT' and c.id = :id) or
                        (:type = 'SHIP' and exists (select 1 from expedition_ship es
                            where es.expedition_id = e.id and es.ship_id = :id))))
                """, Map.of("user", user.id(), "settlement", user.settlementId(),
                "type", event.aggregateType(), "id", event.aggregateId()), Boolean.class));
    }
}
