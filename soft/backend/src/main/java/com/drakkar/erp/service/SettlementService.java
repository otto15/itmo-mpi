package com.drakkar.erp.service;

import com.drakkar.erp.dto.ApiModels;
import com.drakkar.erp.dao.AuditDao;
import com.drakkar.erp.dao.SettlementDao;
import com.drakkar.erp.domain.DomainException;
import com.drakkar.erp.domain.Role;
import com.drakkar.erp.infrastructure.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

@Service
public class SettlementService {
    private static final List<String> STOCK_RESOURCES = List.of(
            "WOOD", "CLOTH", "RESIN", "GOLD", "PROVISIONS", "THRALLS"
    );

    private final SettlementDao dao;
    private final AuditDao audit;
    private final PasswordHasher passwordHasher;
    private final String provisioningKey;

    public SettlementService(
            SettlementDao dao,
            AuditDao audit,
            PasswordHasher passwordHasher,
            @Value("${drakkar.provisioning-key}") String provisioningKey
    ) {
        this.dao = dao;
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
        String settlementName = request.settlementName().trim();
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (dao.usernameExists(username)) {
            throw DomainException.conflict("USERNAME_ALREADY_EXISTS", "Логин уже используется");
        }

        PasswordHasher.EncodedPassword password = passwordHasher.encode(
                request.password().toCharArray());
        Long settlementId = dao.createSettlement(settlementName);
        Long jarlId = dao.createUser(request.jarlDisplayName().trim());
        dao.createAccount(jarlId, username, password.salt(), password.hash());
        dao.addJarlMembership(settlementId, jarlId);
        STOCK_RESOURCES.forEach(resource -> dao.createEmptyStock(settlementId, resource));

        audit.append(
                settlementId, Role.JARL, "SETTLEMENT_CREATED", "SETTLEMENT", settlementId,
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
