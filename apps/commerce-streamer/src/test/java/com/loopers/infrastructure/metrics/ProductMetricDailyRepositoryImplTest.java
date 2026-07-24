package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricDailyEntity;
import com.loopers.domain.metrics.ProductMetricDailyRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EmbeddedKafka(
        partitions = 3,
        topics = {"catalog-events", "order-events", "demo.internal.topic-v1"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@Import({
        MySqlTestContainersConfig.class,
        RedisTestContainersConfig.class
})
@DisplayName("ProductMetricDailyRepository 통합 테스트")
class ProductMetricDailyRepositoryImplTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 23);

    @Autowired
    private ProductMetricDailyRepository productMetricDailyRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("[ECP] 존재하지 않는 (날짜, 상품) 조합에 조회수 증가를 호출하면 행이 생성되고 감사 컬럼이 채워진다.")
    @Test
    void incrementViewCount_createsRow_withAuditColumns_whenRowIsNew() {
        // act
        productMetricDailyRepository.incrementViewCount("PRD_1", DATE);

        // assert
        ProductMetricDailyEntity saved = productMetricDailyRepository.findByProductIdAndMetricDate("PRD_1", DATE).orElseThrow();
        assertEquals(1L, saved.getViewCount());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @DisplayName("[ECP] 이미 존재하는 행에 조회수 증가를 호출하면 created_at은 유지하고 count와 updated_at만 갱신된다.")
    @Test
    void incrementViewCount_incrementsCount_andKeepsCreatedAt_whenRowExists() {
        // arrange
        productMetricDailyRepository.incrementViewCount("PRD_1", DATE);
        ProductMetricDailyEntity first = productMetricDailyRepository.findByProductIdAndMetricDate("PRD_1", DATE).orElseThrow();

        // act
        productMetricDailyRepository.incrementViewCount("PRD_1", DATE);

        // assert
        ProductMetricDailyEntity second = productMetricDailyRepository.findByProductIdAndMetricDate("PRD_1", DATE).orElseThrow();
        assertEquals(2L, second.getViewCount());
        assertTrue(first.getCreatedAt().isEqual(second.getCreatedAt()));
    }

    @DisplayName("[ECP] 좋아요 감소는 like_delta_count를 음수로 만들 수 있다 (summary와 달리 0 미만 방지가 없다).")
    @Test
    void decrementLikeDelta_canGoNegative() {
        // act
        productMetricDailyRepository.decrementLikeDelta("PRD_1", DATE);

        // assert
        assertEquals(-1L, productMetricDailyRepository.findByProductIdAndMetricDate("PRD_1", DATE).orElseThrow().getLikeDeltaCount());
    }

    @DisplayName("[ECP] 좋아요 증가/감소가 섞이면 순증감이 합산된다.")
    @Test
    void likeDelta_accumulatesNetIncrementsAndDecrements() {
        // act
        productMetricDailyRepository.incrementLikeDelta("PRD_1", DATE);
        productMetricDailyRepository.incrementLikeDelta("PRD_1", DATE);
        productMetricDailyRepository.decrementLikeDelta("PRD_1", DATE);

        // assert
        assertEquals(1L, productMetricDailyRepository.findByProductIdAndMetricDate("PRD_1", DATE).orElseThrow().getLikeDeltaCount());
    }

    @DisplayName("[ECP] 구매 수량 증가를 호출하면 amount만큼 누적된다.")
    @Test
    void incrementPurchaseQuantity_addsAmount() {
        // act
        productMetricDailyRepository.incrementPurchaseQuantity("PRD_1", DATE, 3L);
        productMetricDailyRepository.incrementPurchaseQuantity("PRD_1", DATE, 2L);

        // assert
        assertEquals(5L, productMetricDailyRepository.findByProductIdAndMetricDate("PRD_1", DATE).orElseThrow().getPurchaseQuantity());
    }

    @DisplayName("[ECP] 날짜가 다르면 같은 상품이라도 별도 행으로 집계된다.")
    @Test
    void separateDates_areAggregatedIndependently() {
        // act
        productMetricDailyRepository.incrementViewCount("PRD_1", DATE);
        productMetricDailyRepository.incrementViewCount("PRD_1", DATE.plusDays(1));

        // assert
        assertEquals(1L, productMetricDailyRepository.findByProductIdAndMetricDate("PRD_1", DATE).orElseThrow().getViewCount());
        assertEquals(1L, productMetricDailyRepository.findByProductIdAndMetricDate("PRD_1", DATE.plusDays(1)).orElseThrow().getViewCount());
    }

    @DisplayName("[동시성] 같은 (날짜, 상품)에 대한 동시 조회수 증가 요청이 유실 없이 모두 반영된다.")
    @Test
    void incrementViewCount_doesNotLoseUpdates_underConcurrentRequests() throws InterruptedException {
        // arrange
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productMetricDailyRepository.incrementViewCount("PRD_1", DATE);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // assert
        assertEquals(
                (long) threadCount,
                productMetricDailyRepository.findByProductIdAndMetricDate("PRD_1", DATE).orElseThrow().getViewCount()
        );
    }
}
