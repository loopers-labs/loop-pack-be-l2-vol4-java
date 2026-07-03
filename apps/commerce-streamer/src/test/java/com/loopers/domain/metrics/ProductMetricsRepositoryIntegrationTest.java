package com.loopers.domain.metrics;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class ProductMetricsRepositoryIntegrationTest {

    @Autowired
    private ProductMetricsRepository productMetricsRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 수를 증가시킬 때,")
    @Nested
    class IncreaseLikeCount {

        @DisplayName("존재하지 않는 productId면 행이 생성되고 like_count가 1이 된다.")
        @Test
        void createsRow_whenProductIdDoesNotExist() {
            // given
            Long productId = 1L;

            // when
            productMetricsRepository.increaseLikeCount(productId);

            // then
            Optional<ProductMetricsModel> result = productMetricsRepository.findByProductId(productId);
            assertAll(
                    () -> assertThat(result).isPresent(),
                    () -> assertThat(result.get().getLikeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("이미 존재하는 productId면 like_count가 누적된다.")
        @Test
        void accumulates_whenProductIdAlreadyExists() {
            // given
            Long productId = 2L;
            productMetricsRepository.increaseLikeCount(productId);

            // when
            productMetricsRepository.increaseLikeCount(productId);

            // then
            Long likeCount = productMetricsRepository.findByProductId(productId).orElseThrow().getLikeCount();
            assertThat(likeCount).isEqualTo(2L);
        }
    }

    @DisplayName("좋아요 수를 감소시킬 때,")
    @Nested
    class DecreaseLikeCount {

        @DisplayName("0 밑으로 내려가지 않는다.")
        @Test
        void doesNotGoBelowZero_whenLikeCountIsAlreadyZero() {
            // given
            Long productId = 3L;

            // when
            productMetricsRepository.decreaseLikeCount(productId);

            // then
            Long likeCount = productMetricsRepository.findByProductId(productId).orElseThrow().getLikeCount();
            assertThat(likeCount).isEqualTo(0L);
        }

        @DisplayName("1 이상이면 1 감소한다.")
        @Test
        void decreases_whenLikeCountIsPositive() {
            // given
            Long productId = 4L;
            productMetricsRepository.increaseLikeCount(productId);
            productMetricsRepository.increaseLikeCount(productId);

            // when
            productMetricsRepository.decreaseLikeCount(productId);

            // then
            Long likeCount = productMetricsRepository.findByProductId(productId).orElseThrow().getLikeCount();
            assertThat(likeCount).isEqualTo(1L);
        }
    }

    @DisplayName("판매량을 증가시킬 때,")
    @Nested
    class IncreaseSalesCount {

        @DisplayName("주문 수량만큼 sales_count가 누적된다.")
        @Test
        void accumulatesByQuantity_whenSalesIncreased() {
            // given
            Long productId = 5L;
            productMetricsRepository.increaseSalesCount(productId, 3L);

            // when
            productMetricsRepository.increaseSalesCount(productId, 2L);

            // then
            Long salesCount = productMetricsRepository.findByProductId(productId).orElseThrow().getSalesCount();
            assertThat(salesCount).isEqualTo(5L);
        }
    }

    @DisplayName("조회수를 증가시킬 때,")
    @Nested
    class IncreaseViewCount {

        @DisplayName("view_count가 1 누적된다.")
        @Test
        void accumulates_whenViewIncreased() {
            // given
            Long productId = 6L;
            productMetricsRepository.increaseViewCount(productId);

            // when
            productMetricsRepository.increaseViewCount(productId);

            // then
            Long viewCount = productMetricsRepository.findByProductId(productId).orElseThrow().getViewCount();
            assertThat(viewCount).isEqualTo(2L);
        }
    }
}
