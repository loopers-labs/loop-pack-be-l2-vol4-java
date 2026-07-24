package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScoreFormula;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;

final class PeriodRankingJobFactory {
    private final RankingPeriod period;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final StepMonitorListener stepMonitorListener;
    private final DataSource dataSource;

    PeriodRankingJobFactory(
        RankingPeriod period,
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        StepMonitorListener stepMonitorListener,
        DataSource dataSource
    ) {
        this.period = period;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.stepMonitorListener = stepMonitorListener;
        this.dataSource = dataSource;
    }

    Job job(
        String jobName,
        JobListener jobListener,
        PeriodRankingExecutionListener executionListener,
        Clock clock,
        Step prepareStep,
        Step aggregateStep,
        Step publishStep,
        Step cleanupStep
    ) {
        return new JobBuilder(jobName, jobRepository)
            .validator(new PeriodRankingJobParametersValidator(period, clock))
            .start(prepareStep)
            .next(aggregateStep)
            .next(publishStep)
            .next(cleanupStep)
            .listener(jobListener)
            .listener(executionListener)
            .build();
    }

    Step taskletStep(String stepName, Tasklet tasklet) {
        return new StepBuilder(stepName, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    Step prepareStep(String stepName, Tasklet tasklet) {
        DefaultTransactionAttribute transactionAttribute = new DefaultTransactionAttribute();
        transactionAttribute.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return new StepBuilder(stepName, jobRepository)
            .tasklet(tasklet, transactionManager)
            .transactionAttribute(transactionAttribute)
            .listener(stepMonitorListener)
            .build();
    }

    Step aggregateStep(
        String stepName,
        JdbcPagingItemReader<PeriodRankingMetric> reader,
        JdbcBatchItemWriter<PeriodRankingMetric> writer,
        PeriodRankingLeaseRenewalListener leaseRenewalListener
    ) {
        ItemProcessor<PeriodRankingMetric, PeriodRankingMetric> processor = metric -> metric.withScore(
            BigDecimal.valueOf(RankingScoreFormula.calculate(
                metric.viewCount(), metric.likeCount(), metric.salesCount()
            ))
        );
        return new StepBuilder(stepName, jobRepository)
            .<PeriodRankingMetric, PeriodRankingMetric>chunk(PeriodRankingJobSupport.CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .listener(stepMonitorListener)
            .listener(leaseRenewalListener)
            .build();
    }

    JdbcPagingItemReader<PeriodRankingMetric> stagingReader(String readerName, String runKey) {
        return new JdbcPagingItemReaderBuilder<PeriodRankingMetric>()
            .name(readerName)
            .dataSource(dataSource)
            .pageSize(PeriodRankingJobSupport.CHUNK_SIZE)
            .selectClause("SELECT product_id, view_count, like_count, sales_count")
            .fromClause("FROM product_rank_staging")
            .whereClause("WHERE run_key = :runKey")
            .sortKeys(Map.of("product_id", Order.ASCENDING))
            .parameterValues(Map.of("runKey", runKey))
            .rowMapper((rs, rowNum) -> new PeriodRankingMetric(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getLong("sales_count")
            ))
            .build();
    }

    JdbcBatchItemWriter<PeriodRankingMetric> scoreWriter(String runKey) {
        return new JdbcBatchItemWriterBuilder<PeriodRankingMetric>()
            .dataSource(dataSource)
            .sql("""
                UPDATE product_rank_staging
                SET score = ?, updated_at = NOW(6)
                WHERE run_key = ? AND product_id = ?
                """)
            .itemPreparedStatementSetter((metric, statement) -> {
                statement.setBigDecimal(1, metric.score());
                statement.setString(2, runKey);
                statement.setLong(3, metric.productId());
            })
            .assertUpdates(true)
            .build();
    }
}
