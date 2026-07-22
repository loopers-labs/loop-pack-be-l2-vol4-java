package com.loopers.batch.job.catalog.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Date;
import java.util.concurrent.atomic.AtomicLong;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankingAggregationJobConfig.JOB_NAME)
@Configuration
public class ProductRankingAggregationJobConfig {

    public static final String JOB_NAME = "productRankingAggregationJob";
    private static final String VALIDATE_STEP_NAME = "validateProductRankingAggregationParametersStep";
    private static final String CLEANUP_STEP_NAME = "cleanupProductRankMvStep";
    private static final String AGGREGATE_STEP_NAME = "aggregateProductRankStep";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;

    public ProductRankingAggregationJobConfig(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        JobListener jobListener,
        StepMonitorListener stepMonitorListener
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.jobListener = jobListener;
        this.stepMonitorListener = stepMonitorListener;
    }

    @Bean(JOB_NAME)
    public Job productRankingAggregationJob(
        @Qualifier(VALIDATE_STEP_NAME) Step validateProductRankingAggregationParametersStep,
        @Qualifier(CLEANUP_STEP_NAME) Step cleanupProductRankMvStep,
        @Qualifier(AGGREGATE_STEP_NAME) Step aggregateProductRankStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(validateProductRankingAggregationParametersStep)
            .next(cleanupProductRankMvStep)
            .next(aggregateProductRankStep)
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(VALIDATE_STEP_NAME)
    public Step validateProductRankingAggregationParametersStep(
        @Qualifier("validateProductRankingParametersTasklet") Tasklet validateProductRankingParametersTasklet
    ) {
        return new StepBuilder(VALIDATE_STEP_NAME, jobRepository)
            .tasklet(validateProductRankingParametersTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean
    public Tasklet validateProductRankingParametersTasklet(
        @Value("#{jobParameters['period']}") String period,
        @Value("#{jobParameters['baseDate']}") String baseDate
    ) {
        return (contribution, chunkContext) -> {
            ProductRankingJobParameters.of(period, baseDate);
            return RepeatStatus.FINISHED;
        };
    }

    @JobScope
    @Bean(CLEANUP_STEP_NAME)
    public Step cleanupProductRankMvStep(
        @Qualifier("cleanupProductRankMvTasklet") Tasklet cleanupProductRankMvTasklet
    ) {
        return new StepBuilder(CLEANUP_STEP_NAME, jobRepository)
            .tasklet(cleanupProductRankMvTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean
    public Tasklet cleanupProductRankMvTasklet(
        JdbcTemplate jdbcTemplate,
        @Value("#{jobParameters['period']}") String period,
        @Value("#{jobParameters['baseDate']}") String baseDate
    ) {
        return (contribution, chunkContext) -> {
            ProductRankingJobParameters parameters = ProductRankingJobParameters.of(period, baseDate);
            ProductRankingPeriodRange range = parameters.range();
            jdbcTemplate.update(
                "delete from " + parameters.period().tableName()
                    + " where period_start_date = ? and period_end_date = ?",
                Date.valueOf(range.startDate()),
                Date.valueOf(range.endDate())
            );
            return RepeatStatus.FINISHED;
        };
    }

    @JobScope
    @Bean(AGGREGATE_STEP_NAME)
    public Step aggregateProductRankStep(
        JdbcCursorItemReader<ProductMetricsAggregate> productRankingMetricsReader,
        ItemProcessor<ProductMetricsAggregate, ProductRankItem> productRankingItemProcessor,
        JdbcBatchItemWriter<ProductRankItem> productRankMvWriter
    ) {
        return new StepBuilder(AGGREGATE_STEP_NAME, jobRepository)
            .<ProductMetricsAggregate, ProductRankItem>chunk(CHUNK_SIZE, transactionManager)
            .reader(productRankingMetricsReader)
            .processor(productRankingItemProcessor)
            .writer(productRankMvWriter)
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductMetricsAggregate> productRankingMetricsReader(
        DataSource dataSource,
        @Value("#{jobParameters['period']}") String period,
        @Value("#{jobParameters['baseDate']}") String baseDate
    ) {
        ProductRankingJobParameters parameters = ProductRankingJobParameters.of(period, baseDate);
        ProductRankingPeriodRange range = parameters.range();
        return new JdbcCursorItemReaderBuilder<ProductMetricsAggregate>()
            .name("productRankingMetricsReader")
            .dataSource(dataSource)
            .sql("""
                select
                    product_id,
                    sum(view_count) as view_count,
                    sum(like_count) as like_count,
                    sum(sales_amount) as sales_amount,
                    (sum(view_count) * 0.1 + sum(like_count) * 0.2 + sum(sales_amount) * 0.6) as score
                from product_metrics
                where metric_date between ? and ?
                group by product_id
                order by score desc, product_id asc
                limit 100
                """)
            .preparedStatementSetter(preparedStatement -> {
                preparedStatement.setDate(1, Date.valueOf(range.startDate()));
                preparedStatement.setDate(2, Date.valueOf(range.endDate()));
            })
            .rowMapper((resultSet, rowNum) -> new ProductMetricsAggregate(
                resultSet.getLong("product_id"),
                resultSet.getLong("view_count"),
                resultSet.getLong("like_count"),
                resultSet.getLong("sales_amount"),
                resultSet.getDouble("score")
            ))
            .build();
    }

    @StepScope
    @Bean
    public ItemProcessor<ProductMetricsAggregate, ProductRankItem> productRankingItemProcessor(
        @Value("#{jobParameters['period']}") String period,
        @Value("#{jobParameters['baseDate']}") String baseDate
    ) {
        ProductRankingJobParameters parameters = ProductRankingJobParameters.of(period, baseDate);
        ProductRankingPeriodRange range = parameters.range();
        AtomicLong rank = new AtomicLong(1L);
        return item -> new ProductRankItem(
            range.startDate(),
            range.endDate(),
            rank.getAndIncrement(),
            item.productId(),
            item.score()
        );
    }

    @StepScope
    @Bean
    public JdbcBatchItemWriter<ProductRankItem> productRankMvWriter(
        DataSource dataSource,
        @Value("#{jobParameters['period']}") String period
    ) {
        ProductRankingPeriod rankingPeriod = ProductRankingPeriod.from(period);
        return new JdbcBatchItemWriterBuilder<ProductRankItem>()
            .dataSource(dataSource)
            .sql("""
                insert into %s (
                    period_start_date, period_end_date, `rank`, product_id, score,
                    created_at, updated_at
                ) values (
                    :periodStartDate, :periodEndDate, :rank, :productId, :score,
                    now(), now()
                )
                """.formatted(rankingPeriod.tableName()))
            .itemSqlParameterSourceProvider(item -> new MapSqlParameterSource()
                .addValue("periodStartDate", Date.valueOf(item.periodStartDate()))
                .addValue("periodEndDate", Date.valueOf(item.periodEndDate()))
                .addValue("rank", item.rank())
                .addValue("productId", item.productId())
                .addValue("score", item.score()))
            .build();
    }
}
