package com.drakkar.erp.integration;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.application.CrewService;
import com.drakkar.erp.application.AuthService;
import com.drakkar.erp.application.DemoResetService;
import com.drakkar.erp.application.ExpeditionService;
import com.drakkar.erp.application.ShipyardService;
import com.drakkar.erp.domain.DomainException;
import com.drakkar.erp.domain.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ArchitectureSliceIntegrationTest {
    private static final UUID PREPARATION_ASSIGNMENT = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID HALVDAN = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final UUID SAILING_EXPEDITION = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID FALLEN_ASSIGNMENT = UUID.fromString("00000000-0000-0000-0000-000000000312");
    private static final UUID SHIP = UUID.fromString("00000000-0000-0000-0000-000000000401");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.initialization-fail-timeout", () -> "30000");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");
        registry.add("drakkar.provisioning-key", () -> "test-provisioning-key");
    }

    @Autowired CrewService crew;
    @Autowired AuthService auth;
    @Autowired ShipyardService shipyard;
    @Autowired ExpeditionService expeditions;
    @Autowired DemoResetService reset;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;

    @BeforeEach
    void restoreFixture() {
        reset.reset();
    }

    @Test
    void authenticatesLocallyAndNeverStoresRawSessionToken() {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest("ragnar", "raven-2026"));

        assertThat(login.role()).isEqualTo("JARL");
        assertThat(auth.authenticate(login.token()).displayName()).isEqualTo("Рагнар Лодброк");
        assertThat(jdbc.queryForObject(
                "select count(*) from user_session where token_hash = ?", Integer.class, login.token())).isZero();

        assertThatThrownBy(() -> auth.login(new ApiModels.LoginRequest("ragnar", "wrong-password")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Неверный логин или пароль");
    }

    @Test
    void protectedHttpEndpointBindsPathAndReturnsDomainConflict() throws Exception {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest("halvdan", "shield-2026"));
        String payload = "{\"decision\":\"CONFIRMED\",\"expectedVersion\":0}";

        mockMvc.perform(post("/api/crew/{id}/decision", PREPARATION_ASSIGNMENT)
                        .header("Authorization", "Bearer " + login.token())
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/crew/{id}/decision", PREPARATION_ASSIGNMENT)
                        .header("Authorization", "Bearer " + login.token())
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_CREW_ASSIGNMENT"));
    }

    @Test
    void warriorHttpViewContainsOnlyOwnExpeditionAndNoPrivilegedData() throws Exception {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest("halvdan", "shield-2026"));

        mockMvc.perform(get("/api/demo/state")
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ship").doesNotExist())
                .andExpect(jsonPath("$.stock").isEmpty())
                .andExpect(jsonPath("$.availableUsers").isEmpty())
                .andExpect(jsonPath("$.audit").isEmpty())
                .andExpect(jsonPath("$.crew.length()").value(1))
                .andExpect(jsonPath("$.crew[0].userId").value(HALVDAN.toString()))
                .andExpect(jsonPath("$.expeditions.length()").value(1));
    }

    @Test
    void logoutRevokesOpaqueSessionToken() throws Exception {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest("ragnar", "raven-2026"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/demo/state")
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
    }

    @Test
    void staleParticipationDecisionIsRejected() {
        var decision = new ApiModels.CrewDecisionRequest("CONFIRMED", 0);
        AuthenticatedUser warrior = login("halvdan", "shield-2026");

        crew.decide(warrior, PREPARATION_ASSIGNMENT, decision);

        assertThatThrownBy(() -> crew.decide(warrior, PREPARATION_ASSIGNMENT, decision))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("состав экспедиции уже был изменён");
        assertThat(jdbc.queryForObject(
                "select participation_status from crew_assignment where id = ?", String.class, PREPARATION_ASSIGNMENT))
                .isEqualTo("CONFIRMED");
    }

    @Test
    void jarlAssignsAvailableWarriorOnlyOnceAndWritesAudit() {
        AuthenticatedUser jarl = login("ragnar", "raven-2026");
        UUID thorstein = UUID.fromString("00000000-0000-0000-0000-000000000105");
        UUID preparationExpedition = UUID.fromString("00000000-0000-0000-0000-000000000202");
        var request = new ApiModels.AddCrewRequest(thorstein, "разведчик");

        UUID assignmentId = crew.add(jarl, preparationExpedition, request);

        assertThat(jdbc.queryForObject(
                "select participation_status from crew_assignment where id = ?",
                String.class, assignmentId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event
                 where settlement_id = ? and event_type = 'CREW_MEMBER_ASSIGNED'
                """, Integer.class, DemoResetService.DEFAULT_SETTLEMENT_ID)).isEqualTo(1);

        assertThatThrownBy(() -> crew.add(jarl, preparationExpedition, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("уже задействован в другом походе");
        assertThat(jdbc.queryForObject("""
                select count(*) from crew_assignment
                 where user_id = ? and participation_status <> 'REMOVED'
                """, Integer.class, thorstein)).isEqualTo(1);
    }

    @Test
    void insufficientStockRollsBackBothStockAndStage() {
        AuthenticatedUser shipbuilder = login("floki", "oak-2026");
        jdbc.update("""
                update warehouse_stock set quantity = 5
                 where settlement_id = ? and resource = 'RESIN'
                """, DemoResetService.DEFAULT_SETTLEMENT_ID);

        assertThatThrownBy(() -> shipyard.completeStage(
                shipbuilder, SHIP, new ApiModels.CompleteStageRequest(0)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Недостаточно ресурса RESIN");

        assertThat(jdbc.queryForObject("select stage from ship where id = ?", Integer.class, SHIP)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select quantity from warehouse_stock where settlement_id = ? and resource = 'WOOD'",
                Integer.class, DemoResetService.DEFAULT_SETTLEMENT_ID)).isEqualTo(120);
    }

    @Test
    void finalShipStageRequiresPriestBlessing() {
        AuthenticatedUser shipbuilder = login("floki", "oak-2026");
        AuthenticatedUser priest = login("godi", "blot-2026");
        jdbc.update("update ship set stage = 3 where id = ?", SHIP);

        assertThatThrownBy(() -> shipyard.completeStage(
                shipbuilder, SHIP, new ApiModels.CompleteStageRequest(0)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Ожидается благословение Жреца");

        shipyard.bless(priest, SHIP);
        shipyard.completeStage(shipbuilder, SHIP, new ApiModels.CompleteStageRequest(1));

        assertThat(jdbc.queryForObject("select stage from ship where id = ?", Integer.class, SHIP)).isEqualTo(4);
    }

    @Test
    void finalizationCommitsLedgerAndDatabaseMakesResultsImmutable() {
        AuthenticatedUser jarl = login("ragnar", "raven-2026");
        var request = new ApiModels.FinalizeRequest(
                new ApiModels.LootRequest(100, 50, 10), List.of(FALLEN_ASSIGNMENT), 0);

        List<ApiModels.AllocationView> preview = expeditions.preview(jarl, SAILING_EXPEDITION, request);
        List<ApiModels.AllocationView> committed = expeditions.finalizeExpedition(jarl, SAILING_EXPEDITION, request);

        assertThat(committed).isEqualTo(preview);
        assertThat(jdbc.queryForObject(
                "select status from expedition where id = ?", String.class, SAILING_EXPEDITION)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "select quantity from warehouse_stock where settlement_id = ? and resource = 'GOLD'",
                Integer.class, DemoResetService.DEFAULT_SETTLEMENT_ID)).isEqualTo(140);
        assertThat(jdbc.queryForObject(
                "select count(*) from wergild_allocation where expedition_id = ?", Integer.class, SAILING_EXPEDITION))
                .isEqualTo(preview.size());

        assertThatThrownBy(() -> jdbc.update(
                "update expedition set loot_gold = 999 where id = ?", SAILING_EXPEDITION))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> expeditions.finalizeExpedition(jarl, SAILING_EXPEDITION, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("доступны только для чтения");
    }

    @Test
    void provisioningCreatesIsolatedSettlementAndItsFirstJarlAccount() throws Exception {
        String payload = """
                {
                  "settlementName":"Бирка",
                  "jarlDisplayName":"Эрик Биркский",
                  "username":"erik",
                  "password":"birka-pass-2026"
                }
                """;

        mockMvc.perform(post("/api/provisioning/settlements")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROVISIONING_FORBIDDEN"));

        mockMvc.perform(post("/api/provisioning/settlements")
                        .header("X-Provisioning-Key", "test-provisioning-key")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settlementName").value("Бирка"))
                .andExpect(jsonPath("$.username").value("erik"));

        ApiModels.LoginResponse birkaLogin = auth.login(new ApiModels.LoginRequest("erik", "birka-pass-2026"));
        String birkaAuthorization = "Bearer " + birkaLogin.token();

        mockMvc.perform(get("/api/demo/state").header("Authorization", birkaAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSettlementName").value("Бирка"))
                .andExpect(jsonPath("$.expeditions").isEmpty())
                .andExpect(jsonPath("$.stock.length()").value(6))
                .andExpect(jsonPath("$.settlements").doesNotExist());

        String finalizePayload = """
                {"loot":{"gold":1,"provisions":1,"thralls":0},"fallenAssignmentIds":[],"expectedVersion":0}
                """;
        mockMvc.perform(post("/api/expeditions/{id}/finalization-preview", SAILING_EXPEDITION)
                        .header("Authorization", birkaAuthorization)
                        .contentType("application/json")
                        .content(finalizePayload))
                .andExpect(status().isNotFound());

        ApiModels.LoginResponse kattegatLogin = auth.login(new ApiModels.LoginRequest("ragnar", "raven-2026"));
        mockMvc.perform(get("/api/demo/state")
                        .header("Authorization", "Bearer " + kattegatLogin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSettlementName").value("Каттегат"))
                .andExpect(jsonPath("$.expeditions.length()").value(2));
    }

    private AuthenticatedUser login(String username, String password) {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest(username, password));
        return auth.authenticate(login.token());
    }
}
