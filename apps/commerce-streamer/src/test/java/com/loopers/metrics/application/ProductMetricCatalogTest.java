package com.loopers.metrics.application;

import com.loopers.metrics.domain.ProductMetric;
import com.loopers.metrics.domain.ProductMetricId;
import com.loopers.metrics.infrastructure.ProductMetricJpaRepository;
import com.loopers.metrics.interfaces.CatalogEventMessage;
import com.loopers.metrics.interfaces.CatalogEventType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요/조회 = 이벤트가 담은 delta 를 그대로 증분(self-contained). SSOT 재조회 없음.
 * 좋아요는 +1(등록)/-1(취소), 조회는 +1. 재전달되면 중복 누적되는 best-effort 지표.
 * 집계 단위는 (stat_date, product_id) 이며 날짜는 컨슈머가 정해 넘긴다.
 */
@SpringBootTest
class ProductMetricCatalogTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 22);

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
        return new CatalogEventMessage(UUID.randomUUID().toString(), productId, CatalogEventType.LIKE, delta, ZonedDateTime.now());
    }

    private CatalogEventMessage view(long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), productId, CatalogEventType.VIEW, 1, ZonedDateTime.now());
    }

    private ProductMetric metric(long productId, LocalDate statDate) {
        return productMetricJpaRepository.findById(new ProductMetricId(productId, statDate)).orElseThrow();
    }

    @Test
    @DisplayName("좋아요 등록 이벤트의 delta(+1)만큼 좋아요 수를 증가시킨다")
    void givenLikeEvents_whenApplied_thenLikeCountIncreases() {
        productMetricService.applyCatalog(like(100L, 1), DATE);
        productMetricService.applyCatalog(like(100L, 1), DATE);

        assertThat(metric(100L, DATE).getLikeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("좋아요 취소 이벤트의 delta(-1)만큼 좋아요 수를 감소시킨다")
    void givenUnlikeEvent_whenApplied_thenLikeCountDecreases() {
        productMetricService.applyCatalog(like(100L, 1), DATE);
        productMetricService.applyCatalog(like(100L, 1), DATE);
        productMetricService.applyCatalog(like(100L, -1), DATE);

        assertThat(metric(100L, DATE).getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("조회 이벤트는 view_count 를 근사 증분한다")
    void givenViewEvents_whenApplied_thenViewCountIncreases() {
        productMetricService.applyCatalog(view(100L), DATE);
        productMetricService.applyCatalog(view(100L), DATE);

        assertThat(metric(100L, DATE).getViewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 상품이라도 stat_date 가 다르면 날짜별로 나뉘어 쌓인다")
    void givenSameProductOnDifferentDates_whenApplied_thenAggregatedPerDate() {
        LocalDate nextDay = DATE.plusDays(1);

        productMetricService.applyCatalog(view(100L), DATE);
        productMetricService.applyCatalog(view(100L), DATE);
        productMetricService.applyCatalog(view(100L), nextDay);

        assertThat(metric(100L, DATE).getViewCount()).isEqualTo(2);
        assertThat(metric(100L, nextDay).getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 날에 걸친 조회수는 기간 SUM 으로 합산된다")
    void givenViewsAcrossDays_whenSummedOverRange_thenTotalIsAccumulated() {
        productMetricService.applyCatalog(view(100L), DATE);
        productMetricService.applyCatalog(view(100L), DATE.plusDays(1));
        productMetricService.applyCatalog(view(100L), DATE.plusDays(2));

        long total = productMetricJpaRepository.findAll().stream()
                .filter(m -> m.getProductId().equals(100L))
                .mapToLong(ProductMetric::getViewCount)
                .sum();

        assertThat(total).isEqualTo(3);
    }
}
