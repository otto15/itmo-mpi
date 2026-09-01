package com.drakkar.erp.domain;

import java.util.ArrayList;
import java.util.List;

public final class WergildCalculator {
    public static final int JARL_SHARE_PERCENT = 20;

    public record Claimant(String name, boolean alive) {
    }

    public record Allocation(String recipient, String category, Loot loot) {
    }

    public List<Allocation> calculate(Loot total, List<Claimant> claimants) {
        Loot jarlShare = total.percent(JARL_SHARE_PERCENT);
        Loot commonPool = total.minus(jarlShare);
        List<Allocation> result = new ArrayList<>();
        result.add(new Allocation("Ярл", "JARL", jarlShare));

        if (claimants.isEmpty()) {
            result.add(new Allocation("Склад поселения", "SETTLEMENT", commonPool));
            return result;
        }

        Loot equalShare = commonPool.dividedBy(claimants.size());
        for (Claimant claimant : claimants) {
            String recipient = claimant.alive() ? claimant.name() : "Семья: " + claimant.name();
            String category = claimant.alive() ? "WARRIOR" : "FAMILY";
            result.add(new Allocation(recipient, category, equalShare));
        }

        Loot allocated = equalShare.times(claimants.size());
        Loot remainder = commonPool.minus(allocated);
        if (remainder.gold() + remainder.provisions() + remainder.thralls() > 0) {
            result.add(new Allocation("Склад поселения", "SETTLEMENT", remainder));
        }
        return result;
    }
}
