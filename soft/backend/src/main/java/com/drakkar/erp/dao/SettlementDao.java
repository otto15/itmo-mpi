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
                select 1 from app_user where lower(username) = lower(:username)
                """, Map.of("username", username), rs -> rs.next() ? 1 : null);
        return found != null;
    }

    public Long createSettlement(String name) {
        return jdbc.queryForObject(
                "insert into settlement(name) values (:name) returning id",
                Map.of("name", name),
                Long.class);
    }

    public Long createUser(String displayName, String username, byte[] salt, byte[] hash) {
        return jdbc.queryForObject("""
                insert into app_user(display_name, username, password_salt, password_hash)
                values (:displayName, :username, :salt, :hash)
                returning id
                """, Map.of(
                "displayName", displayName,
                "username", username,
                "salt", salt,
                "hash", hash), Long.class);
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
