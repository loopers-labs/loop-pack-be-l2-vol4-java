package com.loopers.batch.job.productranking;

import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = "spring.batch.job.name",
    havingValue = ProductRankingAggregationJobConfig.JOB_NAME)
public class ProductRankingAggregationJobConfig {

  public static final String JOB_NAME = "productRankingAggregationJob";
  public static final int DEFAULT_CHUNK_SIZE = 100;

  private static final String STAGING_INITIALIZATION_STEP =
      "productRankingStagingInitializationStep";
  private static final String WEEKLY_AGGREGATION_STEP = "weeklyProductRankingAggregationStep";
  private static final String WEEKLY_PUBLISH_STEP = "weeklyProductRankingPublishStep";
  private static final String MONTHLY_AGGREGATION_STEP = "monthlyProductRankingAggregationStep";
  private static final String MONTHLY_PUBLISH_STEP = "monthlyProductRankingPublishStep";

  private static final String STAGING_INITIALIZATION_TASKLET =
      "productRankingStagingInitializationTasklet";
  private static final String WEEKLY_READER = "weeklyProductMetricsReader";
  private static final String WEEKLY_WRITER = "weeklyProductRankingStagingWriter";
  private static final String WEEKLY_PUBLISH_TASKLET = "weeklyProductRankingPublishTasklet";
  private static final String MONTHLY_READER = "monthlyProductMetricsReader";
  private static final String MONTHLY_WRITER = "monthlyProductRankingStagingWriter";
  private static final String MONTHLY_PUBLISH_TASKLET = "monthlyProductRankingPublishTasklet";

  @Bean(JOB_NAME)
  public Job productRankingAggregationJob(
      JobRepository jobRepository,
      JobListener jobListener,
      @Qualifier(STAGING_INITIALIZATION_STEP) Step stagingInitializationStep,
      @Qualifier(WEEKLY_AGGREGATION_STEP) Step weeklyAggregationStep,
      @Qualifier(WEEKLY_PUBLISH_STEP) Step weeklyPublishStep,
      @Qualifier(MONTHLY_AGGREGATION_STEP) Step monthlyAggregationStep,
      @Qualifier(MONTHLY_PUBLISH_STEP) Step monthlyPublishStep) {
    return new JobBuilder(JOB_NAME, jobRepository)
        .validator(new ProductRankingJobParametersValidator())
        .start(stagingInitializationStep)
        .next(weeklyAggregationStep)
        .next(weeklyPublishStep)
        .next(monthlyAggregationStep)
        .next(monthlyPublishStep)
        .listener(jobListener)
        .build();
  }

  @Bean(STAGING_INITIALIZATION_STEP)
  public Step stagingInitializationStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      StepMonitorListener stepMonitorListener,
      @Qualifier(STAGING_INITIALIZATION_TASKLET)
          StagingInitializationTasklet stagingInitializationTasklet) {
    return new StepBuilder(STAGING_INITIALIZATION_STEP, jobRepository)
        .tasklet(stagingInitializationTasklet, transactionManager)
        .listener(stepMonitorListener)
        .build();
  }

  @Bean(WEEKLY_AGGREGATION_STEP)
  public Step weeklyAggregationStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ProductRankingBatchSettings batchSettings,
      StepMonitorListener stepMonitorListener,
      ChunkListener chunkListener,
      @Qualifier(WEEKLY_READER) JdbcPagingItemReader<ProductMetricItem> reader,
      ProductRankingScoreProcessor processor,
      @Qualifier(WEEKLY_WRITER) JdbcBatchItemWriter<ProductRankingScore> writer) {
    return new StepBuilder(WEEKLY_AGGREGATION_STEP, jobRepository)
        .<ProductMetricItem, ProductRankingScore>chunk(
            batchSettings.chunkSize(), transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .listener(stepMonitorListener)
        .listener(chunkListener)
        .build();
  }

  @Bean(WEEKLY_PUBLISH_STEP)
  public Step weeklyPublishStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      StepMonitorListener stepMonitorListener,
      @Qualifier(WEEKLY_PUBLISH_TASKLET) RankingSnapshotPublishTasklet publishTasklet) {
    return new StepBuilder(WEEKLY_PUBLISH_STEP, jobRepository)
        .tasklet(publishTasklet, transactionManager)
        .listener(stepMonitorListener)
        .build();
  }

  @Bean(MONTHLY_AGGREGATION_STEP)
  public Step monthlyAggregationStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ProductRankingBatchSettings batchSettings,
      StepMonitorListener stepMonitorListener,
      ChunkListener chunkListener,
      @Qualifier(MONTHLY_READER) JdbcPagingItemReader<ProductMetricItem> reader,
      ProductRankingScoreProcessor processor,
      @Qualifier(MONTHLY_WRITER) JdbcBatchItemWriter<ProductRankingScore> writer) {
    return new StepBuilder(MONTHLY_AGGREGATION_STEP, jobRepository)
        .<ProductMetricItem, ProductRankingScore>chunk(
            batchSettings.chunkSize(), transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .listener(stepMonitorListener)
        .listener(chunkListener)
        .build();
  }

  @Bean(MONTHLY_PUBLISH_STEP)
  public Step monthlyPublishStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      StepMonitorListener stepMonitorListener,
      @Qualifier(MONTHLY_PUBLISH_TASKLET) RankingSnapshotPublishTasklet publishTasklet) {
    return new StepBuilder(MONTHLY_PUBLISH_STEP, jobRepository)
        .tasklet(publishTasklet, transactionManager)
        .listener(stepMonitorListener)
        .build();
  }

  @Bean
  public ProductRankingScoreProcessor productRankingScoreProcessor() {
    return new ProductRankingScoreProcessor();
  }

  @Bean
  public ProductRankingBatchSettings productRankingBatchSettings(
      @Value("${product-ranking.batch.chunk-size:" + DEFAULT_CHUNK_SIZE + "}") int chunkSize) {
    return new ProductRankingBatchSettings(chunkSize);
  }

  @Bean(STAGING_INITIALIZATION_TASKLET)
  @StepScope
  public StagingInitializationTasklet stagingInitializationTasklet(
      DataSource dataSource, @Value("#{jobInstanceId}") Long jobInstanceId) {
    return new StagingInitializationTasklet(new JdbcTemplate(dataSource), jobInstanceId);
  }

  @Bean(WEEKLY_READER)
  @StepScope
  public JdbcPagingItemReader<ProductMetricItem> weeklyProductMetricsReader(
      DataSource dataSource,
      ProductRankingBatchSettings batchSettings,
      @Value("#{jobParameters['targetDate']}") String targetDateValue)
      throws Exception {
    LocalDate targetDate = ProductRankingJobParametersValidator.parseTargetDate(targetDateValue);
    return productMetricsReader(
        dataSource,
        ProductRankingPeriod.WEEKLY,
        targetDate,
        WEEKLY_READER,
        batchSettings.chunkSize());
  }

  @Bean(WEEKLY_WRITER)
  @StepScope
  public JdbcBatchItemWriter<ProductRankingScore> weeklyProductRankingStagingWriter(
      DataSource dataSource, @Value("#{jobInstanceId}") Long jobInstanceId) {
    return productRankingStagingWriter(dataSource, jobInstanceId, ProductRankingPeriod.WEEKLY);
  }

  @Bean(WEEKLY_PUBLISH_TASKLET)
  @StepScope
  public RankingSnapshotPublishTasklet weeklyProductRankingPublishTasklet(
      DataSource dataSource,
      @Value("#{jobInstanceId}") Long jobInstanceId,
      @Value("#{jobParameters['targetDate']}") String targetDateValue)
      throws Exception {
    return new RankingSnapshotPublishTasklet(
        new JdbcTemplate(dataSource),
        jobInstanceId,
        ProductRankingPeriod.WEEKLY,
        ProductRankingJobParametersValidator.parseTargetDate(targetDateValue));
  }

  @Bean(MONTHLY_READER)
  @StepScope
  public JdbcPagingItemReader<ProductMetricItem> monthlyProductMetricsReader(
      DataSource dataSource,
      ProductRankingBatchSettings batchSettings,
      @Value("#{jobParameters['targetDate']}") String targetDateValue)
      throws Exception {
    LocalDate targetDate = ProductRankingJobParametersValidator.parseTargetDate(targetDateValue);
    return productMetricsReader(
        dataSource,
        ProductRankingPeriod.MONTHLY,
        targetDate,
        MONTHLY_READER,
        batchSettings.chunkSize());
  }

  @Bean(MONTHLY_WRITER)
  @StepScope
  public JdbcBatchItemWriter<ProductRankingScore> monthlyProductRankingStagingWriter(
      DataSource dataSource, @Value("#{jobInstanceId}") Long jobInstanceId) {
    return productRankingStagingWriter(dataSource, jobInstanceId, ProductRankingPeriod.MONTHLY);
  }

  @Bean(MONTHLY_PUBLISH_TASKLET)
  @StepScope
  public RankingSnapshotPublishTasklet monthlyProductRankingPublishTasklet(
      DataSource dataSource,
      @Value("#{jobInstanceId}") Long jobInstanceId,
      @Value("#{jobParameters['targetDate']}") String targetDateValue)
      throws Exception {
    return new RankingSnapshotPublishTasklet(
        new JdbcTemplate(dataSource),
        jobInstanceId,
        ProductRankingPeriod.MONTHLY,
        ProductRankingJobParametersValidator.parseTargetDate(targetDateValue));
  }

  private JdbcPagingItemReader<ProductMetricItem> productMetricsReader(
      DataSource dataSource,
      ProductRankingPeriod period,
      LocalDate targetDate,
      String readerName,
      int chunkSize) {
    Map<String, Order> sortKeys = new LinkedHashMap<>();
    sortKeys.put("metric_date", Order.ASCENDING);
    sortKeys.put("product_id", Order.ASCENDING);

    return new JdbcPagingItemReaderBuilder<ProductMetricItem>()
        .name(readerName)
        .dataSource(dataSource)
        .selectClause("SELECT metric_date, product_id, view_count, like_count, order_count")
        .fromClause("FROM product_metrics")
        .whereClause("WHERE metric_date BETWEEN :periodStartDate AND :periodEndDate")
        .parameterValues(
            Map.of(
                "periodStartDate", Date.valueOf(period.startDate(targetDate)),
                "periodEndDate", Date.valueOf(targetDate)))
        .sortKeys(sortKeys)
        .pageSize(chunkSize)
        .fetchSize(chunkSize)
        .saveState(true)
        .rowMapper(
            (resultSet, rowNumber) ->
                new ProductMetricItem(
                    resultSet.getDate("metric_date").toLocalDate(),
                    resultSet.getLong("product_id"),
                    resultSet.getLong("view_count"),
                    resultSet.getLong("like_count"),
                    resultSet.getLong("order_count")))
        .build();
  }

  private JdbcBatchItemWriter<ProductRankingScore> productRankingStagingWriter(
      DataSource dataSource, long jobInstanceId, ProductRankingPeriod period) {
    return new JdbcBatchItemWriterBuilder<ProductRankingScore>()
        .dataSource(dataSource)
        .sql(
            """
            INSERT INTO stg_product_rank_aggregation (
                job_instance_id,
                period_type,
                product_id,
                score,
                created_at,
                updated_at
            ) VALUES (
                :jobInstanceId,
                :periodType,
                :productId,
                :score,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                score = stg_product_rank_aggregation.score + VALUES(score),
                updated_at = CURRENT_TIMESTAMP(6)
            """)
        .itemSqlParameterSourceProvider(
            item ->
                new MapSqlParameterSource()
                    .addValue("jobInstanceId", jobInstanceId)
                    .addValue("periodType", period.name())
                    .addValue("productId", item.productId())
                    .addValue("score", item.score()))
        .build();
  }
}
