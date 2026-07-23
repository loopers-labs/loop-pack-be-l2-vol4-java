package com.loopers.tddstudy.infrastructure.metrics;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsDailyTest {

    @Test
    void 생성자는_전달받은_값을_담고_생성시각을_자동으로_채운다() {
        // given & when
        ProductMetricsDaily daily = new ProductMetricsDaily(
                1L, LocalDate.of(2026, 7, 21), 5, 3, 10);

        // then
        assertThat(daily.getProductId()).isEqualTo(1L);
        assertThat(daily.getMetricDate()).isEqualTo(LocalDate.of(2026, 7, 21));
        assertThat(daily.getLikeCount()).isEqualTo(5);
        assertThat(daily.getSalesCount()).isEqualTo(3);
        assertThat(daily.getViewCount()).isEqualTo(10);
        assertThat(daily.getCreatedAt()).isNotNull();   // now()가 채워졌는지
    }

    @Test
    void 좋아요_델타는_음수도_담을_수_있다() {
        // 좋아요 취소로 델타가 음수인 상황
        ProductMetricsDaily daily = new ProductMetricsDaily(
                1L, LocalDate.of(2026, 7, 21), -2, 0, 0);

        assertThat(daily.getLikeCount()).isEqualTo(-2);
    }
}
