package com.drakkar.erp.api;

import com.drakkar.erp.application.CrewService;
import com.drakkar.erp.application.AuthService;
import com.drakkar.erp.application.DemoQueryService;
import com.drakkar.erp.application.DemoResetService;
import com.drakkar.erp.application.ExpeditionService;
import com.drakkar.erp.application.SettlementService;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final SettlementService settlements;

    public DrakkarController(
            DemoQueryService queries,
            AuthService auth,
            DemoResetService resetService,
            CrewService crew,
            ShipyardService shipyard,
            ExpeditionService expeditions,
            SettlementService settlements
    ) {
        this.queries = queries;
        this.auth = auth;
        this.resetService = resetService;
        this.crew = crew;
        this.shipyard = shipyard;
        this.expeditions = expeditions;
        this.settlements = settlements;
    }

    @PostMapping("/auth/login")
    public ApiModels.LoginResponse login(@Valid @RequestBody ApiModels.LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest servletRequest) {
        auth.logout(bearer(servletRequest));
    }

    @GetMapping("/demo/state")
    public ApiModels.DemoState state(HttpServletRequest servletRequest) {
        return queries.state(current(servletRequest));
    }

    @PostMapping("/demo/reset")
    public ApiModels.MessageResponse reset(HttpServletRequest servletRequest) {
        AuthenticatedUser actor = current(servletRequest);
        RoleGuard.require(actor.role(), Role.JARL);
        resetService.reset(actor.settlementId());
        return new ApiModels.MessageResponse("DEMO_RESET", "Исходное состояние восстановлено");
    }

    @PostMapping("/provisioning/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.ProvisionSettlementResponse provisionSettlement(
            @RequestHeader(value = "X-Provisioning-Key", required = false) String provisioningKey,
            @Valid @RequestBody ApiModels.ProvisionSettlementRequest request
    ) {
        return settlements.provision(provisioningKey, request);
    }

    @PostMapping("/expeditions/{expeditionId}/crew")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.MessageResponse addCrew(
            @PathVariable UUID expeditionId,
            @Valid @RequestBody ApiModels.AddCrewRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = current(servletRequest);
        RoleGuard.require(actor.role(), Role.JARL);
        UUID assignmentId = crew.add(actor, expeditionId, request);
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
        crew.decide(actor, assignmentId, request);
        return new ApiModels.MessageResponse("PARTICIPATION_UPDATED", "Статус участия обновлён");
    }

    @PostMapping("/ships/{shipId}/complete-stage")
    public ApiModels.MessageResponse completeStage(
            @PathVariable UUID shipId,
            @Valid @RequestBody ApiModels.CompleteStageRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = current(servletRequest);
        RoleGuard.require(actor.role(), Role.SHIPBUILDER);
        shipyard.completeStage(actor, shipId, request);
        return new ApiModels.MessageResponse("SHIP_STAGE_COMPLETED", "Этап завершён, ресурсы списаны атомарно");
    }

    @PostMapping("/expeditions/{expeditionId}/ships")
    public ApiModels.MessageResponse assignShip(
            @PathVariable UUID expeditionId,
            @Valid @RequestBody ApiModels.AssignShipRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = current(servletRequest);
        RoleGuard.require(actor.role(), Role.JARL);
        shipyard.assignReadyShip(actor, expeditionId, request.shipId());
        return new ApiModels.MessageResponse("SHIP_ASSIGNED", "Корабль добавлен во флот похода");
    }

    @PostMapping("/expeditions/{expeditionId}/ship-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.MessageResponse requestShip(
            @PathVariable UUID expeditionId,
            @Valid @RequestBody ApiModels.RequestShipRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = current(servletRequest);
        RoleGuard.require(actor.role(), Role.JARL);
        UUID requestId = shipyard.requestShip(actor, expeditionId, request);
        return new ApiModels.MessageResponse("SHIP_BUILD_REQUESTED", requestId.toString());
    }

    @PostMapping("/ships/{shipId}/bless")
    public ApiModels.MessageResponse bless(
            @PathVariable UUID shipId,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = current(servletRequest);
        RoleGuard.require(actor.role(), Role.PRIEST);
        shipyard.bless(actor, shipId);
        return new ApiModels.MessageResponse("SHIP_BLESSED", "Блот подтверждён жрецом");
    }

    @PostMapping("/expeditions/{expeditionId}/finalization-preview")
    public List<ApiModels.AllocationView> preview(
            @PathVariable UUID expeditionId,
            @Valid @RequestBody ApiModels.FinalizeRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = current(servletRequest);
        RoleGuard.require(actor.role(), Role.JARL);
        return expeditions.preview(actor, expeditionId, request);
    }

    @PostMapping("/expeditions/{expeditionId}/finalize")
    public List<ApiModels.AllocationView> finalizeExpedition(
            @PathVariable UUID expeditionId,
            @Valid @RequestBody ApiModels.FinalizeRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = current(servletRequest);
        RoleGuard.require(actor.role(), Role.JARL);
        return expeditions.finalizeExpedition(actor, expeditionId, request);
    }

    private AuthenticatedUser current(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(AuthenticationFilter.USER_ATTRIBUTE);
    }

    private String bearer(HttpServletRequest request) {
        return request.getHeader("Authorization").substring("Bearer ".length());
    }
}
