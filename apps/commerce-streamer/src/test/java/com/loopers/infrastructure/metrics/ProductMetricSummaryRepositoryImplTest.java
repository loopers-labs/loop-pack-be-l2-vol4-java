package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricSummaryEntity;
import com.loopers.domain.metrics.ProductMetricSummaryRepository;
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
@DisplayName("ProductMetricSummaryRepository 통합 테스트")
class ProductMetricSummaryRepositoryImplTest {

    @Autowired
    private ProductMetricSummaryRepository productMetricSummaryRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("[ECP] 존재하지 않는 상품에 조회수 증가를 호출하면 행이 생성되고 감사 컬럼이 채워진다.")
    @Test
    void incrementViewCount_createsRow_withAuditColumns_whenProductIsNew() {
        // act
        productMetricSummaryRepository.incrementViewCount("PRD_1");

        // assert
        ProductMetricSummaryEntity saved = productMetricSummaryRepository.findByProductId("PRD_1").orElseThrow();
        assertEquals(1L, saved.getViewCount());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @DisplayName("[ECP] 이미 존재하는 상품에 조회수 증가를 호출하면 created_at은 유지하고 count와 updated_at만 갱신된다.")
    @Test
    void incrementViewCount_incrementsCount_andKeepsCreatedAt_whenProductExists() {
        // arrange
        productMetricSummaryRepository.incrementViewCount("PRD_1");
        ProductMetricSummaryEntity first = productMetricSummaryRepository.findByProductId("PRD_1").orElseThrow();

        // act
        productMetricSummaryRepository.incrementViewCount("PRD_1");

        // assert
        ProductMetricSummaryEntity second = productMetricSummaryRepository.findByProductId("PRD_1").orElseThrow();
        assertEquals(2L, second.getViewCount());
        assertTrue(first.getCreatedAt().isEqual(second.getCreatedAt()));
    }

    @DisplayName("[Boundary] 좋아요 수가 0인 상품에 감소를 호출해도 음수가 되지 않는다.")
    @Test
    void decrementLikeCount_neverGoesBelowZero() {
        // arrange
        productMetricSummaryRepository.incrementViewCount("PRD_1");

        // act
        productMetricSummaryRepository.decrementLikeCount("PRD_1");

        // assert
        assertEquals(0L, productMetricSummaryRepository.findByProductId("PRD_1").orElseThrow().getLikeCount());
    }

    @DisplayName("[ECP] 구매 수량 증가를 호출하면 amount만큼 누적된다.")
    @Test
    void incrementPurchaseCount_addsAmount() {
        // act
        productMetricSummaryRepository.incrementPurchaseCount("PRD_1", 3L);
        productMetricSummaryRepository.incrementPurchaseCount("PRD_1", 2L);

        // assert
        assertEquals(5L, productMetricSummaryRepository.findByProductId("PRD_1").orElseThrow().getPurchaseCount());
    }

    @DisplayName("[동시성] 같은 상품에 대한 동시 조회수 증가 요청이 유실 없이 모두 반영된다.")
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
                    productMetricSummaryRepository.incrementViewCount("PRD_1");
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // assert
        assertEquals((long) threadCount, productMetricSummaryRepository.findByProductId("PRD_1").orElseThrow().getViewCount());
    }
}
