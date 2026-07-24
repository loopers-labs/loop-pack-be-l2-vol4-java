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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// @Modifying 쿼리(applyLike/addSales)를 리포지토리에서 직접 호출하므로(프로세서 @Transactional 경계 밖) 트랜잭션이 필요
@SpringBootTest
@Transactional
class ProductMetricsUpsertTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 7, 20);
    private static final LocalDate DAY2 = LocalDate.of(2026, 7, 21);

    @DynamicPropertySource
    static void disableKafkaListeners(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired private ProductMetricsRepository repository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("applyLike 의 스냅샷(like_count)은 version 이 더 클 때만 반영한다(오래된 이벤트 무시).")
    @Test
    void applyLikeVersionGuard() {
        repository.applyLike(1L, DAY1, 10L, 5L, 1);   // 최신
        repository.applyLike(1L, DAY1, 3L, 2L, 1);    // 과거 → 스냅샷 무시

        ProductMetrics m = repository.find(1L, DAY1).orElseThrow();
        assertThat(m.getLikeCount()).isEqualTo(10L);
        assertThat(m.getLikeVersion()).isEqualTo(5L);
    }

    @DisplayName("like_delta 는 version 가드와 무관하게 이벤트마다 누적된다(취소는 음수).")
    @Test
    void likeDeltaAccumulatesRegardlessOfVersion() {
        repository.applyLike(1L, DAY1, 10L, 5L, 1);   // 좋아요
        repository.applyLike(1L, DAY1, 11L, 6L, 1);   // 좋아요
        repository.applyLike(1L, DAY1, 10L, 7L, -1);  // 취소

        assertThat(repository.find(1L, DAY1).orElseThrow().getLikeDelta()).isEqualTo(1L);
    }

    @DisplayName("같은 상품이라도 일자가 다르면 별도 로우로 집계된다.")
    @Test
    void separatesRowsPerDate() {
        repository.addView(1L, DAY1);
        repository.addView(1L, DAY1);
        repository.addView(1L, DAY2);

        assertThat(repository.find(1L, DAY1).orElseThrow().getViewCount()).isEqualTo(2L);
        assertThat(repository.find(1L, DAY2).orElseThrow().getViewCount()).isEqualTo(1L);
    }

    @DisplayName("addSales 는 같은 일자에 누적한다.")
    @Test
    void addSalesAccumulates() {
        repository.addSales(1L, DAY1, 2);
        repository.addSales(1L, DAY1, 3);

        assertThat(repository.find(1L, DAY1).orElseThrow().getSalesCount()).isEqualTo(5L);
    }

    @DisplayName("addView 는 1씩 누적하고 다른 지표는 건드리지 않는다.")
    @Test
    void addViewAccumulates() {
        repository.applyLike(1L, DAY1, 10L, 5L, 1);
        repository.addView(1L, DAY1);
        repository.addView(1L, DAY1);

        ProductMetrics m = repository.find(1L, DAY1).orElseThrow();
        assertThat(m.getViewCount()).isEqualTo(2L);
        assertThat(m.getLikeCount()).isEqualTo(10L);
        assertThat(m.getLikeDelta()).isEqualTo(1L);
    }
}
