package com.drakkar.erp.domain;

public record ExpeditionState(String status, int version, boolean immutable) {
    public boolean isInPreparation() {
        return "PREPARATION".equals(status);
    }

    public boolean isSailing() {
        return "SAILING".equals(status);
    }
}
