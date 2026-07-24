package com.loopers.ranking.infrastructure;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class JdbcProductRankingSourceQueryIntegrationTest {

    private final JdbcProductRankingSourceQuery sourceQuery;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    JdbcProductRankingSourceQueryIntegrationTest(
        JdbcProductRankingSourceQuery sourceQuery,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.sourceQuery = sourceQuery;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("기간 양끝을 포함하고 같은 상품의 여러 일간 Metric은 한 상품으로 센다.")
    @Test
    void countsDistinctProductsInInclusivePeriod() {
        // arrange
        insertMetric(LocalDate.of(2026, 7, 12), 404L);
        insertMetric(LocalDate.of(2026, 7, 13), 101L);
        insertMetric(LocalDate.of(2026, 7, 19), 101L);
        insertMetric(LocalDate.of(2026, 7, 15), 202L);
        insertMetric(LocalDate.of(2026, 7, 20), 505L);

        // act
        long result = sourceQuery.countProductsWithMetrics(
            LocalDate.of(2026, 7, 13),
            LocalDate.of(2026, 7, 19)
        );

        // assert
        assertThat(result).isEqualTo(2);
    }

    private void insertMetric(LocalDate metricDate, long productId) {
        jdbcTemplate.update(
            """
                insert into product_metrics(
                    metric_date,
                    product_id,
                    view_count,
                    like_delta,
                    order_quantity,
                    order_amount,
                    updated_at
                )
                values (?, ?, 1, 0, 0, 0, ?)
                """,
            metricDate,
            productId,
            LocalDateTime.of(2026, 7, 20, 2, 0)
        );
    }
}
