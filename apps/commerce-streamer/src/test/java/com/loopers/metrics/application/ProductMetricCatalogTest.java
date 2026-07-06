package com.loopers.metrics.application;

import com.loopers.metrics.domain.ProductMetric;
import com.loopers.metrics.infrastructure.ProductMetricJpaRepository;
import com.loopers.metrics.interfaces.CatalogEventMessage;
import com.loopers.metrics.interfaces.CatalogEventType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요/조회 = 이벤트가 담은 delta 를 그대로 증분(self-contained). SSOT 재조회 없음.
 * 좋아요는 +1(등록)/-1(취소), 조회는 +1. 재전달되면 중복 누적되는 best-effort 지표.
 */
@SpringBootTest
class ProductMetricCatalogTest {

    private final ProductMetricService productMetricService;
    private final ProductMetricJpaRepository productMetricJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductMetricCatalogTest(ProductMetricService productMetricService,
                             ProductMetricJpaRepository productMetricJpaRepository,
                             DatabaseCleanUp databaseCleanUp) {
        this.productMetricService = productMetricService;
        this.productMetricJpaRepository = productMetricJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CatalogEventMessage like(long productId, long delta) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), productId, CatalogEventType.LIKE, delta);
    }

    private CatalogEventMessage view(long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), productId, CatalogEventType.VIEW, 1);
    }

    private ProductMetric metric(long productId) {
        return productMetricJpaRepository.findById(productId).orElseThrow();
    }

    @Test
    @DisplayName("좋아요 등록 이벤트의 delta(+1)만큼 좋아요 수를 증가시킨다")
    void givenLikeEvents_whenApplied_thenLikeCountIncreases() {
        productMetricService.applyCatalog(like(100L, 1));
        productMetricService.applyCatalog(like(100L, 1));

        assertThat(metric(100L).getLikeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("좋아요 취소 이벤트의 delta(-1)만큼 좋아요 수를 감소시킨다")
    void givenUnlikeEvent_whenApplied_thenLikeCountDecreases() {
        productMetricService.applyCatalog(like(100L, 1));
        productMetricService.applyCatalog(like(100L, 1));
        productMetricService.applyCatalog(like(100L, -1));

        assertThat(metric(100L).getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("조회 이벤트는 view_count 를 근사 증분한다")
    void givenViewEvents_whenApplied_thenViewCountIncreases() {
        productMetricService.applyCatalog(view(100L));
        productMetricService.applyCatalog(view(100L));

        assertThat(metric(100L).getViewCount()).isEqualTo(2);
    }
}
