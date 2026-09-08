package com.drakkar.erp.api;

import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.Role;
import com.drakkar.erp.domain.RoleGuard;
import com.drakkar.erp.dto.ApiModels.*;
import com.drakkar.erp.infrastructure.AuthenticationFilter;
import com.drakkar.erp.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expeditions/{id}")
public class PreparationController {
    private final ReservationService reservations;

    public PreparationController(ReservationService reservations) { this.reservations = reservations; }

    @PostMapping("/preparation")
    public MessageResponse save(@PathVariable Long id, @Valid @RequestBody PreparationRequest request, HttpServletRequest servlet) {
        reservations.savePlan(jarl(servlet), id, request);
        return new MessageResponse("PREPARATION_UPDATED", "Маршрут и припасы сохранены");
    }

    @PostMapping("/reservations")
    public MessageResponse reserve(@PathVariable Long id, @Valid @RequestBody ReserveRequest request, HttpServletRequest servlet) {
        reservations.reserve(jarl(servlet), id, request);
        return new MessageResponse("RESOURCES_RESERVED", "Припасы зарезервированы");
    }

    private AuthenticatedUser jarl(HttpServletRequest servlet) {
        var actor = (AuthenticatedUser) servlet.getAttribute(AuthenticationFilter.USER_ATTRIBUTE);
        RoleGuard.require(actor.role(), Role.JARL);
        return actor;
    }
}
