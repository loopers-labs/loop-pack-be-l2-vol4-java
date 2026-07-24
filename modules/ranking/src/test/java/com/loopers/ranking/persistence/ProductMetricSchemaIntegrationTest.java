package com.loopers.ranking.persistence;

import com.loopers.RankingPersistenceTestApplication;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    classes = RankingPersistenceTestApplication.class,
    properties = "spring.config.import=classpath:jpa.yml"
)
class ProductMetricSchemaIntegrationTest {

    private static final LocalDate METRIC_DATE = LocalDate.of(2026, 7, 2);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 7, 2, 2, 0);

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductMetricSchemaIntegrationTest(
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("일간 상품 지표 스키마를 생성할 때")
    @Nested
    class CreateSchema {

        @DisplayName("날짜와 상품 순서의 복합 기본키를 생성한다.")
        @Test
        void createsDateLeadingCompositePrimaryKey() {
            // act
            List<String> primaryKeyColumns = jdbcTemplate.queryForList(
                """
                    select column_name
                    from information_schema.key_column_usage
                    where table_schema = database()
                      and table_name = 'product_metrics'
                      and constraint_name = 'PRIMARY'
                    order by ordinal_position
                    """,
                String.class
            );

            // assert
            assertThat(primaryKeyColumns).containsExactly("metric_date", "product_id");
        }

        @DisplayName("상품별 기간 집계를 위한 상품과 날짜 순서의 보조 인덱스를 생성한다")
        @Test
        void createsProductLeadingAggregationIndex() {
            // act
            List<String> indexColumns = jdbcTemplate.queryForList(
                """
                    select column_name
                    from information_schema.statistics
                    where table_schema = database()
                      and table_name = 'product_metrics'
                      and index_name = 'idx_product_metrics_product_id_metric_date'
                    order by seq_in_index
                    """,
                String.class
            );

            // assert
            assertThat(indexColumns).containsExactly("product_id", "metric_date");
        }

        @DisplayName("랭킹 집계에 필요한 일간 지표 컬럼 계약을 생성한다.")
        @Test
        void createsDailyMetricColumnContracts() {
            // act
            List<ColumnSchema> columns = jdbcTemplate.query(
                """
                    select column_name, data_type, is_nullable, datetime_precision
                    from information_schema.columns
                    where table_schema = database()
                      and table_name = 'product_metrics'
                    """,
                (resultSet, rowNumber) -> new ColumnSchema(
                    resultSet.getString("column_name"),
                    resultSet.getString("data_type"),
                    resultSet.getString("is_nullable"),
                    resultSet.getObject("datetime_precision", Integer.class)
                )
            );

            // assert
            assertThat(columns).containsExactlyInAnyOrder(
                new ColumnSchema("metric_date", "date", "NO", null),
                new ColumnSchema("product_id", "bigint", "NO", null),
                new ColumnSchema("view_count", "bigint", "NO", null),
                new ColumnSchema("like_delta", "bigint", "NO", null),
                new ColumnSchema("order_quantity", "bigint", "NO", null),
                new ColumnSchema("order_amount", "bigint", "NO", null),
                new ColumnSchema("updated_at", "datetime", "NO", 6)
            );
        }

        @DisplayName("조회와 주문 지표의 음수를 막는 이름 있는 Check 제약조건을 생성한다.")
        @Test
        void createsNamedNonNegativeConstraints() {
            // act
            List<String> constraintNames = jdbcTemplate.queryForList(
                """
                    select constraint_name
                    from information_schema.table_constraints
                    where table_schema = database()
                      and table_name = 'product_metrics'
                      and constraint_type = 'CHECK'
                    order by constraint_name
                    """,
                String.class
            );

            // assert
            assertThat(constraintNames).containsExactly(
                "ck_product_metrics_order_amount_non_negative",
                "ck_product_metrics_order_quantity_non_negative",
                "ck_product_metrics_view_count_non_negative"
            );
        }
    }

    @DisplayName("일간 상품 지표 값을 저장할 때")
    @Nested
    class SaveMetric {

        @DisplayName("좋아요 취소 누적값은 음수로 저장할 수 있다.")
        @Test
        void allowsNegativeLikeDelta() {
            // act
            insertMetric(0, -1, 0, 0);

            // assert
            Long likeDelta = jdbcTemplate.queryForObject(
                """
                    select like_delta
                    from product_metrics
                    where metric_date = ?
                      and product_id = 101
                    """,
                Long.class,
                METRIC_DATE
            );
            assertThat(likeDelta).isEqualTo(-1);
        }

        @DisplayName("조회 수와 주문 수량 및 금액은 음수로 저장할 수 없다.")
        @CsvSource({
            "-1, 0, 0",
            "0, -1, 0",
            "0, 0, -1"
        })
        @ParameterizedTest
        void rejectsNegativeNonLikeMetrics(long viewCount, long orderQuantity, long orderAmount) {
            // act & assert
            assertThatThrownBy(() -> insertMetric(viewCount, 0, orderQuantity, orderAmount))
                .isInstanceOf(DataAccessException.class);
        }
    }

    private void insertMetric(
        long viewCount,
        long likeDelta,
        long orderQuantity,
        long orderAmount
    ) {
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
                values (?, 101, ?, ?, ?, ?, ?)
                """,
            METRIC_DATE,
            viewCount,
            likeDelta,
            orderQuantity,
            orderAmount,
            UPDATED_AT
        );
    }

    private record ColumnSchema(
        String columnName,
        String dataType,
        String nullable,
        Integer datetimePrecision
    ) {
    }
}
