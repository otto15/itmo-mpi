package com.drakkar.erp.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class SettlementDao {
    private final NamedParameterJdbcTemplate jdbc;

    public SettlementDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean usernameExists(String username) {
        Integer found = jdbc.query("""
                select 1 from user_account where lower(username) = lower(:username)
                """, Map.of("username", username), rs -> rs.next() ? 1 : null);
        return found != null;
    }

    public Long createSettlement(String name) {
        return jdbc.queryForObject(
                "insert into settlement(name) values (:name) returning id",
                Map.of("name", name),
                Long.class);
    }

    public Long createUser(String displayName) {
        return jdbc.queryForObject(
                "insert into app_user(display_name) values (:displayName) returning id",
                Map.of("displayName", displayName),
                Long.class);
    }

    public void createAccount(Long userId, String username, byte[] salt, byte[] hash) {
        jdbc.update("""
                insert into user_account(user_id, username, password_salt, password_hash)
                values (:userId, :username, :salt, :hash)
                """, Map.of(
                "userId", userId,
                "username", username,
                "salt", salt,
                "hash", hash));
    }

    public void addJarlMembership(Long settlementId, Long userId) {
        jdbc.update("""
                insert into settlement_membership(settlement_id, user_id, member_role)
                values (:settlementId, :userId, 'JARL')
                """, Map.of(
                "settlementId", settlementId,
                "userId", userId));
    }

    public void createEmptyStock(Long settlementId, String resource) {
        jdbc.update("""
                insert into warehouse_stock(settlement_id, resource, quantity)
                values (:settlementId, :resource, 0)
                """, Map.of(
                "settlementId", settlementId,
                "resource", resource));
    }
}
