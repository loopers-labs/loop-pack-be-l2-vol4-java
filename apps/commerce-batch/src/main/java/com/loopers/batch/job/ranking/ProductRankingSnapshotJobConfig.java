package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.PrepareProductRankingTasklet;
import com.loopers.batch.job.ranking.step.PublishProductRankingTasklet;
import com.loopers.batch.job.ranking.step.ProductRankingCandidateWriter;
import com.loopers.batch.job.ranking.step.ProductRankingScoreProcessor;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.ranking.application.ProductMetricAggregate;
import com.loopers.ranking.application.RankingCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(
    name = "spring.batch.job.name",
    havingValue = ProductRankingSnapshotJobConfig.JOB_NAME
)
@RequiredArgsConstructor
@Configuration
public class ProductRankingSnapshotJobConfig {

    public static final String JOB_NAME = "productRankingSnapshotJob";
    public static final String PREPARE_STEP_NAME = "prepareProductRankingStep";
    public static final String CALCULATE_STEP_NAME = "calculateProductRankingScoresStep";
    public static final String PUBLISH_STEP_NAME = "publishProductRankingStep";
    public static final String PRODUCT_METRIC_AGGREGATE_READER_NAME =
        "productMetricAggregateReader";
    private static final int PAGE_SIZE = 1_000;
    private static final int RETRY_LIMIT = 3;
    private static final long RETRY_BACK_OFF_MILLIS = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ProductRankingSnapshotJobParameterValidator parameterValidator;
    private final PrepareProductRankingTasklet prepareTasklet;
    private final PublishProductRankingTasklet publishTasklet;

    @Bean(JOB_NAME)
    public Job productRankingSnapshotJob(
        @Qualifier(PREPARE_STEP_NAME) Step prepareStep,
        @Qualifier(CALCULATE_STEP_NAME) Step calculateStep,
        @Qualifier(PUBLISH_STEP_NAME) Step publishStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .validator(parameterValidator)
            .start(prepareStep)
            .next(calculateStep)
            .next(publishStep)
            .listener(jobListener)
            .build();
    }

    @Bean(PREPARE_STEP_NAME)
    public Step prepareProductRankingStep() {
        return new StepBuilder(PREPARE_STEP_NAME, jobRepository)
            .tasklet(prepareTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean(PUBLISH_STEP_NAME)
    public Step publishProductRankingStep() {
        return new StepBuilder(PUBLISH_STEP_NAME, jobRepository)
            .tasklet(publishTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean(CALCULATE_STEP_NAME)
    public Step calculateProductRankingScoresStep(
        @Qualifier(PRODUCT_METRIC_AGGREGATE_READER_NAME)
        JdbcPagingItemReader<ProductMetricAggregate> reader,
        ProductRankingScoreProcessor processor,
        ProductRankingCandidateWriter writer,
        @Value("${commerce.ranking.batch.chunk-size}") int chunkSize
    ) {
        RetryTemplate readRetryTemplate = transientDatabaseReadRetryTemplate();
        ItemReader<ProductMetricAggregate> retryingReader = () ->
            readRetryTemplate.execute(context -> reader.read());

        return new StepBuilder(CALCULATE_STEP_NAME, jobRepository)
            .<ProductMetricAggregate, RankingCandidate>chunk(chunkSize, transactionManager)
            .reader(retryingReader)
            .stream(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(0)
            .retry(PessimisticLockingFailureException.class)
            .retry(TransientDataAccessResourceException.class)
            .noRetry(QueryTimeoutException.class)
            .retryLimit(RETRY_LIMIT)
            .backOffPolicy(retryBackOffPolicy())
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean(PRODUCT_METRIC_AGGREGATE_READER_NAME)
    public JdbcPagingItemReader<ProductMetricAggregate> productMetricAggregateReader(
        DataSource dataSource,
        @Value("#{jobParameters['period']}") String period,
        @Value("#{jobParameters['aggregationEndDate']}") String aggregationEndDate,
        @Value("#{jobParameters['revision']}") Long revision
    ) {
        ProductRankingSnapshotJobParameters parameters =
            ProductRankingSnapshotJobParameters.from(period, aggregationEndDate, revision);

        return new JdbcPagingItemReaderBuilder<ProductMetricAggregate>()
            .name(PRODUCT_METRIC_AGGREGATE_READER_NAME)
            .dataSource(dataSource)
            .queryProvider(new ProductMetricAggregateKeysetQueryProvider())
            .parameterValues(Map.of(
                "periodStart", parameters.periodStart(),
                "aggregationEndDate", parameters.aggregationEndDate()
            ))
            .rowMapper((resultSet, rowNumber) -> new ProductMetricAggregate(
                resultSet.getLong("product_id"),
                resultSet.getLong("view_count"),
                resultSet.getLong("like_delta"),
                resultSet.getLong("order_amount")
            ))
            .pageSize(PAGE_SIZE)
            .fetchSize(PAGE_SIZE)
            .saveState(true)
            .build();
    }

    private FixedBackOffPolicy retryBackOffPolicy() {
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(RETRY_BACK_OFF_MILLIS);
        return backOffPolicy;
    }

    private RetryTemplate transientDatabaseReadRetryTemplate() {
        return RetryTemplate.builder()
            .maxAttempts(RETRY_LIMIT)
            .fixedBackoff(RETRY_BACK_OFF_MILLIS)
            .retryOn(List.of(
                PessimisticLockingFailureException.class,
                TransientDataAccessResourceException.class
            ))
            .build();
    }
}
