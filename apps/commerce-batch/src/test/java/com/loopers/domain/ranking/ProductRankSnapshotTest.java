package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRankSnapshotTest {

    @Test
    @DisplayName("랭킹 Snapshot은 batchRunId를 가진 inactive 상태로 생성된다.")
    void createInactive_ShouldCreateInactiveSnapshot() {
        ProductRankSnapshot snapshot = ProductRankSnapshot.createInactive(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            1L,
            10L,
            1,
            98.1
        );

        assertThat(snapshot.getPeriod()).isEqualTo(RankingPeriod.WEEKLY);
        assertThat(snapshot.getRankStartDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(snapshot.getRankEndDate()).isEqualTo(LocalDate.of(2026, 7, 26));
        assertThat(snapshot.getBatchRunId()).isEqualTo(1L);
        assertThat(snapshot.getProductId()).isEqualTo(10L);
        assertThat(snapshot.getRankNo()).isEqualTo(1);
        assertThat(snapshot.getScore()).isEqualTo(98.1);
        assertThat(snapshot.isActive()).isFalse();
    }

    @Test
    @DisplayName("랭킹 Snapshot은 활성화하거나 비활성화할 수 있다.")
    void activateAndDeactivate_ShouldChangeActiveStatus() {
        ProductRankSnapshot snapshot = ProductRankSnapshot.createInactive(
            RankingPeriod.MONTHLY,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            2L,
            20L,
            1,
            120.5
        );

        snapshot.activate();
        assertThat(snapshot.isActive()).isTrue();

        snapshot.deactivate();
        assertThat(snapshot.isActive()).isFalse();
    }
}
