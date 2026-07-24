package com.loopers.infrastructure.catalog;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@Transactional
class ProductDailyMetricsJpaRepositoryIntegrationTest {

    @Autowired private ProductDailyMetricsJpaRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long orderCountOf(Long productId) {
        return jdbcTemplate.queryForObject(
            "SELECT order_count FROM product_daily_metrics WHERE product_id = ? AND metric_date = ?",
            Long.class, productId, LocalDate.now().toString());
    }

    private Long likeCountOf(Long productId) {
        return jdbcTemplate.queryForObject(
            "SELECT like_count FROM product_daily_metrics WHERE product_id = ? AND metric_date = ?",
            Long.class, productId, LocalDate.now().toString());
    }

    private Long viewCountOf(Long productId) {
        return jdbcTemplate.queryForObject(
            "SELECT view_count FROM product_daily_metrics WHERE product_id = ? AND metric_date = ?",
            Long.class, productId, LocalDate.now().toString());
    }

    @DisplayName("upsertOrderCount()를 실행할 때,")
    @Nested
    class UpsertOrderCount {

        @DisplayName("해당 날짜의 행이 없으면 새로 만들고 delta만큼 order_count를 채운다.")
        @Test
        void createsRow_whenNoRowForToday() {
            repository.upsertOrderCount(10L, 2);

            assertThat(orderCountOf(10L)).isEqualTo(2L);
        }

        @DisplayName("이미 오늘 행이 있으면 order_count를 delta만큼 누적시킨다.")
        @Test
        void accumulates_whenRowAlreadyExistsForToday() {
            repository.upsertOrderCount(10L, 2);
            repository.upsertOrderCount(10L, 3);

            assertThat(orderCountOf(10L)).isEqualTo(5L);
        }
    }

    @DisplayName("upsertLikeCountIncrement()/upsertLikeCountDecrement()를 실행할 때,")
    @Nested
    class UpsertLikeCount {

        @DisplayName("like_count를 1씩 증가시킨다.")
        @Test
        void increments() {
            repository.upsertLikeCountIncrement(20L);
            repository.upsertLikeCountIncrement(20L);

            assertThat(likeCountOf(20L)).isEqualTo(2L);
        }

        @DisplayName("like_count를 1씩 감소시키되 0 밑으로는 내려가지 않는다.")
        @Test
        void decrementsWithFloorAtZero() {
            repository.upsertLikeCountIncrement(20L);

            repository.upsertLikeCountDecrement(20L);
            repository.upsertLikeCountDecrement(20L);

            assertThat(likeCountOf(20L)).isEqualTo(0L);
        }
    }

    @DisplayName("upsertViewCountIncrement()를 실행할 때,")
    @Nested
    class UpsertViewCount {

        @DisplayName("view_count를 1씩 증가시킨다.")
        @Test
        void increments() {
            repository.upsertViewCountIncrement(30L);
            repository.upsertViewCountIncrement(30L);
            repository.upsertViewCountIncrement(30L);

            assertThat(viewCountOf(30L)).isEqualTo(3L);
        }
    }
}
