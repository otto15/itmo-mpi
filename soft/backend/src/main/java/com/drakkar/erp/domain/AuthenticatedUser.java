package com.drakkar.erp.domain;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String displayName, Role role) {
}
