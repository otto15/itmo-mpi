package com.drakkar.erp.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {
    }

    public record DemoState(
            List<ExpeditionView> expeditions,
            List<CrewView> crew,
            List<UserView> availableUsers,
            ShipView ship,
            List<StockView> stock,
            List<AllocationView> allocations,
            List<AuditView> audit
    ) {
    }

    public record ExpeditionView(
            UUID id,
            String name,
            String target,
            String status,
            LocalDate plannedDeparture,
            String shipName,
            int version,
            boolean immutable,
            LootRequest loot
    ) {
    }

    public record CrewView(
            UUID id,
            UUID expeditionId,
            UUID userId,
            String userName,
            String expeditionRole,
            String participationStatus,
            boolean alive,
            int version
    ) {
    }

    public record UserView(UUID id, String displayName, String systemRole) {
    }

    public record ShipView(
            UUID id,
            String name,
            int stage,
            String stageName,
            int progress,
            boolean blessed,
            int version,
            List<RequirementView> requirements
    ) {
    }

    public record RequirementView(String resource, int quantity, int available) {
    }

    public record StockView(String resource, int quantity, int version) {
    }

    public record AllocationView(String recipient, String category, LootRequest loot) {
    }

    public record AuditView(
            long id,
            Instant happenedAt,
            String actorRole,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String details
    ) {
    }

    public record LootRequest(
            @Min(0) int gold,
            @Min(0) int provisions,
            @Min(0) int thralls
    ) {
    }

    public record CrewDecisionRequest(
            @NotBlank String decision,
            @Min(0) int expectedVersion
    ) {
    }

    public record AddCrewRequest(
            @NotNull UUID userId,
            @NotBlank String expeditionRole
    ) {
    }

    public record CompleteStageRequest(@Min(0) int expectedVersion) {
    }

    public record FinalizeRequest(
            @Valid @NotNull LootRequest loot,
            List<UUID> fallenAssignmentIds,
            @Min(0) int expectedVersion
    ) {
        public FinalizeRequest {
            fallenAssignmentIds = fallenAssignmentIds == null ? List.of() : List.copyOf(fallenAssignmentIds);
        }
    }

    public record MessageResponse(String code, String message) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record LoginResponse(
            String token,
            Instant expiresAt,
            UUID userId,
            String displayName,
            String role
    ) {
    }

    public record ErrorResponse(String code, String message, Instant timestamp) {
    }
}
