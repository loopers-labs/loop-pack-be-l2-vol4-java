package com.loopers.batch.job.ranking.weekly;

import com.loopers.batch.job.ranking.ProductMetricsSummary;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MvActiveVersionEntity;
import com.loopers.domain.ranking.MvProductRankWeeklyEntity;
import com.loopers.domain.ranking.RankingWeightEntity;
import com.loopers.infrastructure.ranking.MvActiveVersionJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.UUID;

@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final int CHUNK_SIZE = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager txManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final MvProductRankWeeklyJpaRepository weeklyRepository;
    private final MvActiveVersionJpaRepository activeVersionRepository;
    private final RankingWeightJpaRepository rankingWeightRepository;
    private final JdbcTemplate jdbcTemplate;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(weeklyAggregationStep())
            .next(weeklyRankStep())
            .next(weeklySwapStep())
            .next(weeklyOldBatchCleanupStep())
            .listener(jobListener)
            .build();
    }

    @Bean("weeklyAggregationStep")
    public Step weeklyAggregationStep() {
        return new StepBuilder("weeklyAggregationStep", jobRepository)
            .<ProductMetricsSummary, MvProductRankWeeklyEntity>chunk(CHUNK_SIZE, txManager)
            .reader(weeklyRankingReader(null, null))
            .processor(weeklyRankingProcessor(null, null))
            .writer(weeklyRankingWriter())
            .listener(stepMonitorListener)
            .build();
    }

    @Bean("weeklyRankStep")
    public Step weeklyRankStep() {
        return new StepBuilder("weeklyRankStep", jobRepository)
            .tasklet(weeklyRankTasklet(null, null), txManager)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean("weeklySwapStep")
    public Step weeklySwapStep() {
        return new StepBuilder("weeklySwapStep", jobRepository)
            .tasklet(weeklySwapTasklet(null, null), txManager)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean("weeklyOldBatchCleanupStep")
    public Step weeklyOldBatchCleanupStep() {
        return new StepBuilder("weeklyOldBatchCleanupStep", jobRepository)
            .tasklet(weeklyOldBatchCleanupTasklet(null, null), txManager)
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean("weeklyRankingReader")
    public JdbcCursorItemReader<ProductMetricsSummary> weeklyRankingReader(
        DataSource dataSource,
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate date  = LocalDate.parse(targetDate, DATE_FORMAT);
        LocalDate start = date.with(DayOfWeek.MONDAY);
        LocalDate end   = date;

        return new JdbcCursorItemReaderBuilder<ProductMetricsSummary>()
            .name("weeklyRankingReader")
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
    @Bean("weeklyRankingProcessor")
    public ItemProcessor<ProductMetricsSummary, MvProductRankWeeklyEntity> weeklyRankingProcessor(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['run.id']}") Long batchId
    ) {
        int yearWeek   = toYearWeek(LocalDate.parse(targetDate, DATE_FORMAT));
        double viewWeight  = getWeight("VIEW",  0.1);
        double likeWeight  = getWeight("LIKE",  0.2);
        double orderWeight = getWeight("ORDER", 0.7);

        return item -> {
            double score = item.likeCount()  * likeWeight
                         + item.viewCount()  * viewWeight
                         + item.salesCount() * orderWeight;
            return new MvProductRankWeeklyEntity(item.productId(), 0, score, yearWeek, batchId);
        };
    }

    @Bean("weeklyRankingWriter")
    public ItemWriter<MvProductRankWeeklyEntity> weeklyRankingWriter() {
        return chunk -> weeklyRepository.saveAll(chunk.getItems());
    }

    @StepScope
    @Bean("weeklyRankTasklet")
    public Tasklet weeklyRankTasklet(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['run.id']}") Long batchId
    ) {
        return (contribution, chunkContext) -> {
            int yearWeek = toYearWeek(LocalDate.parse(targetDate, DATE_FORMAT));

            jdbcTemplate.update("""
                DELETE FROM mv_product_rank_weekly
                WHERE year_week = ? AND batch_id = ?
                  AND product_id NOT IN (
                      SELECT product_id FROM (
                          SELECT product_id
                          FROM mv_product_rank_weekly
                          WHERE year_week = ? AND batch_id = ?
                          ORDER BY score DESC
                          LIMIT 100
                      ) AS top100
                  )
                """, yearWeek, batchId, yearWeek, batchId);

            jdbcTemplate.update("""
                UPDATE mv_product_rank_weekly w
                JOIN (
                    SELECT product_id,
                           ROW_NUMBER() OVER (ORDER BY score DESC) AS rn
                    FROM mv_product_rank_weekly
                    WHERE year_week = ? AND batch_id = ?
                ) r ON w.product_id = r.product_id AND w.year_week = ? AND w.batch_id = ?
                SET w.ranking_order = r.rn
                """, yearWeek, batchId, yearWeek, batchId);

            return RepeatStatus.FINISHED;
        };
    }

    @StepScope
    @Bean("weeklySwapTasklet")
    public Tasklet weeklySwapTasklet(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['run.id']}") Long batchId
    ) {
        return (contribution, chunkContext) -> {
            int yearWeek  = toYearWeek(LocalDate.parse(targetDate, DATE_FORMAT));
            String key    = "WEEKLY:" + yearWeek;

            activeVersionRepository.findById(key)
                .ifPresentOrElse(
                    v -> v.activate(batchId),
                    () -> activeVersionRepository.save(new MvActiveVersionEntity(key, batchId))
                );

            return RepeatStatus.FINISHED;
        };
    }

    @StepScope
    @Bean("weeklyOldBatchCleanupTasklet")
    public Tasklet weeklyOldBatchCleanupTasklet(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['run.id']}") Long batchId
    ) {
        return (contribution, chunkContext) -> {
            int yearWeek = toYearWeek(LocalDate.parse(targetDate, DATE_FORMAT));
            weeklyRepository.deleteOldBatches(yearWeek, batchId);
            return RepeatStatus.FINISHED;
        };
    }

    private int toYearWeek(LocalDate date) {
        int year = date.get(WeekFields.ISO.weekBasedYear());
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        return year * 100 + week;
    }

    private double getWeight(String eventType, double defaultWeight) {
        return rankingWeightRepository.findById(eventType)
            .map(RankingWeightEntity::getWeight)
            .orElse(defaultWeight);
    }
}
