package com.drakkar.erp.dao;

import com.drakkar.erp.domain.AuthenticatedUser;
import com.drakkar.erp.domain.Role;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Repository
public class AuthDao {
    public record Account(
            Long id,
            String displayName,
            Role role,
            Long settlementId,
            byte[] salt,
            byte[] passwordHash
    ) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public AuthDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Account findEnabledAccount(String username) {
        return jdbc.query("""
                select u.id, u.display_name, sm.member_role, sm.settlement_id,
                       a.password_salt, a.password_hash
                  from user_account a
                  join app_user u on u.id = a.user_id
                  join settlement_membership sm on sm.user_id = u.id
                 where lower(a.username) = lower(:username) and a.enabled = true
                """, Map.of("username", username), rs -> rs.next() ? new Account(
                rs.getLong("id"),
                rs.getString("display_name"),
                Role.valueOf(rs.getString("member_role")),
                rs.getLong("settlement_id"),
                rs.getBytes("password_salt"),
                rs.getBytes("password_hash")) : null);
    }

    public void createSession(String tokenHash, Long userId, Long settlementId, Instant expiresAt) {
        jdbc.update("""
                insert into user_session(token_hash, user_id, active_settlement_id, expires_at)
                values (:tokenHash, :userId, :settlementId, :expiresAt)
                """, Map.of(
                "tokenHash", tokenHash,
                "userId", userId,
                "settlementId", settlementId,
                "expiresAt", Timestamp.from(expiresAt)));
    }

    public AuthenticatedUser findActiveSessionUser(String tokenHash) {
        return jdbc.query("""
                select u.id, u.display_name, sm.member_role,
                       st.id as settlement_id, st.name as settlement_name
                  from user_session s
                  join app_user u on u.id = s.user_id
                  join user_account a on a.user_id = u.id
                  join settlement st on st.id = s.active_settlement_id
                  join settlement_membership sm
                    on sm.user_id = u.id and sm.settlement_id = st.id
                 where s.token_hash = :tokenHash
                   and s.revoked_at is null
                   and s.expires_at > now()
                   and a.enabled = true
                """, Map.of("tokenHash", tokenHash), rs -> rs.next() ? new AuthenticatedUser(
                rs.getLong("id"),
                rs.getString("display_name"),
                Role.valueOf(rs.getString("member_role")),
                rs.getLong("settlement_id"),
                rs.getString("settlement_name")) : null);
    }

    public void revokeSession(String tokenHash) {
        jdbc.update("""
                update user_session set revoked_at = now()
                 where token_hash = :tokenHash and revoked_at is null
                """, Map.of("tokenHash", tokenHash));
    }
}
