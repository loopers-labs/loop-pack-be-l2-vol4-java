package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsTest {

    private final ZonedDateTime t0 = ZonedDateTime.parse("2026-07-03T10:00:00+09:00");

    @DisplayName("좋아요 delta")
    @Nested
    class Like {
        @DisplayName("newer 이벤트는 delta 를 반영한다.")
        @Test
        void appliesNewerDelta() {
            ProductMetrics m = new ProductMetrics(1L, t0);

            m.applyLikeDelta(1, t0.plusSeconds(1));

            assertThat(m.getLikeCount()).isEqualTo(1);
            assertThat(m.getLastEventAt()).isEqualTo(t0.plusSeconds(1));
        }

        @DisplayName("older 이벤트는 무시된다.")
        @Test
        void ignoresOlder() {
            ProductMetrics m = new ProductMetrics(1L, t0.plusHours(1));

            m.applyLikeDelta(1, t0);

            assertThat(m.getLikeCount()).isZero();
        }

        @DisplayName("음수 delta 라도 0 미만으로 떨어지지 않는다.")
        @Test
        void notNegative() {
            ProductMetrics m = new ProductMetrics(1L, t0);

            m.applyLikeDelta(-1, t0.plusSeconds(1));

            assertThat(m.getLikeCount()).isZero();
        }
    }

    @DisplayName("조회수 / 판매량")
    @Nested
    class ViewsAndSales {
        @DisplayName("incrementView 는 카운트를 1 증가시킨다.")
        @Test
        void view() {
            ProductMetrics m = new ProductMetrics(1L, t0);

            m.incrementView(t0.plusSeconds(1));
            m.incrementView(t0.plusSeconds(2));

            assertThat(m.getViewCount()).isEqualTo(2);
        }

        @DisplayName("addSales 는 수량만큼 판매량을 증가시킨다.")
        @Test
        void sales() {
            ProductMetrics m = new ProductMetrics(1L, t0);

            m.addSales(3, t0.plusSeconds(1));
            m.addSales(2, t0.plusSeconds(2));

            assertThat(m.getSalesCount()).isEqualTo(5);
        }

        @DisplayName("수량 0 이하는 무시된다.")
        @Test
        void ignoresZeroSales() {
            ProductMetrics m = new ProductMetrics(1L, t0);

            m.addSales(0, t0.plusSeconds(1));

            assertThat(m.getSalesCount()).isZero();
        }
    }
}
