package com.drakkar.erp.application;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.domain.DomainException;
import com.drakkar.erp.domain.Role;
import com.drakkar.erp.infrastructure.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

@Service
public class SettlementService {
    private static final List<String> STOCK_RESOURCES = List.of(
            "WOOD", "CLOTH", "RESIN", "GOLD", "PROVISIONS", "THRALLS"
    );

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final PasswordHasher passwordHasher;
    private final String provisioningKey;

    public SettlementService(
            JdbcTemplate jdbc,
            AuditWriter audit,
            PasswordHasher passwordHasher,
            @Value("${drakkar.provisioning-key}") String provisioningKey
    ) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.passwordHasher = passwordHasher;
        this.provisioningKey = provisioningKey;
    }

    @Transactional
    public ApiModels.ProvisionSettlementResponse provision(
            String suppliedKey,
            ApiModels.ProvisionSettlementRequest request
    ) {
        requireProvisioningKey(suppliedKey);
        UUID settlementId = UUID.randomUUID();
        UUID jarlId = UUID.randomUUID();
        String settlementName = request.settlementName().trim();
        String username = request.username().trim().toLowerCase(Locale.ROOT);

        Integer duplicate = jdbc.query("""
                select 1 from user_account where lower(username) = lower(?)
                """, rs -> rs.next() ? 1 : null, username);
        if (duplicate != null) {
            throw DomainException.conflict("USERNAME_ALREADY_EXISTS", "Логин уже используется");
        }

        PasswordHasher.EncodedPassword password = passwordHasher.encode(request.password().toCharArray());

        jdbc.update("insert into settlement(id, name) values (?, ?)", settlementId, settlementName);
        jdbc.update("insert into app_user(id, display_name) values (?, ?)", jarlId, request.jarlDisplayName().trim());
        jdbc.update("""
                insert into user_account(user_id, username, password_salt, password_hash)
                values (?, ?, ?, ?)
                """, jarlId, username, password.salt(), password.hash());
        jdbc.update("""
                insert into settlement_membership(settlement_id, user_id, member_role)
                values (?, ?, 'JARL')
                """, settlementId, jarlId);
        for (String resource : STOCK_RESOURCES) {
            jdbc.update("""
                    insert into warehouse_stock(settlement_id, resource, quantity)
                    values (?, ?, 0)
                    """, settlementId, resource);
        }
        audit.append(settlementId, Role.JARL, "SETTLEMENT_CREATED", "SETTLEMENT", settlementId,
                "{\"name\":\"" + escapeJson(settlementName) + "\"}");
        return new ApiModels.ProvisionSettlementResponse(settlementId, settlementName, username);
    }

    private void requireProvisioningKey(String suppliedKey) {
        byte[] expected = provisioningKey.getBytes(StandardCharsets.UTF_8);
        byte[] actual = suppliedKey == null
                ? new byte[0]
                : suppliedKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new DomainException(
                    "PROVISIONING_FORBIDDEN",
                    "Операция подключения поселения недоступна",
                    HttpStatus.FORBIDDEN);
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
