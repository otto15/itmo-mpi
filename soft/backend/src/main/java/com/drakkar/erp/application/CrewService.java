package com.drakkar.erp.application;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.DomainException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class CrewService {
    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public CrewService(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public UUID add(AuthenticatedUser actor, UUID expeditionId, ApiModels.AddCrewRequest request) {
        Integer preparation = jdbc.query("""
                select 1 from expedition
                 where id = ? and settlement_id = ? and status = 'PREPARATION'
                   for update
                """, rs -> rs.next() ? 1 : null, expeditionId, actor.settlementId());
        if (preparation == null) {
            throw DomainException.conflict("EXPEDITION_NOT_IN_PREPARATION",
                    "Состав можно менять только на этапе подготовки");
        }

        Integer userLock = jdbc.query("""
                select 1 from settlement_membership
                 where settlement_id = ? and user_id = ? and member_role = 'WARRIOR'
                   for update
                """, rs -> rs.next() ? 1 : null, actor.settlementId(), request.userId());
        if (userLock == null) {
            throw DomainException.notFound("Житель");
        }

        Integer occupied = jdbc.query("""
                select 1
                from crew_assignment ca
                join expedition e on e.id = ca.expedition_id
                where ca.user_id = ?
                  and e.settlement_id = ?
                  and ca.participation_status in ('PENDING', 'CONFIRMED')
                  and e.status in ('PREPARATION', 'SAILING')
                limit 1
                """, rs -> rs.next() ? 1 : null, request.userId(), actor.settlementId());
        if (occupied != null) {
            throw DomainException.conflict("WARRIOR_ALREADY_ASSIGNED",
                    "Житель уже задействован в другом походе");
        }

        UUID assignmentId = UUID.randomUUID();
        jdbc.update("""
                insert into crew_assignment(id, expedition_id, user_id, expedition_role, participation_status)
                values (?, ?, ?, ?, 'PENDING')
                """, assignmentId, expeditionId, request.userId(), request.expeditionRole().trim());
        audit.append(actor.settlementId(), actor.role(), "CREW_MEMBER_ASSIGNED", "EXPEDITION", expeditionId,
                "{\"assignmentId\":\"" + assignmentId + "\"}");
        return assignmentId;
    }

    @Transactional
    public void decide(AuthenticatedUser actor, UUID assignmentId, ApiModels.CrewDecisionRequest request) {
        String decision = request.decision().toUpperCase(Locale.ROOT);
        if (!decision.equals("CONFIRMED") && !decision.equals("DECLINED")) {
            throw DomainException.conflict("INVALID_DECISION", "Доступны только CONFIRMED и DECLINED");
        }

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
                """, decision, assignmentId, actor.id(), actor.settlementId(), request.expectedVersion());
        if (changed == 0) {
            throw DomainException.conflict("STALE_CREW_ASSIGNMENT",
                    "Изменения не применены: состав экспедиции уже был изменён");
        }
        audit.append(actor.settlementId(), actor.role(), "PARTICIPATION_" + decision, "CREW_ASSIGNMENT", assignmentId,
                "{\"expectedVersion\":" + request.expectedVersion() + "}");
    }
}
