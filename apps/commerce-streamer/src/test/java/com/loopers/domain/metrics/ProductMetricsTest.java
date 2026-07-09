package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductMetrics 는 이벤트 투영 read model 이라 Spring 없이 순수 POJO 로 카운터 증감 규칙만 검증한다.
 * (동시성·@DynamicUpdate 의 컬럼단위 쓰기는 영속성 관심사라 통합 테스트에서 확인한다.)
 */
class ProductMetricsTest {

    @Nested
    @DisplayName("좋아요 카운터")
    class Like {
        @Test
        @DisplayName("increaseLike 는 1 증가시킨다.")
        void increaseLike() {
            ProductMetrics metrics = ProductMetrics.of(1L);
            metrics.increaseLike();
            assertThat(metrics.getLikeCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("decreaseLike 는 0 미만으로 내려가지 않는다.")
        void decreaseLikeFloorsAtZero() {
            ProductMetrics metrics = ProductMetrics.of(1L);
            metrics.decreaseLike();
            assertThat(metrics.getLikeCount()).isZero();
        }
    }

    @Nested
    @DisplayName("판매량 카운터")
    class Sales {
        @Test
        @DisplayName("increaseSales 는 주문 수량만큼 누적한다.")
        void increaseSalesByQuantity() {
            ProductMetrics metrics = ProductMetrics.of(1L);
            metrics.increaseSales(3);
            metrics.increaseSales(2);
            assertThat(metrics.getSalesCount()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("조회수 카운터")
    class View {
        @Test
        @DisplayName("increaseView 는 1 증가시킨다.")
        void increaseView() {
            ProductMetrics metrics = ProductMetrics.of(1L);
            metrics.increaseView();
            metrics.increaseView();
            assertThat(metrics.getViewCount()).isEqualTo(2L);
        }
    }

    @Test
    @DisplayName("한 카운터의 증감은 다른 카운터에 영향을 주지 않는다(컬럼 독립).")
    void countersAreIndependent() {
        ProductMetrics metrics = ProductMetrics.of(1L);
        metrics.increaseLike();
        metrics.increaseSales(4);
        metrics.increaseView();
        assertThat(metrics.getLikeCount()).isEqualTo(1L);
        assertThat(metrics.getSalesCount()).isEqualTo(4L);
        assertThat(metrics.getViewCount()).isEqualTo(1L);
    }
}
