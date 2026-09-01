package com.drakkar.erp.domain;

public record Loot(int gold, int provisions, int thralls) {
    public Loot {
        if (gold < 0 || provisions < 0 || thralls < 0) {
            throw new IllegalArgumentException("Добыча не может быть отрицательной");
        }
    }

    public Loot percent(int percentage) {
        return new Loot(gold * percentage / 100, provisions * percentage / 100, thralls * percentage / 100);
    }

    public Loot minus(Loot other) {
        return new Loot(gold - other.gold, provisions - other.provisions, thralls - other.thralls);
    }

    public Loot dividedBy(int divisor) {
        return new Loot(gold / divisor, provisions / divisor, thralls / divisor);
    }

    public Loot times(int multiplier) {
        return new Loot(gold * multiplier, provisions * multiplier, thralls * multiplier);
    }
}
