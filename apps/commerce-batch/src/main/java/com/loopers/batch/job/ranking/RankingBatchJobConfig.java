package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.RankingPublishTasklet;
import com.loopers.batch.job.ranking.step.RankingStagingCleanupTasklet;
import com.loopers.batch.job.ranking.step.RankingTop100FlushListener;
import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import com.loopers.domain.ranking.batch.RankingDailyMetricsAggregate;
import com.loopers.domain.ranking.batch.RankingMvScoreCalculator;
import com.loopers.domain.ranking.batch.RankingScoreCandidate;
import com.loopers.domain.ranking.batch.RankingTop100Accumulator;
import com.loopers.infrastructure.ranking.batch.ProductDailyMetricsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingBatchJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingBatchJobConfig {

    public static final String JOB_NAME = "rankingProductMvJob";
    private static final String STEP_STAGING_CLEANUP = "stagingCleanupStep";
    private static final String STEP_AGGREGATE = "aggregateStep";
    private static final String STEP_PUBLISH = "publishStep";
    private static final int CHUNK_SIZE = 50;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final ChunkListener chunkListener;
    private final StepMonitorListener stepMonitorListener;
    private final RankingBatchJobParametersValidator jobParametersValidator;
    private final RankingBatchJobMetricsListener jobMetricsListener;
    private final RankingBatchLockListener lockListener;
    private final RankingStagingCleanupTasklet stagingCleanupTasklet;
    private final RankingPublishTasklet publishTasklet;

    @Bean(JOB_NAME)
    public Job rankingProductMvJob(
        @Qualifier(STEP_STAGING_CLEANUP) Step stagingCleanupStep,
        @Qualifier(STEP_AGGREGATE) Step aggregateStep,
        @Qualifier(STEP_PUBLISH) Step publishStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .validator(jobParametersValidator)
            .listener(lockListener)
            .listener(jobListener)
            .listener(jobMetricsListener)
            .start(stagingCleanupStep)
            .next(aggregateStep)
            .next(publishStep)
            .build();
    }

    @Bean(STEP_STAGING_CLEANUP)
    public Step stagingCleanupStep() {
        return new StepBuilder(STEP_STAGING_CLEANUP, jobRepository)
            .tasklet(stagingCleanupTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean(STEP_AGGREGATE)
    public Step aggregateStep(
        RepositoryItemReader<RankingDailyMetricsAggregate> aggregateReader,
        ItemProcessor<RankingDailyMetricsAggregate, RankingScoreCandidate> scoreProcessor,
        ItemWriter<RankingScoreCandidate> top100Writer,
        RankingTop100FlushListener top100FlushListener
    ) {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
            .<RankingDailyMetricsAggregate, RankingScoreCandidate>chunk(CHUNK_SIZE, transactionManager)
            .reader(aggregateReader)
            .processor(scoreProcessor)
            .writer(top100Writer)
            .listener(top100FlushListener)
            .listener(chunkListener)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean(STEP_PUBLISH)
    public Step publishStep() {
        return new StepBuilder(STEP_PUBLISH, jobRepository)
            .tasklet(publishTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean
    public RepositoryItemReader<RankingDailyMetricsAggregate> aggregateReader(
        ProductDailyMetricsJpaRepository productDailyMetricsJpaRepository,
        @Value("#{jobParameters['period']}") String period,
        @Value("#{jobParameters['periodKey']}") String periodKey
    ) {
        RankingBatchJobParameters parameters = RankingBatchJobParameters.of(period, periodKey);
        return new RepositoryItemReaderBuilder<RankingDailyMetricsAggregate>()
            .name("rankingDailyMetricsAggregateReader")
            .repository(productDailyMetricsJpaRepository)
            .methodName("aggregateByDateRange")
            .arguments(List.of(parameters.startDate(), parameters.endDate()))
            .pageSize(CHUNK_SIZE)
            .sorts(Map.of("productId", Sort.Direction.ASC))
            .build();
    }

    @Bean
    public ItemProcessor<RankingDailyMetricsAggregate, RankingScoreCandidate> scoreProcessor(
        RankingMvScoreCalculator scoreCalculator
    ) {
        return scoreCalculator::calculate;
    }

    @StepScope
    @Bean
    public RankingTop100Accumulator rankingTop100Accumulator() {
        return new RankingTop100Accumulator();
    }

    @Bean
    public ItemWriter<RankingScoreCandidate> top100Writer(RankingTop100Accumulator accumulator) {
        return chunk -> chunk.getItems().forEach(accumulator::add);
    }
}
