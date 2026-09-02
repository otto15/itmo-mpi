package com.drakkar.erp.application;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.DomainException;
import com.drakkar.erp.domain.Role;
import com.drakkar.erp.infrastructure.PasswordHasher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private record Account(
            Long id,
            String displayName,
            Role role,
            Long settlementId,
            byte[] salt,
            byte[] passwordHash
    ) {
    }

    private final JdbcTemplate jdbc;
    private final PasswordHasher passwordHasher;
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcTemplate jdbc, PasswordHasher passwordHasher) {
        this.jdbc = jdbc;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public ApiModels.LoginResponse login(ApiModels.LoginRequest request) {
        Account account = jdbc.query("""
                select u.id, u.display_name, sm.member_role, sm.settlement_id,
                       a.password_salt, a.password_hash
                  from user_account a
                  join app_user u on u.id = a.user_id
                  join settlement_membership sm on sm.user_id = u.id
                  join settlement st on st.id = sm.settlement_id
                 where lower(a.username) = lower(?) and a.enabled = true
                """, rs -> rs.next() ? new Account(
                rs.getLong("id"),
                rs.getString("display_name"),
                Role.valueOf(rs.getString("member_role")),
                rs.getLong("settlement_id"),
                rs.getBytes("password_salt"),
                rs.getBytes("password_hash")) : null, request.username().trim());

        if (account == null || !passwordHasher.matches(request.password().toCharArray(), account.salt(), account.passwordHash())) {
            throw new DomainException("INVALID_CREDENTIALS", "Неверный логин или пароль", HttpStatus.UNAUTHORIZED);
        }

        byte[] rawToken = new byte[32];
        random.nextBytes(rawToken);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        jdbc.update("""
                insert into user_session(token_hash, user_id, active_settlement_id, expires_at)
                values (?, ?, ?, ?)
                """, tokenHash(token), account.id(), account.settlementId(), java.sql.Timestamp.from(expiresAt));
        return new ApiModels.LoginResponse(token, expiresAt, account.id(), account.displayName(), account.role().name());
    }

    public AuthenticatedUser authenticate(String token) {
        AuthenticatedUser user = jdbc.query("""
                select u.id, u.display_name, sm.member_role, st.id as settlement_id, st.name as settlement_name
                  from user_session s
                  join app_user u on u.id = s.user_id
                  join user_account a on a.user_id = u.id
                  join settlement st on st.id = s.active_settlement_id
                  join settlement_membership sm
                    on sm.user_id = u.id and sm.settlement_id = st.id
                 where s.token_hash = ?
                   and s.revoked_at is null
                   and s.expires_at > now()
                   and a.enabled = true
                """, rs -> rs.next() ? new AuthenticatedUser(
                rs.getLong("id"),
                rs.getString("display_name"),
                Role.valueOf(rs.getString("member_role")),
                rs.getLong("settlement_id"),
                rs.getString("settlement_name")) : null, tokenHash(token));
        if (user == null) {
            throw new DomainException("SESSION_INVALID", "Сессия отсутствует или истекла", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    @Transactional
    public void logout(String token) {
        jdbc.update("update user_session set revoked_at = now() where token_hash = ? and revoked_at is null", tokenHash(token));
    }

    private String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }
}
