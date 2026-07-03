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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 = likes(SSOT) 재계산 덮어쓰기(멱등). 조회 = SSOT 없는 소프트 지표라 근사 증분.
 */
@SpringBootTest
class ProductMetricCatalogTest {

    private final ProductMetricService productMetricService;
    private final ProductMetricJpaRepository productMetricJpaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductMetricCatalogTest(ProductMetricService productMetricService,
                             ProductMetricJpaRepository productMetricJpaRepository,
                             JdbcTemplate jdbcTemplate,
                             DatabaseCleanUp databaseCleanUp) {
        this.productMetricService = productMetricService;
        this.productMetricJpaRepository = productMetricJpaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void seedLike(long userId, long productId) {
        jdbcTemplate.update("""
                INSERT INTO likes (user_id, product_id, created_at, updated_at)
                VALUES (?, ?, NOW(6), NOW(6))
                """, userId, productId);
    }

    private CatalogEventMessage like(long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), productId, CatalogEventType.LIKE, 1);
    }

    private CatalogEventMessage view(long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), productId, CatalogEventType.VIEW, 1);
    }

    private long likeCount(long productId) {
        return productMetricJpaRepository.findById(productId).map(ProductMetric::getLikeCount).orElse(0L);
    }

    @Test
    @DisplayName("좋아요 이벤트를 트리거로 likes 수를 재계산한다")
    void givenLikes_whenApplyLike_thenLikeCountRecomputed() {
        seedLike(1L, 100L);
        seedLike(2L, 100L);

        productMetricService.applyCatalog(like(100L));

        assertThat(likeCount(100L)).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 좋아요 트리거를 두 번 처리해도 재계산이라 동일하다(멱등)")
    void givenSameLikeTrigger_whenAppliedTwice_thenSame() {
        seedLike(1L, 100L);

        productMetricService.applyCatalog(like(100L));
        productMetricService.applyCatalog(like(100L));

        assertThat(likeCount(100L)).isEqualTo(1);
    }

    @Test
    @DisplayName("soft-delete(취소)된 좋아요는 재계산에서 제외된다")
    void givenSoftDeletedLike_whenRecomputed_thenExcluded() {
        seedLike(1L, 100L);
        jdbcTemplate.update("UPDATE likes SET deleted_at = NOW(6) WHERE user_id = 1 AND product_id = 100");

        productMetricService.applyCatalog(like(100L));

        assertThat(likeCount(100L)).isZero();
    }

    @Test
    @DisplayName("조회 이벤트는 view_count 를 근사 증분한다")
    void givenViewEvents_whenApplied_thenViewCountIncreases() {
        productMetricService.applyCatalog(view(100L));
        productMetricService.applyCatalog(view(100L));

        ProductMetric metric = productMetricJpaRepository.findById(100L).orElseThrow();
        assertThat(metric.getViewCount()).isEqualTo(2);
    }
}
