package com.drakkar.erp.service;

import com.drakkar.erp.dto.ApiModels;
import com.drakkar.erp.dao.AuthDao;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.DomainException;
import com.drakkar.erp.infrastructure.PasswordHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {
    public static final Duration SESSION_TTL = Duration.ofHours(8);

    private final AuthDao dao;
    private final PasswordHasher passwordHasher;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AuthDao dao, PasswordHasher passwordHasher) {
        this.dao = dao;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public ApiModels.LoginResponse login(ApiModels.LoginRequest request) {
        AuthDao.Account account = dao.findEnabledAccount(request.username().trim());
        if (account == null
                || !passwordHasher.matches(
                request.password().toCharArray(), account.salt(), account.passwordHash())) {
            throw new DomainException(
                    "INVALID_CREDENTIALS",
                    "Неверный логин или пароль",
                    HttpStatus.UNAUTHORIZED);
        }

        String token = newToken();
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        dao.createSession(tokenHash(token), account.id(), account.settlementId(), expiresAt);
        return new ApiModels.LoginResponse(
                token, expiresAt, account.id(), account.displayName(), account.role().name());
    }

    public AuthenticatedUser authenticate(String token) {
        AuthenticatedUser user = dao.findActiveSessionUser(tokenHash(token));
        if (user == null) {
            throw new DomainException(
                    "SESSION_INVALID",
                    "Сессия отсутствует или истекла",
                    HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    @Transactional
    public void logout(String token) {
        dao.revokeSession(tokenHash(token));
    }

    private String newToken() {
        byte[] rawToken = new byte[32];
        random.nextBytes(rawToken);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);
    }

    private String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }
}
