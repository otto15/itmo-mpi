package com.drakkar.erp.domain;

import java.util.Arrays;

public final class RoleGuard {
    private RoleGuard() {
    }

    public static Role parse(String value) {
        try {
            return Role.valueOf(value.toUpperCase());
        } catch (RuntimeException ex) {
            throw DomainException.forbidden("Неизвестная роль: " + value);
        }
    }

    public static void require(Role actual, Role... allowed) {
        if (Arrays.stream(allowed).noneMatch(actual::equals)) {
            throw DomainException.forbidden("Операция недоступна для роли " + actual);
        }
    }
}
