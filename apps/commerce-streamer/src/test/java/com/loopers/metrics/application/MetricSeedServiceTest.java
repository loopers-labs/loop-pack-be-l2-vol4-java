package com.loopers.metrics.application;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시드가 (날짜 × 상품) 만큼 product_metrics 를 채우고, 다시 돌리면 덮어쓴다(누적 아님).
 */
@SpringBootTest
class MetricSeedServiceTest {

    private final MetricSeedService metricSeedService;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    MetricSeedServiceTest(MetricSeedService metricSeedService, JdbcTemplate jdbcTemplate, DatabaseCleanUp databaseCleanUp) {
        this.metricSeedService = metricSeedService;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_metrics", Long.class);
    }

    @Test
    @DisplayName("기간의 각 날짜 × 각 상품만큼 행을 채운다")
    void givenRange_whenSeed_thenRowsPerDayPerProduct() {
        int rows = metricSeedService.seed(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), List.of(100L, 200L));

        assertThat(rows).isEqualTo(6);   // 3일 × 2상품
        assertThat(count()).isEqualTo(6);
    }

    @Test
    @DisplayName("같은 기간을 다시 시드하면 누적하지 않고 덮어쓴다")
    void givenReseed_whenSeed_thenOverwrittenNotAccumulated() {
        LocalDate day = LocalDate.of(2026, 7, 1);
        metricSeedService.seed(day, day, List.of(100L));
        long first = jdbcTemplate.queryForObject(
                "SELECT view_count FROM product_metrics WHERE stat_date = ? AND product_id = 100", Long.class, day);

        metricSeedService.seed(day, day, List.of(100L));

        assertThat(count()).isEqualTo(1);
        long second = jdbcTemplate.queryForObject(
                "SELECT view_count FROM product_metrics WHERE stat_date = ? AND product_id = 100", Long.class, day);
        assertThat(second).isEqualTo(first);   // 두 배가 아니라 그대로
    }
}
