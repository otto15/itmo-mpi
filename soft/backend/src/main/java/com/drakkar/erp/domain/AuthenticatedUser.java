package com.drakkar.erp.domain;

public record AuthenticatedUser(
        Long id,
        String displayName,
        Role role,
        Long settlementId,
        String settlementName
) {
}
