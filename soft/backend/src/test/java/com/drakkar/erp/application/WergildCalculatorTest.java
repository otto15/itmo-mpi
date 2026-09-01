package com.drakkar.erp.application;

import com.drakkar.erp.domain.Loot;
import com.drakkar.erp.domain.WergildCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WergildCalculatorTest {
    private final WergildCalculator calculator = new WergildCalculator();

    @Test
    void splitsTwentyPercentToJarlAndEqualClaimsToWarriorsAndFamilies() {
        List<WergildCalculator.Allocation> allocations = calculator.calculate(
                new Loot(100, 50, 10),
                List.of(
                        new WergildCalculator.Claimant("Бьёрн", true),
                        new WergildCalculator.Claimant("Ивар", false)
                ));

        assertThat(allocations).containsExactly(
                new WergildCalculator.Allocation("Ярл", "JARL", new Loot(20, 10, 2)),
                new WergildCalculator.Allocation("Бьёрн", "WARRIOR", new Loot(40, 20, 4)),
                new WergildCalculator.Allocation("Семья: Ивар", "FAMILY", new Loot(40, 20, 4))
        );
    }

    @Test
    void keepsIntegerRemainderInSettlement() {
        List<WergildCalculator.Allocation> allocations = calculator.calculate(
                new Loot(11, 1, 0),
                List.of(
                        new WergildCalculator.Claimant("А", true),
                        new WergildCalculator.Claimant("Б", true),
                        new WergildCalculator.Claimant("В", false)
                ));

        assertThat(allocations).last().isEqualTo(
                new WergildCalculator.Allocation("Склад поселения", "SETTLEMENT", new Loot(0, 1, 0)));
    }

    @Test
    void routesCommonPoolToSettlementWhenThereAreNoClaimants() {
        List<WergildCalculator.Allocation> allocations = calculator.calculate(new Loot(100, 0, 0), List.of());

        assertThat(allocations).containsExactly(
                new WergildCalculator.Allocation("Ярл", "JARL", new Loot(20, 0, 0)),
                new WergildCalculator.Allocation("Склад поселения", "SETTLEMENT", new Loot(80, 0, 0))
        );
    }
}
