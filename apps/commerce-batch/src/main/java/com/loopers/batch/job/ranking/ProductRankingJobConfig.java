package com.loopers.batch.job.ranking;

import com.loopers.application.ranking.ProductMetricInput;
import com.loopers.application.ranking.ProductRankBatchRunRepository;
import com.loopers.application.ranking.ProductRankSnapshotService;
import com.loopers.application.ranking.ProductRankingJobParameterValidator;
import com.loopers.domain.ranking.ProductRankBatchRun;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.infrastructure.ranking.ProductMetricsBatchJpaEntity;
import com.loopers.infrastructure.ranking.ProductMetricsBatchJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class ProductRankingJobConfig {

    public static final String JOB_NAME = "productRankingAggregationJob";
    private static final String CREATE_BATCH_RUN_STEP = "createProductRankBatchRunStep";
    private static final String AGGREGATE_STEP = "aggregateProductRankStep";
    private static final String ACTIVATE_STEP = "activateProductRankSnapshotStep";
    private static final int CHUNK_SIZE = 1_000;
    private static final DateTimeFormatter DATE_KEY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ProductRankingJobParameterValidator parameterValidator;
    private final ProductRankBatchRunRepository batchRunRepository;
    private final ProductRankSnapshotService snapshotService;

    @Bean(JOB_NAME)
    public Job productRankingAggregationJob(
        @Qualifier(CREATE_BATCH_RUN_STEP) Step createBatchRunStep,
        @Qualifier(AGGREGATE_STEP) Step aggregateProductRankStep,
        @Qualifier(ACTIVATE_STEP) Step activateProductRankSnapshotStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .validator(parameterValidator)
            .start(createBatchRunStep)
            .next(aggregateProductRankStep)
            .next(activateProductRankSnapshotStep)
            .build();
    }

    @Bean(CREATE_BATCH_RUN_STEP)
    @JobScope
    public Step createBatchRunStep() {
        return new StepBuilder(CREATE_BATCH_RUN_STEP, jobRepository)
            .tasklet((contribution, chunkContext) -> {
                Map<String, Object> jobParameters = chunkContext.getStepContext().getJobParameters();
                RankingPeriod period = RankingPeriod.valueOf((String) jobParameters.get("period"));
                LocalDate rankStartDate = LocalDate.parse((String) jobParameters.get("startDate"), DATE_KEY_FORMATTER);
                LocalDate rankEndDate = LocalDate.parse((String) jobParameters.get("endDate"), DATE_KEY_FORMATTER);
                ProductRankBatchRun batchRun = batchRunRepository.create(period, rankStartDate, rankEndDate);

                StepExecution stepExecution = contribution.getStepExecution();
                stepExecution.getJobExecution().getExecutionContext().putLong("batchRunId", batchRun.getId());
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    @Bean(AGGREGATE_STEP)
    @JobScope
    public Step aggregateProductRankStep(
        RepositoryItemReader<ProductMetricsBatchJpaEntity> productMetricsItemReader,
        ItemProcessor<ProductMetricsBatchJpaEntity, ProductMetricInput> productMetricInputProcessor,
        ProductRankSnapshotItemWriter productRankSnapshotItemWriter
    ) {
        return new StepBuilder(AGGREGATE_STEP, jobRepository)
            .<ProductMetricsBatchJpaEntity, ProductMetricInput>chunk(CHUNK_SIZE, transactionManager)
            .reader(productMetricsItemReader)
            .processor(productMetricInputProcessor)
            .writer(productRankSnapshotItemWriter)
            .listener(productRankSnapshotItemWriter)
            .build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<ProductMetricsBatchJpaEntity> productMetricsItemReader(
        ProductMetricsBatchJpaRepository repository,
        @Value("#{jobParameters['startDate']}") String startDate,
        @Value("#{jobParameters['endDate']}") String endDate
    ) {
        return new RepositoryItemReaderBuilder<ProductMetricsBatchJpaEntity>()
            .name("productMetricsItemReader")
            .repository(repository)
            .methodName("findByMetricDateBetween")
            .arguments(List.of(
                LocalDate.parse(startDate, DATE_KEY_FORMATTER),
                LocalDate.parse(endDate, DATE_KEY_FORMATTER)
            ))
            .pageSize(CHUNK_SIZE)
            .sorts(Map.of("metricDate", Sort.Direction.ASC, "id", Sort.Direction.ASC))
            .build();
    }

    @Bean
    public ItemProcessor<ProductMetricsBatchJpaEntity, ProductMetricInput> productMetricInputProcessor() {
        return item -> new ProductMetricInput(
            item.getMetricDate(),
            item.getProductId(),
            item.getDailyRankingScore()
        );
    }

    @Bean(ACTIVATE_STEP)
    @JobScope
    public Step activateProductRankSnapshotStep() {
        return new StepBuilder(ACTIVATE_STEP, jobRepository)
            .tasklet((contribution, chunkContext) -> {
                Long batchRunId = contribution.getStepExecution()
                    .getJobExecution()
                    .getExecutionContext()
                    .getLong("batchRunId");
                snapshotService.activateSnapshot(batchRunId);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }
}
