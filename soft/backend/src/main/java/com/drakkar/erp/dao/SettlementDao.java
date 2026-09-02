package com.drakkar.erp.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementDao {
    private final JdbcTemplate jdbc;

    public SettlementDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean usernameExists(String username) {
        Integer found = jdbc.query("""
                select 1 from user_account where lower(username) = lower(?)
                """, rs -> rs.next() ? 1 : null, username);
        return found != null;
    }

    public Long createSettlement(String name) {
        return jdbc.queryForObject(
                "insert into settlement(name) values (?) returning id",
                Long.class,
                name);
    }

    public Long createUser(String displayName) {
        return jdbc.queryForObject(
                "insert into app_user(display_name) values (?) returning id",
                Long.class,
                displayName);
    }

    public void createAccount(Long userId, String username, byte[] salt, byte[] hash) {
        jdbc.update("""
                insert into user_account(user_id, username, password_salt, password_hash)
                values (?, ?, ?, ?)
                """, userId, username, salt, hash);
    }

    public void addJarlMembership(Long settlementId, Long userId) {
        jdbc.update("""
                insert into settlement_membership(settlement_id, user_id, member_role)
                values (?, ?, 'JARL')
                """, settlementId, userId);
    }

    public void createEmptyStock(Long settlementId, String resource) {
        jdbc.update("""
                insert into warehouse_stock(settlement_id, resource, quantity)
                values (?, ?, 0)
                """, settlementId, resource);
    }
}
