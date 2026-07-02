package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// @Modifying 쿼리(applyLike/addSales)를 리포지토리에서 직접 호출하므로(프로세서 @Transactional 경계 밖) 트랜잭션이 필요
@SpringBootTest
@Transactional
class ProductMetricsUpsertTest {

    @DynamicPropertySource
    static void disableKafkaListeners(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired private ProductMetricsRepository repository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("applyLike 는 version 이 더 클 때만 반영한다(오래된 이벤트 무시).")
    @Test
    void applyLikeVersionGuard() {
        repository.applyLike(1L, 10L, 5L);   // 최신
        repository.applyLike(1L, 3L, 2L);    // 과거 → 무시

        ProductMetrics m = repository.find(1L).orElseThrow();
        assertThat(m.getLikeCount()).isEqualTo(10L);
        assertThat(m.getLikeVersion()).isEqualTo(5L);
    }

    @DisplayName("addSales 는 누적한다.")
    @Test
    void addSalesAccumulates() {
        repository.addSales(1L, 2);
        repository.addSales(1L, 3);

        assertThat(repository.find(1L).orElseThrow().getSalesCount()).isEqualTo(5L);
    }
}
