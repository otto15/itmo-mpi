package com.drakkar.erp.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiModels {
    private ApiModels() {
    }

    public record DemoState(
            List<ExpeditionView> expeditions,
            List<CrewView> crew,
            List<UserView> availableUsers,
            List<ShipView> ships,
            List<ShipTypeView> shipTypes,
            List<StockView> stock,
            List<AllocationView> allocations,
            String activeSettlementName,
            boolean demoResetAvailable
    ) {
    }

    public record ExpeditionView(
            Long id,
            String name,
            String target,
            String status,
            LocalDate plannedDeparture,
            int requiredCapacity,
            int readyCapacity,
            int plannedCapacity,
            List<FleetShipView> fleet,
            List<AuditView> audit,
            int version,
            boolean immutable,
            LootRequest loot
    ) {
    }

    public record CrewView(
            Long id,
            Long expeditionId,
            Long userId,
            String userName,
            String expeditionRole,
            String participationStatus,
            boolean alive,
            int version
    ) {
    }

    public record UserView(Long id, String displayName, String systemRole) {
    }

    public record ShipView(
            Long id,
            String name,
            String typeCode,
            String typeName,
            int capacity,
            int stage,
            String stageName,
            int progress,
            boolean blessed,
            int version,
            boolean available,
            Long expeditionId,
            String expeditionName,
            String requestStatus,
            List<RequirementView> requirements
    ) {
    }

    public record FleetShipView(
            Long id,
            String name,
            String typeName,
            int capacity,
            int stage,
            boolean ready,
            String requestStatus
    ) {
    }

    public record ShipTypeView(
            String code,
            String name,
            int capacity,
            List<RecipeResourceView> recipe
    ) {
    }

    public record RecipeResourceView(String resource, int quantity) {
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
            Long aggregateId,
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
            @NotNull Long userId,
            @NotBlank String expeditionRole
    ) {
    }

    public record CompleteStageRequest(@Min(0) int expectedVersion) {
    }

    public record AssignShipRequest(@NotNull Long shipId) {
    }

    public record StartExpeditionRequest(@Min(0) int expectedVersion) {
    }

    public record RequestShipRequest(
            @NotBlank @Size(max = 120) String shipName,
            @NotBlank String shipTypeCode
    ) {
    }

    public record FinalizeRequest(
            @Valid @NotNull LootRequest loot,
            List<Long> fallenAssignmentIds,
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
            Long userId,
            String displayName,
            String role
    ) {
    }

    public record ProvisionSettlementRequest(
            @NotBlank @Size(max = 160) String settlementName,
            @NotBlank @Size(max = 120) String jarlDisplayName,
            @NotBlank @Size(max = 80)
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "допустимы латинские буквы, цифры, точка, дефис и подчёркивание")
            String username,
            @NotBlank @Size(min = 10, max = 200) String password
    ) {
    }

    public record ProvisionSettlementResponse(Long settlementId, String settlementName, String username) {
    }

    public record ErrorResponse(String code, String message, Instant timestamp) {
    }
}
