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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyProductRankingJobConfig.JOB_NAME)
@Configuration
public class MonthlyProductRankingJobConfig {
    public static final String JOB_NAME = "monthlyProductRankingJob";
    private static final RankingPeriod PERIOD = RankingPeriod.MONTHLY;

    @Bean
    PeriodRankingJobFactory monthlyRankingJobFactory(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        StepMonitorListener listener,
        DataSource dataSource
    ) {
        return new PeriodRankingJobFactory(PERIOD, jobRepository, transactionManager, listener, dataSource);
    }

    @Bean(JOB_NAME)
    Job monthlyProductRankingJob(
        PeriodRankingJobFactory monthlyRankingJobFactory,
        JobListener jobListener,
        PeriodRankingExecutionListener monthlyRankingExecutionListener,
        Clock clock,
        Step monthlyRankingPrepareStep,
        Step monthlyRankingAggregateStep,
        Step monthlyRankingPublishStep,
        Step monthlyRankingCleanupStep
    ) {
        return monthlyRankingJobFactory.job(
            JOB_NAME,
            jobListener,
            monthlyRankingExecutionListener,
            clock,
            monthlyRankingPrepareStep,
            monthlyRankingAggregateStep,
            monthlyRankingPublishStep,
            monthlyRankingCleanupStep
        );
    }

    @Bean
    PeriodRankingLockManager monthlyRankingLockManager(
        JdbcTemplate jdbcTemplate,
        @Value("${batch.ranking.lock-lease-seconds:7200}") long leaseSeconds
    ) {
        return new PeriodRankingLockManager(jdbcTemplate, PERIOD, leaseSeconds);
    }

    @Bean
    PeriodRankingExecutionListener monthlyRankingExecutionListener(
        PeriodRankingLockManager monthlyRankingLockManager
    ) {
        return new PeriodRankingExecutionListener(monthlyRankingLockManager, PERIOD);
    }

    @Bean
    PeriodRankingLeaseRenewalListener monthlyRankingLeaseRenewalListener(
        PeriodRankingLockManager monthlyRankingLockManager
    ) {
        return new PeriodRankingLeaseRenewalListener(monthlyRankingLockManager, PERIOD);
    }

    @Bean
    PeriodRankingPrepareTasklet monthlyRankingPrepareTasklet(
        JdbcTemplate jdbcTemplate,
        PeriodRankingLockManager monthlyRankingLockManager,
        @Value("${batch.ranking.staging-retention-days:7}") int retentionDays
    ) {
        return new PeriodRankingPrepareTasklet(jdbcTemplate, PERIOD, monthlyRankingLockManager, retentionDays);
    }

    @Bean
    PeriodRankingPublishTasklet monthlyRankingPublishTasklet(
        JdbcTemplate jdbcTemplate,
        PeriodRankingLockManager monthlyRankingLockManager
    ) {
        return new PeriodRankingPublishTasklet(
            jdbcTemplate,
            PERIOD,
            "mv_product_rank_monthly",
            monthlyRankingLockManager
        );
    }

    @Bean
    PeriodRankingStagingCleanupTasklet monthlyRankingStagingCleanupTasklet(JdbcTemplate jdbcTemplate) {
        return new PeriodRankingStagingCleanupTasklet(jdbcTemplate, PERIOD);
    }

    @Bean
    Step monthlyRankingPrepareStep(
        PeriodRankingJobFactory monthlyRankingJobFactory,
        PeriodRankingPrepareTasklet monthlyRankingPrepareTasklet
    ) {
        return monthlyRankingJobFactory.prepareStep("monthlyRankingPrepareStep", monthlyRankingPrepareTasklet);
    }

    @Bean
    Step monthlyRankingAggregateStep(
        PeriodRankingJobFactory monthlyRankingJobFactory,
        JdbcPagingItemReader<PeriodRankingMetric> monthlyRankingMetricReader,
        JdbcBatchItemWriter<PeriodRankingMetric> monthlyRankingStagingWriter,
        PeriodRankingLeaseRenewalListener monthlyRankingLeaseRenewalListener
    ) {
        return monthlyRankingJobFactory.aggregateStep(
            "monthlyRankingAggregateStep",
            monthlyRankingMetricReader,
            monthlyRankingStagingWriter,
            monthlyRankingLeaseRenewalListener
        );
    }

    @Bean
    Step monthlyRankingPublishStep(
        PeriodRankingJobFactory monthlyRankingJobFactory,
        PeriodRankingPublishTasklet monthlyRankingPublishTasklet
    ) {
        return monthlyRankingJobFactory.taskletStep("monthlyRankingPublishStep", monthlyRankingPublishTasklet);
    }

    @Bean
    Step monthlyRankingCleanupStep(
        PeriodRankingJobFactory monthlyRankingJobFactory,
        PeriodRankingStagingCleanupTasklet monthlyRankingStagingCleanupTasklet
    ) {
        return monthlyRankingJobFactory.taskletStep("monthlyRankingCleanupStep", monthlyRankingStagingCleanupTasklet);
    }

    @StepScope
    @Bean
    JdbcPagingItemReader<PeriodRankingMetric> monthlyRankingMetricReader(
        PeriodRankingJobFactory monthlyRankingJobFactory,
        @Value("#{jobParameters['requestDate']}") String requestDate,
        @Value("#{jobInstanceId}") Long jobInstanceId
    ) {
        LocalDate date = PeriodRankingJobSupport.requestDate(requestDate);
        return monthlyRankingJobFactory.stagingReader(
            "monthlyRankingMetricReader",
            PeriodRankingJobSupport.runKey(PERIOD, date, jobInstanceId)
        );
    }

    @StepScope
    @Bean
    JdbcBatchItemWriter<PeriodRankingMetric> monthlyRankingStagingWriter(
        PeriodRankingJobFactory monthlyRankingJobFactory,
        @Value("#{jobParameters['requestDate']}") String requestDate,
        @Value("#{jobInstanceId}") Long jobInstanceId
    ) {
        LocalDate date = PeriodRankingJobSupport.requestDate(requestDate);
        return monthlyRankingJobFactory.scoreWriter(
            PeriodRankingJobSupport.runKey(PERIOD, date, jobInstanceId)
        );
    }
}
