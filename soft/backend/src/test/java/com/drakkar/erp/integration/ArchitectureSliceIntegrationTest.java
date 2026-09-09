package com.drakkar.erp.integration;

import com.drakkar.erp.dto.ApiModels;
import com.drakkar.erp.service.CrewService;
import com.drakkar.erp.service.AuthService;
import com.drakkar.erp.service.DemoResetService;
import com.drakkar.erp.service.ExpeditionService;
import com.drakkar.erp.service.ShipyardService;
import com.drakkar.erp.domain.DomainException;
import com.drakkar.erp.domain.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ArchitectureSliceIntegrationTest {
    private static final Long PREPARATION_ASSIGNMENT = 301L;
    private static final Long HALVDAN = 104L;
    private static final Long BJORN = 101L;
    private static final Long SAILING_EXPEDITION = 201L;
    private static final Long PREPARATION_EXPEDITION = 202L;
    private static final Long READY_PREPARATION_EXPEDITION = 207L;
    private static final Long FALLEN_ASSIGNMENT = 312L;
    private static final Long SHIP = 401L;

    private static final String EXTERNAL_DATABASE_URL = System.getenv("TEST_DATABASE_URL");
    static PostgreSQLContainer<?> postgres;

    static {
        if (EXTERNAL_DATABASE_URL == null || EXTERNAL_DATABASE_URL.isBlank()) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres == null
                ? EXTERNAL_DATABASE_URL : postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres == null
                ? System.getenv().getOrDefault("TEST_DATABASE_USER", "drakkar") : postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres == null
                ? System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "drakkar") : postgres.getPassword());
        registry.add("spring.datasource.hikari.initialization-fail-timeout", () -> "30000");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");
        registry.add("drakkar.provisioning-key", () -> "test-provisioning-key");
        registry.add("drakkar.reservations.ttl", () -> "PT1M");
        registry.add("drakkar.reservations.sweep-ms", () -> "3600000");
        registry.add("drakkar.events.dispatch-ms", () -> "3600000");
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Autowired CrewService crew;
    @Autowired AuthService auth;
    @Autowired ShipyardService shipyard;
    @Autowired ExpeditionService expeditions;
    @Autowired DemoResetService reset;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired com.drakkar.erp.service.ReservationService reservations;
    @Autowired com.drakkar.erp.service.ReservationScheduler scheduler;
    @Autowired com.drakkar.erp.service.DemoQueryService queries;
    @Autowired com.drakkar.erp.service.StateEventService events;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

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
    void everySeededCrewMemberCanAuthenticate() {
        Map<String, String> credentials = Map.of(
                "bjorn", "ironside-2026",
                "ivar", "boneless-2026",
                "halvdan", "shield-2026",
                "thorstein", "red-spear-2026",
                "ulf", "white-wolf-2026",
                "astrid", "shieldmaiden-2026",
                "sigurd", "serpent-eye-2026");
        List<String> crewUsernames = jdbc.queryForList("""
                select distinct u.username
                  from crew_assignment ca
                  join app_user u on u.id = ca.user_id
                 order by u.username
                """, String.class);

        assertThat(crewUsernames).containsExactlyInAnyOrderElementsOf(credentials.keySet());
        credentials.forEach((username, password) ->
                assertThat(auth.login(new ApiModels.LoginRequest(username, password)).token()).isNotBlank());
    }

    @Test
    void demoFixtureContainsSeveralExpeditionsAndHistoryFromEveryRole() {
        assertThat(jdbc.queryForObject("""
                select count(*) from expedition where settlement_id = ?
                """, Integer.class, DemoResetService.DEFAULT_SETTLEMENT_ID)).isEqualTo(7);
        assertThat(jdbc.queryForObject("""
                select count(*) from expedition
                 where settlement_id = ? and status = 'COMPLETED'
                """, Integer.class, DemoResetService.DEFAULT_SETTLEMENT_ID)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where settlement_id = ?
                """, Integer.class, DemoResetService.DEFAULT_SETTLEMENT_ID)).isEqualTo(12);
        assertThat(jdbc.queryForObject("""
                select count(distinct actor_role) from audit_event where settlement_id = ?
                """, Integer.class, DemoResetService.DEFAULT_SETTLEMENT_ID)).isEqualTo(4);
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
                .andExpect(jsonPath("$.ships").isEmpty())
                .andExpect(jsonPath("$.stock").isEmpty())
                .andExpect(jsonPath("$.availableUsers").isEmpty())
                .andExpect(jsonPath("$.crew.length()").value(2))
                .andExpect(jsonPath("$.crew[0].userId").value(HALVDAN))
                .andExpect(jsonPath("$.expeditions.length()").value(2));
    }

    @Test
    void shipbuilderSeesTheExpeditionBehindTheConstructionOrder() throws Exception {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest("floki", "oak-2026"));

        mockMvc.perform(get("/api/demo/state")
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expeditions.length()").value(1))
                .andExpect(jsonPath("$.expeditions[0].name").value("Экспедиция в Нортумбрию"))
                .andExpect(jsonPath("$.ships.length()").value(1))
                .andExpect(jsonPath("$.ships[0].expeditionName").value("Экспедиция в Нортумбрию"))
                .andExpect(jsonPath("$.stock.length()").value(3));
    }

    @Test
    void fleetCapacityComesFromShipTypesAndSeatDemandComesFromInvitedCrew() throws Exception {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest("ragnar", "raven-2026"));

        mockMvc.perform(get("/api/demo/state")
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expeditions[?(@.id == 201)].fleet.length()").value(2))
                .andExpect(jsonPath("$.expeditions[?(@.id == 201)].readyCapacity").value(60))
                .andExpect(jsonPath("$.expeditions[?(@.id == 201)].crewSize").value(3));

        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = 'public' and table_name = 'expedition'
                   and column_name = 'required_capacity'
                """, Integer.class)).isZero();
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
        Long einar = 112L;
        var request = new ApiModels.AddCrewRequest(einar, "разведчик");
        int auditBefore = jdbc.queryForObject("""
                select count(*) from audit_event
                 where settlement_id = ? and event_type = 'CREW_MEMBER_ASSIGNED'
                """, Integer.class, DemoResetService.DEFAULT_SETTLEMENT_ID);

        Long assignmentId = crew.add(jarl, PREPARATION_EXPEDITION, request);

        assertThat(jdbc.queryForObject(
                "select participation_status from crew_assignment where id = ?",
                String.class, assignmentId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event
                 where settlement_id = ? and event_type = 'CREW_MEMBER_ASSIGNED'
                """, Integer.class, DemoResetService.DEFAULT_SETTLEMENT_ID)).isEqualTo(auditBefore + 1);

        assertThatThrownBy(() -> crew.add(jarl, PREPARATION_EXPEDITION, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("уже задействован в другом походе");
        assertThat(jdbc.queryForObject("""
                select count(*) from crew_assignment
                 where user_id = ? and participation_status <> 'REMOVED'
                """, Integer.class, einar)).isEqualTo(1);
    }

    @Test
    void warriorInSailingExpeditionIsVisibleInCrewButUnavailableForAnotherExpedition() throws Exception {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest("ragnar", "raven-2026"));

        mockMvc.perform(get("/api/demo/state")
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crew[?(@.expeditionId == " + SAILING_EXPEDITION
                        + " && @.userId == " + BJORN
                        + " && @.participationStatus == 'CONFIRMED')]").isNotEmpty())
                .andExpect(jsonPath("$.availableUsers[?(@.id == " + BJORN + ")]").isEmpty());

        assertThatThrownBy(() -> crew.add(
                login("ragnar", "raven-2026"),
                PREPARATION_EXPEDITION,
                new ApiModels.AddCrewRequest(BJORN, "разведчик")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("уже задействован в другом походе");
    }

    @Test
    void declinedWarriorBecomesAvailableForAnotherExpedition() {
        AuthenticatedUser warrior = login("halvdan", "shield-2026");
        AuthenticatedUser jarl = login("ragnar", "raven-2026");

        crew.decide(warrior, PREPARATION_ASSIGNMENT, new ApiModels.CrewDecisionRequest("DECLINED", 0));
        Long assignmentId = crew.add(jarl, READY_PREPARATION_EXPEDITION,
                new ApiModels.AddCrewRequest(HALVDAN, "разведчик"));

        assertThat(jdbc.queryForObject(
                "select expedition_id from crew_assignment where id = ?",
                Long.class, assignmentId)).isEqualTo(READY_PREPARATION_EXPEDITION);
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
        String username = "harald_" + Long.toUnsignedString(System.nanoTime());
        String payload = """
                {
                  "settlementName":"Хедебю",
                  "jarlDisplayName":"Харальд Хедебюский",
                  "username":"%s",
                  "password":"hedeby-pass-2026"
                }
                """.formatted(username);

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
                .andExpect(jsonPath("$.settlementName").value("Хедебю"))
                .andExpect(jsonPath("$.username").value(username));

        ApiModels.LoginResponse hedebyLogin = auth.login(new ApiModels.LoginRequest(username, "hedeby-pass-2026"));
        String hedebyAuthorization = "Bearer " + hedebyLogin.token();

        mockMvc.perform(get("/api/demo/state").header("Authorization", hedebyAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSettlementName").value("Хедебю"))
                .andExpect(jsonPath("$.expeditions").isEmpty())
                .andExpect(jsonPath("$.stock.length()").value(6))
                .andExpect(jsonPath("$.settlements").doesNotExist());

        String finalizePayload = """
                {"loot":{"gold":1,"provisions":1,"thralls":0},"fallenAssignmentIds":[],"expectedVersion":0}
                """;
        mockMvc.perform(post("/api/expeditions/{id}/finalization-preview", SAILING_EXPEDITION)
                        .header("Authorization", hedebyAuthorization)
                        .contentType("application/json")
                        .content(finalizePayload))
                .andExpect(status().isNotFound());

        ApiModels.LoginResponse kattegatLogin = auth.login(new ApiModels.LoginRequest("ragnar", "raven-2026"));
        mockMvc.perform(get("/api/demo/state")
                        .header("Authorization", "Bearer " + kattegatLogin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSettlementName").value("Каттегат"))
                .andExpect(jsonPath("$.expeditions.length()").value(7));
    }

    @Test
    void seededBirkaAccountSeesOnlyItsOwnExpeditionAndWarehouse() throws Exception {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest("erik", "birka-2026"));

        mockMvc.perform(get("/api/demo/state")
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSettlementName").value("Бирка"))
                .andExpect(jsonPath("$.expeditions.length()").value(1))
                .andExpect(jsonPath("$.expeditions[0].name").value("Поход к Готланду"))
                .andExpect(jsonPath("$.ships.length()").value(1))
                .andExpect(jsonPath("$.stock[?(@.resource == 'WOOD')].quantity").value(45));
    }

    @Test
    void jarlRequestsShipWhenInvitedCrewHasNoPlannedSeats() {
        AuthenticatedUser jarl = login("ragnar", "raven-2026");
        Long preparation = 208L;

        assertThat(jdbc.queryForObject("""
                select count(*) from crew_assignment
                 where expedition_id = ? and participation_status in ('PENDING', 'CONFIRMED')
                """, Integer.class, preparation)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from expedition_ship where expedition_id = ?",
                Integer.class, preparation)).isZero();

        Long requestId = shipyard.requestShip(
                jarl,
                preparation,
                new ApiModels.RequestShipRequest("Скат", "KNOERR"));

        Long shipId = jdbc.queryForObject(
                "select ship_id from ship_build_request where id = ?", Long.class, requestId);
        assertThat(jdbc.queryForObject(
                "select count(*) from expedition_ship where expedition_id = ? and ship_id = ?",
                Integer.class, preparation, shipId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select sum(quantity) from ship_stage_requirement where ship_id = ?",
                Integer.class, shipId)).isEqualTo(87);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where aggregate_id = ? and event_type = 'SHIP_BUILD_REQUESTED'",
                Integer.class, preparation)).isEqualTo(1);

        assertThatThrownBy(() -> shipyard.requestShip(
                jarl,
                preparation,
                new ApiModels.RequestShipRequest("Скат II", "KNOERR")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Плановая вместимость флота уже набрана");
    }

    @Test
    void jarlAddsAFreeReadyShipToAnExpeditionFleet() {
        AuthenticatedUser jarl = login("ragnar", "raven-2026");
        Long preparation = 202L;
        Long freeShip = 402L;

        shipyard.assignReadyShip(jarl, preparation, freeShip);

        assertThat(jdbc.queryForObject(
                "select count(*) from expedition_ship where expedition_id = ? and ship_id = ?",
                Integer.class, preparation, freeShip)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where aggregate_id = ? and event_type = 'SHIP_ASSIGNED'",
                Integer.class, preparation)).isEqualTo(1);
        assertThatThrownBy(() -> shipyard.assignReadyShip(jarl, preparation, freeShip))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("уже назначен в активный поход");
    }

    @Test
    void jarlRemovesShipFromPreparationFleetAndDetachesItsBuildOrder() throws Exception {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest("ragnar", "raven-2026"));
        Long preparation = 202L;

        mockMvc.perform(delete("/api/expeditions/{expeditionId}/ships/{shipId}", preparation, SHIP)
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("""
                select count(*) from expedition_ship where expedition_id = ? and ship_id = ?
                """, Integer.class, preparation, SHIP)).isZero();
        assertThat(jdbc.queryForObject("""
                select expedition_id from ship_build_request where ship_id = ?
                """, Long.class, SHIP)).isNull();
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where aggregate_id = ? and event_type = 'SHIP_REMOVED'
                """, Integer.class, preparation)).isEqualTo(1);
    }

    @Test
    void readyExpeditionStartsButIncompleteFleetIsRejected() {
        AuthenticatedUser jarl = login("ragnar", "raven-2026");
        Long readyPreparation = 207L;
        Long incompletePreparation = 202L;

        reservations.reserve(jarl, readyPreparation, new ApiModels.ReserveRequest(0));
        expeditions.start(jarl, readyPreparation, new ApiModels.StartExpeditionRequest(1));

        assertThat(jdbc.queryForObject(
                "select status from expedition where id = ?", String.class, readyPreparation)).isEqualTo("SAILING");
        assertThat(jdbc.queryForObject(
                "select version from expedition where id = ?", Integer.class, readyPreparation)).isEqualTo(2);
        assertThatThrownBy(() -> expeditions.start(
                jarl, incompletePreparation, new ApiModels.StartExpeditionRequest(0)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("недостроенные корабли");
    }

    private AuthenticatedUser login(String username, String password) {
        ApiModels.LoginResponse login = auth.login(new ApiModels.LoginRequest(username, password));
        return auth.authenticate(login.token());
    }

    @Test
    void reservationDoesNotDeductStockAndExpiredReservationIsImmediatelyAvailable() {
        var jarl = login("ragnar", "raven-2026");
        Long id = reservations.reserve(jarl, 207L, new ApiModels.ReserveRequest(0));
        var reserved = queries.state(jarl).stock().stream().filter(s -> s.resource().equals("PROVISIONS")).findFirst().orElseThrow();
        assertThat(reserved.quantity()).isEqualTo(90);
        assertThat(reserved.reserved()).isEqualTo(20);
        assertThat(reserved.available()).isEqualTo(70);
        expireReservation(id);
        var released = queries.state(jarl).stock().stream().filter(s -> s.resource().equals("PROVISIONS")).findFirst().orElseThrow();
        assertThat(released.reserved()).isZero();
        assertThat(released.available()).isEqualTo(90);
        assertThatThrownBy(() -> expeditions.start(jarl, 207L, new ApiModels.StartExpeditionRequest(1)))
                .isInstanceOf(DomainException.class).hasMessageContaining("резерва");
        scheduler.releaseExpired();
        scheduler.releaseExpired();
        assertThat(jdbc.queryForObject("select status from resource_reservation where id = ?", String.class, id)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where event_type = 'RESERVATION_EXPIRED' and actor_role = 'SYSTEM' and actor_user_id is null", Integer.class)).isEqualTo(1);
    }

    @Test
    void launchConsumesOnceAndKeepsEntireFleetAndCrewSnapshot() {
        var jarl = login("ragnar", "raven-2026");
        shipyard.assignReadyShip(jarl, 207L, 402L);
        Long reservation = reservations.reserve(jarl, 207L, new ApiModels.ReserveRequest(1));
        expeditions.start(jarl, 207L, new ApiModels.StartExpeditionRequest(2));
        assertThat(jdbc.queryForObject("select quantity from warehouse_stock where settlement_id = 1 and resource = 'PROVISIONS'", Integer.class)).isEqualTo(70);
        assertThat(jdbc.queryForObject("select status from resource_reservation where id = ?", String.class, reservation)).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("select jsonb_array_length(departure_snapshot->'fleet') from expedition where id = 207", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select jsonb_array_length(departure_snapshot->'crew') from expedition where id = 207", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select started_at is not null from expedition where id = 207", Boolean.class)).isTrue();
        assertThatThrownBy(() -> expeditions.start(jarl, 207L, new ApiModels.StartExpeditionRequest(2))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> jdbc.update("update expedition set departure_snapshot = '{}' where id = 207")).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select actor_user_id from audit_event where aggregate_id = 207 and event_type = 'EXPEDITION_STARTED'", Long.class)).isEqualTo(jarl.id());
        assertThat(jdbc.queryForObject("select quantity from warehouse_stock where settlement_id = 1 and resource = 'PROVISIONS'", Integer.class)).isEqualTo(70);
    }

    @Test
    void eachRenewalAddsOneFullMinuteWithoutIncreasingReservedStock() {
        var jarl = login("ragnar", "raven-2026");
        Long first = reservations.reserve(jarl, 207L, new ApiModels.ReserveRequest(0));
        var firstExpiry = reservationExpiry(first);
        Long second = reservations.reserve(jarl, 207L, new ApiModels.ReserveRequest(1));
        assertThat(reservationExpiry(second)).isEqualTo(firstExpiry.plusSeconds(60));
        Long third = reservations.reserve(jarl, 207L, new ApiModels.ReserveRequest(2));
        assertThat(reservationExpiry(third)).isEqualTo(firstExpiry.plusSeconds(120));

        assertThatThrownBy(() -> reservations.reserve(jarl, 207L, new ApiModels.ReserveRequest(2)))
                .isInstanceOf(DomainException.class).hasMessageContaining("устарели");
        assertThat(reservationExpiry(third)).isEqualTo(firstExpiry.plusSeconds(120));
        assertThat(jdbc.queryForObject("select count(*) from resource_reservation where expedition_id = 207 and status = 'ACTIVE'", Integer.class)).isEqualTo(1);
        var stock = queries.state(jarl).stock().stream().filter(s -> s.resource().equals("PROVISIONS")).findFirst().orElseThrow();
        assertThat(stock.quantity()).isEqualTo(90);
        assertThat(stock.reserved()).isEqualTo(20);
        assertThat(stock.available()).isEqualTo(70);
    }

    @Test
    void expiredReservationStartsANewMinuteFromCurrentTime() {
        var jarl = login("ragnar", "raven-2026");
        Long first = reservations.reserve(jarl, 207L, new ApiModels.ReserveRequest(0));
        expireReservation(first);
        Long renewed = reservations.reserve(jarl, 207L, new ApiModels.ReserveRequest(1));
        var createdAt = jdbc.queryForObject("select created_at from resource_reservation where id = ?", java.sql.Timestamp.class, renewed).toInstant();
        assertThat(reservationExpiry(renewed)).isEqualTo(createdAt.plusSeconds(60));
        assertThat(jdbc.queryForObject("select status from resource_reservation where id = ?", String.class, first)).isEqualTo("RELEASED");
    }

    private java.time.Instant reservationExpiry(Long id) {
        return jdbc.queryForObject("select expires_at from resource_reservation where id = ?", java.sql.Timestamp.class, id).toInstant();
    }

    @Test
    void builderCannotSpendResourcesReservedForExpeditionAndFailureRollsBackEverything() {
        var jarl = login("ragnar", "raven-2026");
        reservations.savePlan(jarl, 207L, new ApiModels.PreparationRequest(
                List.of(new ApiModels.RoutePoint("Каттегат", 0), new ApiModels.RoutePoint("Брессей", 650)),
                List.of(new ApiModels.SupplyRequest("WOOD", 100)), 0));
        reservations.reserve(jarl, 207L, new ApiModels.ReserveRequest(1));
        assertThatThrownBy(() -> shipyard.completeStage(login("floki", "oak-2026"), SHIP, new ApiModels.CompleteStageRequest(0)))
                .isInstanceOf(DomainException.class).hasMessageContaining("Недостаточно");
        assertThat(jdbc.queryForObject("select quantity from warehouse_stock where settlement_id = 1 and resource = 'WOOD'", Integer.class)).isEqualTo(120);
        assertThat(jdbc.queryForObject("select stage from ship where id = 401", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> reservations.reserve(jarl, 202L, new ApiModels.ReserveRequest(99))).isInstanceOf(DomainException.class);
    }

    @Test
    void finalizedLossesAndAllocationsCannotBeChangedOrDeleted() {
        var jarl = login("ragnar", "raven-2026");
        expeditions.finalizeExpedition(jarl, 201L, new ApiModels.FinalizeRequest(new ApiModels.LootRequest(10, 10, 0), List.of(312L), 0));
        assertThatThrownBy(() -> jdbc.update("update crew_assignment set alive = true where id = 312")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from crew_assignment where id = 312")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update wergild_allocation set gold = 999 where expedition_id = 201")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from wergild_allocation where expedition_id = 201")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void warriorCannotAnswerAfterDepartureOrAnswerAnotherPersonsInvitation() {
        var warrior = login("halvdan", "shield-2026");
        jdbc.update("update expedition set status = 'SAILING' where id = 202");
        assertThatThrownBy(() -> crew.decide(warrior, 301L, new ApiModels.CrewDecisionRequest("CONFIRMED", 0)))
                .isInstanceOf(DomainException.class).hasMessageContaining("подготовки");
        assertThatThrownBy(() -> crew.decide(warrior, 325L, new ApiModels.CrewDecisionRequest("CONFIRMED", 0)))
                .isInstanceOf(DomainException.class);
        assertThat(jdbc.queryForObject("select participation_status from crew_assignment where id = 301", String.class)).isEqualTo("PENDING");
    }

    @Test
    void sseSendsOnlyAfterCommitAndNeverToAnotherSettlementOrUnrelatedWarrior() throws Exception {
        String jarl = auth.login(new ApiModels.LoginRequest("ragnar", "raven-2026")).token();
        String other = auth.login(new ApiModels.LoginRequest("erik", "birka-2026")).token();
        String warrior = auth.login(new ApiModels.LoginRequest("halvdan", "shield-2026")).token();
        var first = mockMvc.perform(get("/api/events").header("Authorization", "Bearer " + jarl)).andExpect(status().isOk()).andReturn();
        var second = mockMvc.perform(get("/api/events").header("Authorization", "Bearer " + other)).andReturn();
        var third = mockMvc.perform(get("/api/events").header("Authorization", "Bearer " + warrior)).andReturn();
        events.dispatch();
        assertThat(first.getResponse().getContentAsString()).contains("event:connected");
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            reservations.reserve(auth.authenticate(jarl), 207L, new ApiModels.ReserveRequest(0));
            events.dispatch();
            assertThat(new String(first.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("event:refresh");
            tx.setRollbackOnly();
        });
        events.dispatch();
        assertThat(first.getResponse().getContentAsString()).doesNotContain("event:refresh");
        reservations.reserve(auth.authenticate(jarl), 207L, new ApiModels.ReserveRequest(0));
        events.dispatch();
        assertThat(first.getResponse().getContentAsString()).contains("event:refresh");
        assertThat(second.getResponse().getContentAsString()).doesNotContain("event:refresh");
        assertThat(third.getResponse().getContentAsString()).doesNotContain("event:refresh");
        auth.logout(jarl);
        auth.logout(other);
        auth.logout(warrior);
        events.dispatch();
        mockMvc.perform(get("/api/events").header("Authorization", "Bearer " + jarl)).andExpect(status().isUnauthorized());
    }

    private void expireReservation(Long id) {
        jdbc.update("update resource_reservation set created_at = clock_timestamp() - interval '2 minutes', expires_at = clock_timestamp() - interval '1 minute' where id = ?", id);
    }

    @Test
    void concurrentReservationsCannotOverbookStock() throws Exception {
        var jarl = login("ragnar", "raven-2026");
        jdbc.update("update warehouse_stock set quantity = 30 where settlement_id = 1 and resource = 'PROVISIONS'");
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var results = List.of(202L, 207L).stream().map(id -> executor.submit(() -> {
                start.await();
                try { reservations.reserve(jarl, id, new ApiModels.ReserveRequest(0)); return "OK"; }
                catch (DomainException ex) { return ex.code(); }
            })).toList();
            start.countDown();
            assertThat(List.of(results.get(0).get(5, java.util.concurrent.TimeUnit.SECONDS), results.get(1).get(5, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "INSUFFICIENT_STOCK");
            assertThat(jdbc.queryForObject("select count(*) from resource_reservation where status = 'ACTIVE' and settlement_id = 1", Integer.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void departureChecksRealTimeAfterWaitingForLock() throws Exception {
        var jarl = login("ragnar", "raven-2026");
        Long id = reservations.reserve(jarl, 207L, new ApiModels.ReserveRequest(0));
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var result = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<String>>();
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from settlement where id = 1 for update", Long.class);
                result.set(executor.submit(() -> {
                    try { expeditions.start(jarl, 207L, new ApiModels.StartExpeditionRequest(1)); return "OK"; }
                    catch (DomainException ex) { return ex.code(); }
                }));
                // Wait until the competing transaction has reached the lock, then expire its reservation.
                long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
                while (!jdbc.queryForObject("select exists(select 1 from pg_stat_activity where wait_event_type = 'Lock' and query like 'select id from settlement%')", Boolean.class)) {
                    if (System.nanoTime() > deadline) throw new AssertionError("Start did not reach settlement lock");
                    jdbc.execute("select pg_sleep(0.02)");
                }
                jdbc.update("update resource_reservation set created_at = clock_timestamp() - interval '2 minutes', expires_at = clock_timestamp() + interval '100 milliseconds' where id = ?", id);
                jdbc.execute("select pg_sleep(0.15)");
            });
            assertThat(result.get().get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("RESERVATION_REQUIRED");
            assertThat(jdbc.queryForObject("select status from expedition where id = 207", String.class)).isEqualTo("PREPARATION");
            assertThat(jdbc.queryForObject("select quantity from warehouse_stock where settlement_id = 1 and resource = 'PROVISIONS'", Integer.class)).isEqualTo(90);
        } finally { executor.shutdownNow(); }
    }
}
