package com.drakkar.erp.domain;

public record CrewCounts(int confirmed, int pending) {
    public int invited() {
        return confirmed + pending;
    }

    public boolean hasConfirmedMembers() {
        return confirmed > 0;
    }

    public boolean hasPendingDecisions() {
        return pending > 0;
    }
}
