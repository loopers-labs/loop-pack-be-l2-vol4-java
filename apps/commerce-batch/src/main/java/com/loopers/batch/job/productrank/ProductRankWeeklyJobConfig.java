package com.loopers.batch.job.productrank;

import com.loopers.batch.job.productrank.step.ProductRankMvCleanupTasklet;
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
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankWeeklyJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class ProductRankWeeklyJobConfig {
    public static final String JOB_NAME = "productRankWeeklyJob";

    private static final ProductRankMvTable TABLE = ProductRankMvTable.WEEKLY;
    private static final int WINDOW_DAYS = 7;
    private static final int CHUNK_SIZE = 500;
    private static final String CLEANUP_STEP_NAME = "productRankWeeklyCleanupStep";
    private static final String AGGREGATE_STEP_NAME = "productRankWeeklyStep";
    private static final String READER_NAME = "productRankWeeklyDailyMetricReader";

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final ProductRankScoreProcessor productRankScoreProcessor;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;

    @Bean(JOB_NAME)
    public Job productRankWeeklyJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(productRankWeeklyCleanupStep())
                .next(productRankWeeklyStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(CLEANUP_STEP_NAME)
    public Step productRankWeeklyCleanupStep() {
        return ProductRankJobSupport.cleanupStep(
                CLEANUP_STEP_NAME, jobRepository, transactionManager, stepMonitorListener, productRankMvCleanupTasklet(null));
    }

    @JobScope
    @Bean(AGGREGATE_STEP_NAME)
    public Step productRankWeeklyStep() {
        return ProductRankJobSupport.aggregateStep(
                AGGREGATE_STEP_NAME, jobRepository, transactionManager, CHUNK_SIZE,
                dailyMetricReader(null), productRankScoreProcessor, productRankMvUpsertWriter(null),
                stepMonitorListener, chunkListener);
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductMetricDailyRow> dailyMetricReader(
            @Value("#{jobParameters['requestDate']}") String requestDateText) {
        return ProductRankJobSupport.dailyMetricReader(
                READER_NAME, dataSource, LocalDate.parse(requestDateText), WINDOW_DAYS);
    }

    @StepScope
    @Bean
    public ProductRankMvUpsertWriter productRankMvUpsertWriter(
            @Value("#{jobParameters['requestDate']}") String requestDateText) {
        return new ProductRankMvUpsertWriter(jdbcTemplate, LocalDate.parse(requestDateText), TABLE);
    }

    @StepScope
    @Bean
    public ProductRankMvCleanupTasklet productRankMvCleanupTasklet(
            @Value("#{jobParameters['requestDate']}") String requestDateText) {
        return new ProductRankMvCleanupTasklet(jdbcTemplate, LocalDate.parse(requestDateText), TABLE);
    }
}
