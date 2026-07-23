package com.loopers.domain.metrics;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

// UPSERT(@Modifying)는 ProductMetricsDailyService의 @Transactional에 합류해야 실행 가능하므로
// 쓰기는 Service를 통해 호출하고, 조회(findByProductIdAndMetricDate)만 Repository를 직접 사용한다.
@SpringBootTest
class ProductMetricsDailyServiceIntegrationTest {

    private static final LocalDate METRIC_DATE = LocalDate.of(2026, 7, 23);

    @Autowired
    private ProductMetricsDailyService productMetricsDailyService;

    @Autowired
    private ProductMetricsDailyRepository productMetricsDailyRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 수를 증가시킬 때,")
    @Nested
    class IncreaseLikeCount {

        @DisplayName("해당 날짜 행이 없으면 행이 생성되고 like_count가 1이 된다.")
        @Test
        void createsRow_whenDailyRowDoesNotExist() {
            // given
            Long productId = 1L;

            // when
            productMetricsDailyService.increaseLikeCount(productId, METRIC_DATE);

            // then
            Optional<ProductMetricsDailyModel> result =
                    productMetricsDailyRepository.findByProductIdAndMetricDate(productId, METRIC_DATE);
            assertAll(
                    () -> assertThat(result).isPresent(),
                    () -> assertThat(result.get().getMetricDate()).isEqualTo(METRIC_DATE),
                    () -> assertThat(result.get().getLikeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("같은 상품·날짜면 like_count가 누적된다.")
        @Test
        void accumulates_whenSameProductAndDate() {
            // given
            Long productId = 2L;
            productMetricsDailyService.increaseLikeCount(productId, METRIC_DATE);

            // when
            productMetricsDailyService.increaseLikeCount(productId, METRIC_DATE);

            // then
            Long likeCount = productMetricsDailyRepository.findByProductIdAndMetricDate(productId, METRIC_DATE)
                    .orElseThrow().getLikeCount();
            assertThat(likeCount).isEqualTo(2L);
        }

        @DisplayName("같은 상품이라도 날짜가 다르면 서로 다른 행으로 집계된다.")
        @Test
        void separatesByDate_whenSameProductDifferentDate() {
            // given
            Long productId = 3L;
            LocalDate otherDate = METRIC_DATE.plusDays(1);
            productMetricsDailyService.increaseLikeCount(productId, METRIC_DATE);

            // when
            productMetricsDailyService.increaseLikeCount(productId, otherDate);

            // then
            Long countOnMetricDate = productMetricsDailyRepository.findByProductIdAndMetricDate(productId, METRIC_DATE)
                    .orElseThrow().getLikeCount();
            Long countOnOtherDate = productMetricsDailyRepository.findByProductIdAndMetricDate(productId, otherDate)
                    .orElseThrow().getLikeCount();
            assertAll(
                    () -> assertThat(countOnMetricDate).isEqualTo(1L),
                    () -> assertThat(countOnOtherDate).isEqualTo(1L)
            );
        }
    }

    @DisplayName("좋아요 수를 감소시킬 때,")
    @Nested
    class DecreaseLikeCount {

        @DisplayName("0 밑으로 내려가지 않는다.")
        @Test
        void doesNotGoBelowZero_whenLikeCountIsAlreadyZero() {
            // given
            Long productId = 4L;

            // when
            productMetricsDailyService.decreaseLikeCount(productId, METRIC_DATE);

            // then
            Long likeCount = productMetricsDailyRepository.findByProductIdAndMetricDate(productId, METRIC_DATE)
                    .orElseThrow().getLikeCount();
            assertThat(likeCount).isEqualTo(0L);
        }

        @DisplayName("1 이상이면 1 감소한다.")
        @Test
        void decreases_whenLikeCountIsPositive() {
            // given
            Long productId = 5L;
            productMetricsDailyService.increaseLikeCount(productId, METRIC_DATE);
            productMetricsDailyService.increaseLikeCount(productId, METRIC_DATE);

            // when
            productMetricsDailyService.decreaseLikeCount(productId, METRIC_DATE);

            // then
            Long likeCount = productMetricsDailyRepository.findByProductIdAndMetricDate(productId, METRIC_DATE)
                    .orElseThrow().getLikeCount();
            assertThat(likeCount).isEqualTo(1L);
        }
    }

    @DisplayName("주문 수를 증가시킬 때,")
    @Nested
    class IncreaseOrderCount {

        @DisplayName("주문 수량만큼 order_count가 누적된다.")
        @Test
        void accumulatesByQuantity_whenOrderIncreased() {
            // given
            Long productId = 6L;
            productMetricsDailyService.increaseOrderCount(productId, METRIC_DATE, 3L);

            // when
            productMetricsDailyService.increaseOrderCount(productId, METRIC_DATE, 2L);

            // then
            Long orderCount = productMetricsDailyRepository.findByProductIdAndMetricDate(productId, METRIC_DATE)
                    .orElseThrow().getOrderCount();
            assertThat(orderCount).isEqualTo(5L);
        }
    }

    @DisplayName("조회수를 증가시킬 때,")
    @Nested
    class IncreaseViewCount {

        @DisplayName("view_count가 1 누적된다.")
        @Test
        void accumulates_whenViewIncreased() {
            // given
            Long productId = 7L;
            productMetricsDailyService.increaseViewCount(productId, METRIC_DATE);

            // when
            productMetricsDailyService.increaseViewCount(productId, METRIC_DATE);

            // then
            Long viewCount = productMetricsDailyRepository.findByProductIdAndMetricDate(productId, METRIC_DATE)
                    .orElseThrow().getViewCount();
            assertThat(viewCount).isEqualTo(2L);
        }
    }
}
