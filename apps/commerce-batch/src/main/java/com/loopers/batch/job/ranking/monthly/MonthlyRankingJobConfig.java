package com.loopers.batch.job.ranking.monthly;

import com.loopers.batch.job.ranking.ProductMetricsSummary;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MvActiveVersionEntity;
import com.loopers.domain.ranking.MvProductRankMonthlyEntity;
import com.loopers.domain.ranking.RankingWeightEntity;
import com.loopers.infrastructure.ranking.MvActiveVersionJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.RankingWeightJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final int CHUNK_SIZE = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager txManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final MvProductRankMonthlyJpaRepository monthlyRepository;
    private final MvActiveVersionJpaRepository activeVersionRepository;
    private final RankingWeightJpaRepository rankingWeightRepository;
    private final JdbcTemplate jdbcTemplate;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(monthlyAggregationStep())
            .next(monthlyRankStep())
            .next(monthlySwapStep())
            .next(monthlyOldBatchCleanupStep())
            .listener(jobListener)
            .build();
    }

    @Bean("monthlyAggregationStep")
    public Step monthlyAggregationStep() {
        return new StepBuilder("monthlyAggregationStep", jobRepository)
            .<ProductMetricsSummary, MvProductRankMonthlyEntity>chunk(CHUNK_SIZE, txManager)
            .reader(monthlyRankingReader(null, null))
            .processor(monthlyRankingProcessor(null, null))
            .writer(monthlyRankingWriter())
            .listener(stepMonitorListener)
            .build();
    }

    @Bean("monthlyRankStep")
    public Step monthlyRankStep() {
        return new StepBuilder("monthlyRankStep", jobRepository)
            .tasklet(monthlyRankTasklet(null, null), txManager)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean("monthlySwapStep")
    public Step monthlySwapStep() {
        return new StepBuilder("monthlySwapStep", jobRepository)
            .tasklet(monthlySwapTasklet(null, null), txManager)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean("monthlyOldBatchCleanupStep")
    public Step monthlyOldBatchCleanupStep() {
        return new StepBuilder("monthlyOldBatchCleanupStep", jobRepository)
            .tasklet(monthlyOldBatchCleanupTasklet(null, null), txManager)
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean("monthlyRankingReader")
    public JdbcCursorItemReader<ProductMetricsSummary> monthlyRankingReader(
        DataSource dataSource,
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate date  = LocalDate.parse(targetDate, DATE_FORMAT);
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end   = date;

        return new JdbcCursorItemReaderBuilder<ProductMetricsSummary>()
            .name("monthlyRankingReader")
            .dataSource(dataSource)
            .sql("""
                SELECT product_id,
                       SUM(like_count)  AS like_count,
                       SUM(view_count)  AS view_count,
                       SUM(sales_count) AS sales_count
                FROM product_metrics
                WHERE date BETWEEN ? AND ?
                GROUP BY product_id
                """)
            .preparedStatementSetter(ps -> {
                ps.setDate(1, Date.valueOf(start));
                ps.setDate(2, Date.valueOf(end));
            })
            .rowMapper((rs, rowNum) -> {
                byte[] bytes = rs.getBytes("product_id");
                ByteBuffer bb = ByteBuffer.wrap(bytes);
                UUID productId = new UUID(bb.getLong(), bb.getLong());
                return new ProductMetricsSummary(
                    productId,
                    rs.getLong("like_count"),
                    rs.getLong("view_count"),
                    rs.getLong("sales_count")
                );
            })
            .build();
    }

    @StepScope
    @Bean("monthlyRankingProcessor")
    public ItemProcessor<ProductMetricsSummary, MvProductRankMonthlyEntity> monthlyRankingProcessor(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['run.id']}") Long batchId
    ) {
        int yearMonth  = toYearMonth(LocalDate.parse(targetDate, DATE_FORMAT));
        double viewWeight  = getWeight("VIEW",  0.1);
        double likeWeight  = getWeight("LIKE",  0.2);
        double orderWeight = getWeight("ORDER", 0.7);

        return item -> {
            double score = item.likeCount()  * likeWeight
                         + item.viewCount()  * viewWeight
                         + item.salesCount() * orderWeight;
            return new MvProductRankMonthlyEntity(item.productId(), 0, score, yearMonth, batchId);
        };
    }

    @Bean("monthlyRankingWriter")
    public ItemWriter<MvProductRankMonthlyEntity> monthlyRankingWriter() {
        return chunk -> monthlyRepository.saveAll(chunk.getItems());
    }

    @StepScope
    @Bean("monthlyRankTasklet")
    public Tasklet monthlyRankTasklet(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['run.id']}") Long batchId
    ) {
        return (contribution, chunkContext) -> {
            int yearMonth = toYearMonth(LocalDate.parse(targetDate, DATE_FORMAT));

            jdbcTemplate.update("""
                DELETE FROM mv_product_rank_monthly
                WHERE period_month = ? AND batch_id = ?
                  AND product_id NOT IN (
                      SELECT product_id FROM (
                          SELECT product_id
                          FROM mv_product_rank_monthly
                          WHERE period_month = ? AND batch_id = ?
                          ORDER BY score DESC
                          LIMIT 100
                      ) AS top100
                  )
                """, yearMonth, batchId, yearMonth, batchId);

            jdbcTemplate.update("""
                UPDATE mv_product_rank_monthly m
                JOIN (
                    SELECT product_id,
                           ROW_NUMBER() OVER (ORDER BY score DESC) AS rn
                    FROM mv_product_rank_monthly
                    WHERE period_month = ? AND batch_id = ?
                ) r ON m.product_id = r.product_id AND m.period_month = ? AND m.batch_id = ?
                SET m.ranking_order = r.rn
                """, yearMonth, batchId, yearMonth, batchId);

            return RepeatStatus.FINISHED;
        };
    }

    @StepScope
    @Bean("monthlySwapTasklet")
    public Tasklet monthlySwapTasklet(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['run.id']}") Long batchId
    ) {
        return (contribution, chunkContext) -> {
            int yearMonth = toYearMonth(LocalDate.parse(targetDate, DATE_FORMAT));
            String key    = "MONTHLY:" + yearMonth;

            activeVersionRepository.findById(key)
                .ifPresentOrElse(
                    v -> v.activate(batchId),
                    () -> activeVersionRepository.save(new MvActiveVersionEntity(key, batchId))
                );

            return RepeatStatus.FINISHED;
        };
    }

    @StepScope
    @Bean("monthlyOldBatchCleanupTasklet")
    public Tasklet monthlyOldBatchCleanupTasklet(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['run.id']}") Long batchId
    ) {
        return (contribution, chunkContext) -> {
            int yearMonth = toYearMonth(LocalDate.parse(targetDate, DATE_FORMAT));
            monthlyRepository.deleteOldBatches(yearMonth, batchId);
            return RepeatStatus.FINISHED;
        };
    }

    private int toYearMonth(LocalDate date) {
        return date.getYear() * 100 + date.getMonthValue();
    }

    private double getWeight(String eventType, double defaultWeight) {
        return rankingWeightRepository.findById(eventType)
            .map(RankingWeightEntity::getWeight)
            .orElse(defaultWeight);
    }
}
