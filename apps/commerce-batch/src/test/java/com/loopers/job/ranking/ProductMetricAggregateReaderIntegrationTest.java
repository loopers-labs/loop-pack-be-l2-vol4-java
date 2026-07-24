package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.ranking.application.ProductMetricAggregate;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.StepScopeTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingSnapshotJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
@SpringBatchTest
class ProductMetricAggregateReaderIntegrationTest {

    private final JdbcPagingItemReader<ProductMetricAggregate> reader;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductMetricAggregateReaderIntegrationTest(
        @Qualifier(ProductRankingSnapshotJobConfig.PRODUCT_METRIC_AGGREGATE_READER_NAME)
        JdbcPagingItemReader<ProductMetricAggregate> reader,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.reader = reader;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        insertMetric(LocalDate.of(2026, 7, 13), 101L, 10, 2, 10_000);
        insertMetric(LocalDate.of(2026, 7, 19), 101L, 20, -1, 20_000);
        insertMetric(LocalDate.of(2026, 7, 15), 202L, 30, 0, 0);
        insertMetric(LocalDate.of(2026, 7, 1), 303L, 100, 0, 0);
        insertMetric(LocalDate.of(2026, 6, 30), 404L, 999, 0, 0);
        insertMetric(LocalDate.of(2026, 7, 20), 505L, 999, 0, 0);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주간 시작일과 종료일을 포함해 상품별 Metric 합계를 product_id 순서로 읽는다.")
    @Test
    void readsWeeklyProductMetricAggregates() throws Exception {
        // act
        List<ProductMetricAggregate> result = readAll("WEEKLY");

        // assert
        assertThat(result).containsExactly(
            new ProductMetricAggregate(101L, 30, 1, 30_000),
            new ProductMetricAggregate(202L, 30, 0, 0)
        );
    }

    @DisplayName("월초부터 종료일까지 상품별 Metric 합계를 product_id 순서로 읽는다.")
    @Test
    void readsMonthlyProductMetricAggregates() throws Exception {
        // act
        List<ProductMetricAggregate> result = readAll("MONTHLY");

        // assert
        assertThat(result).containsExactly(
            new ProductMetricAggregate(101L, 30, 1, 30_000),
            new ProductMetricAggregate(202L, 30, 0, 0),
            new ProductMetricAggregate(303L, 100, 0, 0)
        );
    }

    private List<ProductMetricAggregate> readAll(String period) throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
            .addString("period", period)
            .addString("aggregationEndDate", "20260719")
            .addLong("revision", 1L)
            .toJobParameters();
        StepExecution stepExecution =
            MetaDataInstanceFactory.createStepExecution(jobParameters);

        return StepScopeTestUtils.doInStepScope(stepExecution, () -> {
            List<ProductMetricAggregate> aggregates = new ArrayList<>();
            reader.open(stepExecution.getExecutionContext());
            try {
                ProductMetricAggregate aggregate;
                while ((aggregate = reader.read()) != null) {
                    aggregates.add(aggregate);
                }
                reader.update(stepExecution.getExecutionContext());
                return aggregates;
            } finally {
                reader.close();
            }
        });
    }

    private void insertMetric(
        LocalDate metricDate,
        long productId,
        long viewCount,
        long likeDelta,
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
                values (?, ?, ?, ?, 0, ?, ?)
                """,
            metricDate,
            productId,
            viewCount,
            likeDelta,
            orderAmount,
            LocalDateTime.of(2026, 7, 20, 2, 0)
        );
    }
}
