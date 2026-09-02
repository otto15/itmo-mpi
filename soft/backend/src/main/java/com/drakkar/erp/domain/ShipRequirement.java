package com.drakkar.erp.domain;

public record ShipRequirement(String resource, int required, int available) {
    public boolean isSatisfied() {
        return available >= required;
    }
}
