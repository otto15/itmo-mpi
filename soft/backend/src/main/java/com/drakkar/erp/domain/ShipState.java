package com.drakkar.erp.domain;

public record ShipState(int stage, boolean blessed, int version) {
    public boolean isCompleted() {
        return stage == 4;
    }

    public boolean needsBlessing() {
        return stage == 3 && !blessed;
    }
}
