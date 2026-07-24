package com.loopers.infrastructure.ranking.batch;

import com.loopers.domain.ranking.batch.RankingDailyMetricsAggregate;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class ProductDailyMetricsJpaRepositoryIntegrationTest {

    @Autowired private ProductDailyMetricsJpaRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void insertDailyMetrics(LocalDate date, Long productId, long orderCount, long likeCount, long viewCount) {
        jdbcTemplate.update(
            """
                INSERT INTO product_daily_metrics (metric_date, product_id, order_count, like_count, view_count, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """,
            date, productId, orderCount, likeCount, viewCount
        );
    }

    @DisplayName("aggregateByDateRange()를 실행할 때,")
    @Nested
    class AggregateByDateRange {

        @DisplayName("날짜 범위 안의 여러 날짜를 상품별로 SUM해서 합산한다.")
        @Test
        void sumsCountsAcrossDatesWithinRange() {
            insertDailyMetrics(LocalDate.of(2026, 7, 13), 1L, 1, 2, 3);
            insertDailyMetrics(LocalDate.of(2026, 7, 14), 1L, 4, 5, 6);
            insertDailyMetrics(LocalDate.of(2026, 7, 19), 1L, 10, 10, 10);

            List<RankingDailyMetricsAggregate> result = repository.aggregateByDateRange(
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19),
                PageRequest.of(0, 10, Sort.by("productId"))
            ).getContent();

            assertThat(result).hasSize(1);
            RankingDailyMetricsAggregate aggregate = result.get(0);
            assertThat(aggregate.productId()).isEqualTo(1L);
            assertThat(aggregate.orderCount()).isEqualTo(15L);
            assertThat(aggregate.likeCount()).isEqualTo(17L);
            assertThat(aggregate.viewCount()).isEqualTo(19L);
        }

        @DisplayName("날짜 범위 밖의 로우는 집계에서 제외한다.")
        @Test
        void excludesRowsOutsideRange() {
            insertDailyMetrics(LocalDate.of(2026, 7, 12), 1L, 100, 100, 100);
            insertDailyMetrics(LocalDate.of(2026, 7, 20), 1L, 100, 100, 100);
            insertDailyMetrics(LocalDate.of(2026, 7, 13), 1L, 1, 1, 1);

            List<RankingDailyMetricsAggregate> result = repository.aggregateByDateRange(
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19),
                PageRequest.of(0, 10, Sort.by("productId"))
            ).getContent();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).orderCount()).isEqualTo(1L);
        }

        @DisplayName("여러 상품이 있으면 상품별로 각각 그룹핑되고 productId 오름차순으로 정렬된다.")
        @Test
        void groupsByProductAndOrdersByProductIdAscending() {
            insertDailyMetrics(LocalDate.of(2026, 7, 15), 20L, 2, 2, 2);
            insertDailyMetrics(LocalDate.of(2026, 7, 15), 10L, 1, 1, 1);

            List<RankingDailyMetricsAggregate> result = repository.aggregateByDateRange(
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19),
                PageRequest.of(0, 10, Sort.by("productId"))
            ).getContent();

            assertThat(result).extracting(RankingDailyMetricsAggregate::productId).containsExactly(10L, 20L);
        }
    }
}
