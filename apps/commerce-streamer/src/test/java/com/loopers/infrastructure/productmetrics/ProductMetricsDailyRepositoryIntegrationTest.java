package com.loopers.infrastructure.productmetrics;

import com.loopers.domain.productmetrics.ProductMetricsDailyModel;
import com.loopers.domain.productmetrics.ProductMetricsDailyRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 일간 롤업 테이블(product_metrics_daily) 통합 테스트 — (product_id, metric_date) 단위 upsert와 유니크 제약을 검증한다.
 * Testcontainers(MySQL) 기반이라 Docker가 필요하다.
 */
@SpringBootTest
class ProductMetricsDailyRepositoryIntegrationTest {

    @Autowired
    private ProductMetricsDailyRepository productMetricsDailyRepository;

    @Autowired
    private ProductMetricsDailyJpaRepository productMetricsDailyJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final Long PRODUCT_ID = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 22);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 상품이라도 날짜가 다르면 별도 행으로 적재된다 (기간 분리의 근거).")
    @Test
    void storesSeparateRowsPerDate() {
        // arrange
        ProductMetricsDailyModel today = new ProductMetricsDailyModel(PRODUCT_ID, TODAY);
        today.incrementSalesCount(2);
        ProductMetricsDailyModel yesterday = new ProductMetricsDailyModel(PRODUCT_ID, YESTERDAY);
        yesterday.incrementSalesCount(5);

        // act
        productMetricsDailyRepository.save(today);
        productMetricsDailyRepository.save(yesterday);

        // assert
        assertThat(productMetricsDailyJpaRepository.findAll()).hasSize(2);
        assertThat(productMetricsDailyRepository.findByProductIdAndMetricDate(PRODUCT_ID, TODAY))
            .get().extracting(ProductMetricsDailyModel::getSalesCount).isEqualTo(2L);
        assertThat(productMetricsDailyRepository.findByProductIdAndMetricDate(PRODUCT_ID, YESTERDAY))
            .get().extracting(ProductMetricsDailyModel::getSalesCount).isEqualTo(5L);
    }

    @DisplayName("같은 (상품, 날짜)의 기존 행을 조회해 증가시키면 같은 행이 누적된다 (upsert).")
    @Test
    void accumulatesOnExistingRow() {
        // arrange
        productMetricsDailyRepository.save(new ProductMetricsDailyModel(PRODUCT_ID, TODAY));

        // act — 같은 (상품, 날짜) 행을 다시 조회해 증가
        ProductMetricsDailyModel found = productMetricsDailyRepository.findByProductIdAndMetricDate(PRODUCT_ID, TODAY).orElseThrow();
        found.incrementViewCount();
        found.incrementLikeCount();
        found.incrementSalesCount(3);
        productMetricsDailyRepository.save(found);

        // assert
        assertThat(productMetricsDailyJpaRepository.findAll()).hasSize(1);
        Optional<ProductMetricsDailyModel> result = productMetricsDailyRepository.findByProductIdAndMetricDate(PRODUCT_ID, TODAY);
        assertThat(result).get().satisfies(m -> {
            assertThat(m.getViewCount()).isEqualTo(1);
            assertThat(m.getLikeCount()).isEqualTo(1);
            assertThat(m.getSalesCount()).isEqualTo(3);
        });
    }

    @DisplayName("같은 (상품, 날짜)로 서로 다른 행을 두 번 삽입하면 유니크 제약 위반이 발생한다.")
    @Test
    void rejectsDuplicateProductDate() {
        // arrange
        productMetricsDailyJpaRepository.saveAndFlush(new ProductMetricsDailyModel(PRODUCT_ID, TODAY));

        // act & assert
        assertThatThrownBy(() ->
            productMetricsDailyJpaRepository.saveAndFlush(new ProductMetricsDailyModel(PRODUCT_ID, TODAY))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
