package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.ranking.RankingPeriod;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyProductRankingJobConfig.JOB_NAME)
@Configuration
public class WeeklyProductRankingJobConfig {
    public static final String JOB_NAME = "weeklyProductRankingJob";
    private static final RankingPeriod PERIOD = RankingPeriod.WEEKLY;

    @Bean
    PeriodRankingJobFactory weeklyRankingJobFactory(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        StepMonitorListener listener,
        DataSource dataSource
    ) {
        return new PeriodRankingJobFactory(PERIOD, jobRepository, transactionManager, listener, dataSource);
    }

    @Bean(JOB_NAME)
    Job weeklyProductRankingJob(
        PeriodRankingJobFactory weeklyRankingJobFactory,
        JobListener jobListener,
        PeriodRankingExecutionListener weeklyRankingExecutionListener,
        Clock clock,
        Step weeklyRankingPrepareStep,
        Step weeklyRankingAggregateStep,
        Step weeklyRankingPublishStep,
        Step weeklyRankingCleanupStep
    ) {
        return weeklyRankingJobFactory.job(
            JOB_NAME,
            jobListener,
            weeklyRankingExecutionListener,
            clock,
            weeklyRankingPrepareStep,
            weeklyRankingAggregateStep,
            weeklyRankingPublishStep,
            weeklyRankingCleanupStep
        );
    }

    @Bean
    PeriodRankingLockManager weeklyRankingLockManager(
        JdbcTemplate jdbcTemplate,
        @Value("${batch.ranking.lock-lease-seconds:7200}") long leaseSeconds
    ) {
        return new PeriodRankingLockManager(jdbcTemplate, PERIOD, leaseSeconds);
    }

    @Bean
    PeriodRankingExecutionListener weeklyRankingExecutionListener(
        PeriodRankingLockManager weeklyRankingLockManager
    ) {
        return new PeriodRankingExecutionListener(weeklyRankingLockManager, PERIOD);
    }

    @Bean
    PeriodRankingLeaseRenewalListener weeklyRankingLeaseRenewalListener(
        PeriodRankingLockManager weeklyRankingLockManager
    ) {
        return new PeriodRankingLeaseRenewalListener(weeklyRankingLockManager, PERIOD);
    }

    @Bean
    PeriodRankingPrepareTasklet weeklyRankingPrepareTasklet(
        JdbcTemplate jdbcTemplate,
        PeriodRankingLockManager weeklyRankingLockManager,
        @Value("${batch.ranking.staging-retention-days:7}") int retentionDays
    ) {
        return new PeriodRankingPrepareTasklet(jdbcTemplate, PERIOD, weeklyRankingLockManager, retentionDays);
    }

    @Bean
    PeriodRankingPublishTasklet weeklyRankingPublishTasklet(
        JdbcTemplate jdbcTemplate,
        PeriodRankingLockManager weeklyRankingLockManager
    ) {
        return new PeriodRankingPublishTasklet(
            jdbcTemplate,
            PERIOD,
            "mv_product_rank_weekly",
            weeklyRankingLockManager
        );
    }

    @Bean
    PeriodRankingStagingCleanupTasklet weeklyRankingStagingCleanupTasklet(JdbcTemplate jdbcTemplate) {
        return new PeriodRankingStagingCleanupTasklet(jdbcTemplate, PERIOD);
    }

    @Bean
    Step weeklyRankingPrepareStep(
        PeriodRankingJobFactory weeklyRankingJobFactory,
        PeriodRankingPrepareTasklet weeklyRankingPrepareTasklet
    ) {
        return weeklyRankingJobFactory.prepareStep("weeklyRankingPrepareStep", weeklyRankingPrepareTasklet);
    }

    @Bean
    Step weeklyRankingAggregateStep(
        PeriodRankingJobFactory weeklyRankingJobFactory,
        JdbcPagingItemReader<PeriodRankingMetric> weeklyRankingMetricReader,
        JdbcBatchItemWriter<PeriodRankingMetric> weeklyRankingStagingWriter,
        PeriodRankingLeaseRenewalListener weeklyRankingLeaseRenewalListener
    ) {
        return weeklyRankingJobFactory.aggregateStep(
            "weeklyRankingAggregateStep",
            weeklyRankingMetricReader,
            weeklyRankingStagingWriter,
            weeklyRankingLeaseRenewalListener
        );
    }

    @Bean
    Step weeklyRankingPublishStep(
        PeriodRankingJobFactory weeklyRankingJobFactory,
        PeriodRankingPublishTasklet weeklyRankingPublishTasklet
    ) {
        return weeklyRankingJobFactory.taskletStep("weeklyRankingPublishStep", weeklyRankingPublishTasklet);
    }

    @Bean
    Step weeklyRankingCleanupStep(
        PeriodRankingJobFactory weeklyRankingJobFactory,
        PeriodRankingStagingCleanupTasklet weeklyRankingStagingCleanupTasklet
    ) {
        return weeklyRankingJobFactory.taskletStep("weeklyRankingCleanupStep", weeklyRankingStagingCleanupTasklet);
    }

    @StepScope
    @Bean
    JdbcPagingItemReader<PeriodRankingMetric> weeklyRankingMetricReader(
        PeriodRankingJobFactory weeklyRankingJobFactory,
        @Value("#{jobParameters['requestDate']}") String requestDate,
        @Value("#{jobInstanceId}") Long jobInstanceId
    ) {
        LocalDate date = PeriodRankingJobSupport.requestDate(requestDate);
        return weeklyRankingJobFactory.stagingReader(
            "weeklyRankingMetricReader",
            PeriodRankingJobSupport.runKey(PERIOD, date, jobInstanceId)
        );
    }

    @StepScope
    @Bean
    JdbcBatchItemWriter<PeriodRankingMetric> weeklyRankingStagingWriter(
        PeriodRankingJobFactory weeklyRankingJobFactory,
        @Value("#{jobParameters['requestDate']}") String requestDate,
        @Value("#{jobInstanceId}") Long jobInstanceId
    ) {
        LocalDate date = PeriodRankingJobSupport.requestDate(requestDate);
        return weeklyRankingJobFactory.scoreWriter(
            PeriodRankingJobSupport.runKey(PERIOD, date, jobInstanceId)
        );
    }
}
