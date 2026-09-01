package com.drakkar.erp.integration;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.application.CrewService;
import com.drakkar.erp.application.AuthService;
import com.drakkar.erp.application.DemoResetService;
import com.drakkar.erp.application.ExpeditionService;
import com.drakkar.erp.application.ShipyardService;
import com.drakkar.erp.domain.DomainException;
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

        crew.decide(PREPARATION_ASSIGNMENT, HALVDAN, decision);

        assertThatThrownBy(() -> crew.decide(PREPARATION_ASSIGNMENT, HALVDAN, decision))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("состав экспедиции уже был изменён");
        assertThat(jdbc.queryForObject(
                "select participation_status from crew_assignment where id = ?", String.class, PREPARATION_ASSIGNMENT))
                .isEqualTo("CONFIRMED");
    }

    @Test
    void insufficientStockRollsBackBothStockAndStage() {
        jdbc.update("update warehouse_stock set quantity = 5 where resource = 'RESIN'");

        assertThatThrownBy(() -> shipyard.completeStage(SHIP, new ApiModels.CompleteStageRequest(0)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Недостаточно ресурса RESIN");

        assertThat(jdbc.queryForObject("select stage from ship where id = ?", Integer.class, SHIP)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select quantity from warehouse_stock where resource = 'WOOD'", Integer.class)).isEqualTo(120);
    }

    @Test
    void finalShipStageRequiresPriestBlessing() {
        jdbc.update("update ship set stage = 3 where id = ?", SHIP);

        assertThatThrownBy(() -> shipyard.completeStage(SHIP, new ApiModels.CompleteStageRequest(0)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Ожидается благословение Жреца");

        shipyard.bless(SHIP);
        shipyard.completeStage(SHIP, new ApiModels.CompleteStageRequest(1));

        assertThat(jdbc.queryForObject("select stage from ship where id = ?", Integer.class, SHIP)).isEqualTo(4);
    }

    @Test
    void finalizationCommitsLedgerAndDatabaseMakesResultsImmutable() {
        var request = new ApiModels.FinalizeRequest(
                new ApiModels.LootRequest(100, 50, 10), List.of(FALLEN_ASSIGNMENT), 0);

        List<ApiModels.AllocationView> preview = expeditions.preview(SAILING_EXPEDITION, request);
        List<ApiModels.AllocationView> committed = expeditions.finalizeExpedition(SAILING_EXPEDITION, request);

        assertThat(committed).isEqualTo(preview);
        assertThat(jdbc.queryForObject(
                "select status from expedition where id = ?", String.class, SAILING_EXPEDITION)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "select quantity from warehouse_stock where resource = 'GOLD'", Integer.class)).isEqualTo(140);
        assertThat(jdbc.queryForObject(
                "select count(*) from wergild_allocation where expedition_id = ?", Integer.class, SAILING_EXPEDITION))
                .isEqualTo(preview.size());

        assertThatThrownBy(() -> jdbc.update(
                "update expedition set loot_gold = 999 where id = ?", SAILING_EXPEDITION))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> expeditions.finalizeExpedition(SAILING_EXPEDITION, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("доступны только для чтения");
    }
}
