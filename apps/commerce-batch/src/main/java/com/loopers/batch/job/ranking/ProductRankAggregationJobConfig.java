package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.batch.repeat.RepeatStatus;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class ProductRankAggregationJobConfig {

    public static final String JOB_NAME = "productRankAggregationJob";
    private static final String STEP_CLEAR_MV = "clearProductRankMvStep";
    private static final String STEP_AGGREGATE_RANK = "aggregateProductRankStep";
    private static final int CHUNK_SIZE = 500;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Bean(JOB_NAME)
    public Job productRankAggregationJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(clearProductRankMvStep())
            .next(aggregateProductRankStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_CLEAR_MV)
    public Step clearProductRankMvStep() {
        return new StepBuilder(STEP_CLEAR_MV, jobRepository)
            .tasklet((contribution, chunkContext) -> {
                ProductRankPeriod period = period(chunkContext.getStepContext().getJobParameters().get("period"));
                LocalDate targetDate = targetDate(chunkContext.getStepContext().getJobParameters().get("targetDate"));
                jdbcTemplate.update(
                    "DELETE FROM " + period.tableName() + " WHERE period_start_date = ?",
                    period.startDate(targetDate));
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_AGGREGATE_RANK)
    public Step aggregateProductRankStep() {
        return new StepBuilder(STEP_AGGREGATE_RANK, jobRepository)
            .<AggregatedProductRank, ProductRankMvRow>chunk(CHUNK_SIZE, transactionManager)
            .reader(productRankReader(null, null))
            .processor(productRankProcessor(null, null))
            .writer(productRankWriter(null))
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build();
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<AggregatedProductRank> productRankReader(
        @Value("#{jobParameters['period']}") String periodValue,
        @Value("#{jobParameters['targetDate']}") String targetDateValue
    ) {
        ProductRankPeriod period = ProductRankPeriod.from(periodValue);
        LocalDate targetDate = ProductRankPeriod.parseTargetDate(targetDateValue);
        LocalDate start = period.startDate(targetDate);
        LocalDate end = period.endDate(targetDate);

        String sql = """
            SELECT
                product_id,
                SUM(view_count) AS view_count,
                SUM(like_count) AS like_count,
                SUM(sale_count) AS sale_count,
                SUM(order_score) AS order_score,
                (SUM(view_count) * 0.1 + SUM(like_count) * 0.2 + SUM(order_score) * 0.6) AS score
            FROM product_metrics_daily
            WHERE metric_date BETWEEN ? AND ?
            GROUP BY product_id
            ORDER BY score DESC, product_id ASC
            LIMIT 100
            """;

        return new JdbcCursorItemReaderBuilder<AggregatedProductRank>()
            .name("productRankReader")
            .dataSource(dataSource)
            .sql(sql)
            .preparedStatementSetter(ps -> {
                ps.setDate(1, Date.valueOf(start));
                ps.setDate(2, Date.valueOf(end));
            })
            .rowMapper((rs, rowNum) -> new AggregatedProductRank(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getLong("sale_count"),
                rs.getDouble("order_score"),
                rs.getDouble("score")))
            .build();
    }

    @StepScope
    @Bean
    public ItemProcessor<AggregatedProductRank, ProductRankMvRow> productRankProcessor(
        @Value("#{jobParameters['period']}") String periodValue,
        @Value("#{jobParameters['targetDate']}") String targetDateValue
    ) {
        ProductRankPeriod period = ProductRankPeriod.from(periodValue);
        LocalDate targetDate = ProductRankPeriod.parseTargetDate(targetDateValue);
        LocalDate start = period.startDate(targetDate);
        LocalDate end = period.endDate(targetDate);
        AtomicInteger rank = new AtomicInteger(0);

        return item -> new ProductRankMvRow(
            start,
            end,
            rank.incrementAndGet(),
            item.productId(),
            item.score(),
            item.viewCount(),
            item.likeCount(),
            item.saleCount(),
            item.orderScore());
    }

    @StepScope
    @Bean
    public JdbcBatchItemWriter<ProductRankMvRow> productRankWriter(
        @Value("#{jobParameters['period']}") String periodValue
    ) {
        ProductRankPeriod period = ProductRankPeriod.from(periodValue);
        return new JdbcBatchItemWriterBuilder<ProductRankMvRow>()
            .dataSource(dataSource)
            .sql("INSERT INTO " + period.tableName() + " "
                + "(period_start_date, period_end_date, rank_no, product_id, score, "
                + "view_count, like_count, sale_count, order_score, created_at, updated_at) "
                + "VALUES (:periodStartDate, :periodEndDate, :rankNo, :productId, :score, "
                + ":viewCount, :likeCount, :saleCount, :orderScore, NOW(6), NOW(6))")
            .beanMapped()
            .build();
    }

    private ProductRankPeriod period(Object value) {
        return ProductRankPeriod.from(value == null ? null : value.toString());
    }

    private LocalDate targetDate(Object value) {
        return ProductRankPeriod.parseTargetDate(value == null ? null : value.toString());
    }
}
