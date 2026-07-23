package com.loopers.tddstudy.infrastructure.ranking;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductRankMvTest {

    @Test
    void 주간_랭킹_행은_전달받은_값을_담는다() {
        ProductRankWeekly weekly =
                new ProductRankWeekly("2026-W27", 1, 5L, 12.5, 10, 15, 100);

        assertThat(weekly.getPeriodKey()).isEqualTo("2026-W27");
        assertThat(weekly.getRankNo()).isEqualTo(1);
        assertThat(weekly.getProductId()).isEqualTo(5L);
        assertThat(weekly.getScore()).isEqualTo(12.5);
        assertThat(weekly.getLikeCount()).isEqualTo(10);
        assertThat(weekly.getCreatedAt()).isNotNull();
    }

    @Test
    void 월간_랭킹_행도_같은_구조로_동작한다() {
        ProductRankMonthly monthly =
                new ProductRankMonthly("2026-07", 3, 7L, 8.0, 5, 10, 50);

        assertThat(monthly.getPeriodKey()).isEqualTo("2026-07");
        assertThat(monthly.getRankNo()).isEqualTo(3);
        assertThat(monthly.getProductId()).isEqualTo(7L);
    }
}
