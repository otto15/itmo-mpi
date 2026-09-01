package com.drakkar.erp.api;

import com.drakkar.erp.application.CrewService;
import com.drakkar.erp.application.AuthService;
import com.drakkar.erp.application.DemoQueryService;
import com.drakkar.erp.application.DemoResetService;
import com.drakkar.erp.application.ExpeditionService;
import com.drakkar.erp.application.ShipyardService;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.Role;
import com.drakkar.erp.domain.RoleGuard;
import com.drakkar.erp.infrastructure.AuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DrakkarController {
    private final DemoQueryService queries;
    private final AuthService auth;
    private final DemoResetService resetService;
    private final CrewService crew;
    private final ShipyardService shipyard;
    private final ExpeditionService expeditions;

    public DrakkarController(
            DemoQueryService queries,
            AuthService auth,
            DemoResetService resetService,
            CrewService crew,
            ShipyardService shipyard,
            ExpeditionService expeditions
    ) {
        this.queries = queries;
        this.auth = auth;
        this.resetService = resetService;
        this.crew = crew;
        this.shipyard = shipyard;
        this.expeditions = expeditions;
    }

    @PostMapping("/auth/login")
    public ApiModels.LoginResponse login(@Valid @RequestBody ApiModels.LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest servletRequest) {
        String authorization = servletRequest.getHeader("Authorization");
        auth.logout(authorization.substring("Bearer ".length()));
    }

    @GetMapping("/demo/state")
    public ApiModels.DemoState state(HttpServletRequest servletRequest) {
        return queries.state(current(servletRequest));
    }

    @PostMapping("/demo/reset")
    public ApiModels.MessageResponse reset(HttpServletRequest servletRequest) {
        RoleGuard.require(current(servletRequest).role(), Role.JARL);
        resetService.reset();
        return new ApiModels.MessageResponse("DEMO_RESET", "Исходное состояние восстановлено");
    }

    @PostMapping("/expeditions/{expeditionId}/crew")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.MessageResponse addCrew(
            @PathVariable UUID expeditionId,
            @Valid @RequestBody ApiModels.AddCrewRequest request,
            HttpServletRequest servletRequest
    ) {
        RoleGuard.require(current(servletRequest).role(), Role.JARL);
        UUID assignmentId = crew.add(expeditionId, request);
        return new ApiModels.MessageResponse("CREW_MEMBER_ASSIGNED", assignmentId.toString());
    }

    @PostMapping("/crew/{assignmentId}/decision")
    public ApiModels.MessageResponse decide(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody ApiModels.CrewDecisionRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = current(servletRequest);
        RoleGuard.require(actor.role(), Role.WARRIOR);
        crew.decide(assignmentId, actor.id(), request);
        return new ApiModels.MessageResponse("PARTICIPATION_UPDATED", "Статус участия обновлён");
    }

    @PostMapping("/ships/{shipId}/complete-stage")
    public ApiModels.MessageResponse completeStage(
            @PathVariable UUID shipId,
            @Valid @RequestBody ApiModels.CompleteStageRequest request,
            HttpServletRequest servletRequest
    ) {
        RoleGuard.require(current(servletRequest).role(), Role.SHIPBUILDER);
        shipyard.completeStage(shipId, request);
        return new ApiModels.MessageResponse("SHIP_STAGE_COMPLETED", "Этап завершён, ресурсы списаны атомарно");
    }

    @PostMapping("/ships/{shipId}/bless")
    public ApiModels.MessageResponse bless(
            @PathVariable UUID shipId,
            HttpServletRequest servletRequest
    ) {
        RoleGuard.require(current(servletRequest).role(), Role.PRIEST);
        shipyard.bless(shipId);
        return new ApiModels.MessageResponse("SHIP_BLESSED", "Блот подтверждён жрецом");
    }

    @PostMapping("/expeditions/{expeditionId}/finalization-preview")
    public List<ApiModels.AllocationView> preview(
            @PathVariable UUID expeditionId,
            @Valid @RequestBody ApiModels.FinalizeRequest request,
            HttpServletRequest servletRequest
    ) {
        RoleGuard.require(current(servletRequest).role(), Role.JARL);
        return expeditions.preview(expeditionId, request);
    }

    @PostMapping("/expeditions/{expeditionId}/finalize")
    public List<ApiModels.AllocationView> finalizeExpedition(
            @PathVariable UUID expeditionId,
            @Valid @RequestBody ApiModels.FinalizeRequest request,
            HttpServletRequest servletRequest
    ) {
        RoleGuard.require(current(servletRequest).role(), Role.JARL);
        return expeditions.finalizeExpedition(expeditionId, request);
    }

    private AuthenticatedUser current(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(AuthenticationFilter.USER_ATTRIBUTE);
    }
}
