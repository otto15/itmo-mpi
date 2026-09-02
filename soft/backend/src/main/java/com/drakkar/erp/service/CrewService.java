package com.drakkar.erp.service;

import com.drakkar.erp.dto.ApiModels;
import com.drakkar.erp.dao.AuditDao;
import com.drakkar.erp.dao.CrewDao;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CrewService {
    private final CrewDao dao;
    private final AuditDao audit;

    public CrewService(CrewDao dao, AuditDao audit) {
        this.dao = dao;
        this.audit = audit;
    }

    @Transactional
    public Long add(AuthenticatedUser actor, Long expeditionId, ApiModels.AddCrewRequest request) {
        if (!dao.lockPreparationExpedition(actor.settlementId(), expeditionId)) {
            throw DomainException.conflict(
                    "EXPEDITION_NOT_IN_PREPARATION",
                    "Состав можно менять только на этапе подготовки");
        }
        if (!dao.lockWarriorMembership(actor.settlementId(), request.userId())) {
            throw DomainException.notFound("Житель");
        }
        if (dao.isOccupied(actor.settlementId(), request.userId())) {
            throw DomainException.conflict(
                    "WARRIOR_ALREADY_ASSIGNED",
                    "Житель уже задействован в другом походе");
        }

        Long assignmentId = dao.createAssignment(
                expeditionId, request.userId(), request.expeditionRole().trim());
        audit.append(
                actor.settlementId(), actor.role(), "CREW_MEMBER_ASSIGNED", "EXPEDITION", expeditionId,
                "{\"assignmentId\":" + assignmentId + "}");
        return assignmentId;
    }

    @Transactional
    public void decide(AuthenticatedUser actor, Long assignmentId, ApiModels.CrewDecisionRequest request) {
        String decision = request.decision().toUpperCase(Locale.ROOT);
        if (!decision.equals("CONFIRMED") && !decision.equals("DECLINED")) {
            throw DomainException.conflict(
                    "INVALID_DECISION",
                    "Доступны только CONFIRMED и DECLINED");
        }

        if (!dao.updateDecision(
                actor.settlementId(),
                actor.id(),
                assignmentId,
                decision,
                request.expectedVersion())) {
            throw DomainException.conflict(
                    "STALE_CREW_ASSIGNMENT",
                    "Изменения не применены: состав экспедиции уже был изменён");
        }
        audit.append(
                actor.settlementId(), actor.role(), "PARTICIPATION_" + decision,
                "CREW_ASSIGNMENT", assignmentId,
                "{\"expectedVersion\":" + request.expectedVersion() + "}");
    }
}
