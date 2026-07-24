package com.loopers.batch.job.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProductMetricAggregateKeysetQueryProviderTest {

    @DisplayName("첫 페이지는 파생 테이블 없이 상품별 Metric을 집계한다")
    @Test
    void aggregatesFirstPageWithoutDerivedTable() throws Exception {
        // arrange
        ProductMetricAggregateKeysetQueryProvider queryProvider =
            initializedQueryProvider();

        // act
        String query = queryProvider.generateFirstPageQuery(1_000);

        // assert
        assertThat(query)
            .doesNotContain("MAIN_QRY", ":_product_id")
            .contains(
                "metric_date between :periodStart and :aggregationEndDate",
                "GROUP BY product_id",
                "ORDER BY product_id ASC",
                "LIMIT 1000"
            );
    }

    @DisplayName("다음 페이지는 집계 전에 상품 ID 키셋 조건을 적용한다")
    @Test
    void appliesProductIdKeysetConditionBeforeGrouping() throws Exception {
        // arrange
        ProductMetricAggregateKeysetQueryProvider queryProvider =
            initializedQueryProvider();

        // act
        String query = queryProvider.generateRemainingPagesQuery(1_000);

        // assert
        assertThat(query)
            .doesNotContain("MAIN_QRY")
            .contains(
                "product_id > :_product_id",
                "ORDER BY product_id ASC",
                "LIMIT 1000"
            );
        assertThat(query.indexOf("product_id > :_product_id"))
            .isLessThan(query.indexOf("GROUP BY product_id"));
    }

    private ProductMetricAggregateKeysetQueryProvider initializedQueryProvider()
        throws Exception {
        ProductMetricAggregateKeysetQueryProvider queryProvider =
            new ProductMetricAggregateKeysetQueryProvider();
        queryProvider.init(mock(DataSource.class));
        return queryProvider;
    }
}
