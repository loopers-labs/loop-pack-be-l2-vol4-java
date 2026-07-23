package com.loopers.batch.job.rank;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.rank.MonthlyProductRankModel;
import com.loopers.domain.rank.ProductRankSnapshotModel;
import com.loopers.domain.rank.RankScorePolicy;
import com.loopers.domain.rank.WeeklyProductRankModel;
import com.loopers.infrastructure.rank.MonthlyProductRankJpaRepository;
import com.loopers.infrastructure.rank.WeeklyProductRankJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 주간·월간 랭킹 집계 Job — 일간 롤업(product_metrics_daily)을 기간 집계해 MV 두 테이블에 TOP 100을 적재한다.
 *
 * <p>구성: 각 기간마다 (clear → chunk-aggregate) 두 스텝. clear가 <b>해당 기간</b> 스냅샷만 비우고(과거 기간은
 * 보존 — 히스토리), chunk 스텝이 가중 점수 내림차순 TOP 100을 순위와 함께 새로 적재한다 — 같은 baseDate
 * 재실행 시 그 기간만 교체하는 멱등.
 *
 * <p>파라미터: {@code baseDate}(yyyy-MM-dd, 미지정 시 어제). 주간=[baseDate-6, baseDate], 월간=[baseDate-29, baseDate].
 * 오늘의 일간 롤업은 아직 누적 중이라 기본 기준일을 어제로 둔다.
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class ProductRankAggregationJobConfig {

    public static final String JOB_NAME = "productRankAggregationJob";

    private static final int WEEKLY_DAYS = 7;
    private static final int MONTHLY_DAYS = 30;

    /** 청크 커밋 인터벌 — TOP_N과 무관하며, 집계된 전체 상품이 이 크기 단위로 처리·커밋된다. */
    private static final int CHUNK_SIZE = 100;

    /**
     * 기간 내 상품별 카운트 합산 + 가중 점수 내림차순 정렬 (LIMIT 없음).
     * TOP N 컷은 SQL이 아니라 청크의 Processor({@link TopNRankProcessor})가 담당해, 집계된 전체 상품이
     * Reader→Processor 청크 파이프라인을 관통하도록 한다. 점수 계수는 {@link RankScorePolicy} 단일 소스.
     */
    private static final String AGGREGATE_SQL =
        "SELECT product_id AS productId, "
            + "SUM(view_count) AS viewSum, SUM(like_count) AS likeSum, SUM(sales_count) AS salesSum "
            + "FROM product_metrics_daily "
            + "WHERE metric_date BETWEEN ? AND ? "
            + "GROUP BY product_id "
            + "ORDER BY (" + RankScorePolicy.scoreSql("SUM(view_count)", "SUM(like_count)", "SUM(sales_count)") + ") DESC, "
            + "product_id ASC";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;
    private final WeeklyProductRankJpaRepository weeklyRepository;
    private final MonthlyProductRankJpaRepository monthlyRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;

    @Bean(JOB_NAME)
    public Job productRankAggregationJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(weeklyClearStep())
            .next(weeklyAggregateStep())
            .next(monthlyClearStep())
            .next(monthlyAggregateStep())
            .listener(jobListener)
            .build();
    }

    // ── 주간 ────────────────────────────────────────────────────────────────

    @Bean
    public Step weeklyClearStep() {
        return new StepBuilder("weeklyClearStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                LocalDate end = resolveBaseDate(baseDateParam(chunkContext));
                weeklyRepository.deleteByPeriod(end.minusDays(WEEKLY_DAYS - 1), end);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean
    public Step weeklyAggregateStep() {
        return new StepBuilder("weeklyAggregateStep", jobRepository)
            .<ProductMetricsAggregate, ProductRankSnapshotModel>chunk(CHUNK_SIZE, transactionManager)
            .reader(weeklyRankReader(null))
            .processor(weeklyRankProcessor(null))
            .writer(rankWriter())
            .listener(stepMonitorListener)
            .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<ProductMetricsAggregate> weeklyRankReader(
        @Value("#{jobParameters['baseDate']}") String baseDate
    ) {
        LocalDate end = resolveBaseDate(baseDate);
        return buildReader("weeklyRankReader", end.minusDays(WEEKLY_DAYS - 1), end);
    }

    @Bean
    @StepScope
    public TopNRankProcessor<WeeklyProductRankModel> weeklyRankProcessor(
        @Value("#{jobParameters['baseDate']}") String baseDate
    ) {
        LocalDate end = resolveBaseDate(baseDate);
        LocalDate start = end.minusDays(WEEKLY_DAYS - 1);
        return new TopNRankProcessor<>(RankScorePolicy.TOP_N, start, end, WeeklyProductRankModel::new);
    }

    // ── 월간 ────────────────────────────────────────────────────────────────

    @Bean
    public Step monthlyClearStep() {
        return new StepBuilder("monthlyClearStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                LocalDate end = resolveBaseDate(baseDateParam(chunkContext));
                monthlyRepository.deleteByPeriod(end.minusDays(MONTHLY_DAYS - 1), end);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean
    public Step monthlyAggregateStep() {
        return new StepBuilder("monthlyAggregateStep", jobRepository)
            .<ProductMetricsAggregate, ProductRankSnapshotModel>chunk(CHUNK_SIZE, transactionManager)
            .reader(monthlyRankReader(null))
            .processor(monthlyRankProcessor(null))
            .writer(rankWriter())
            .listener(stepMonitorListener)
            .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<ProductMetricsAggregate> monthlyRankReader(
        @Value("#{jobParameters['baseDate']}") String baseDate
    ) {
        LocalDate end = resolveBaseDate(baseDate);
        return buildReader("monthlyRankReader", end.minusDays(MONTHLY_DAYS - 1), end);
    }

    @Bean
    @StepScope
    public TopNRankProcessor<MonthlyProductRankModel> monthlyRankProcessor(
        @Value("#{jobParameters['baseDate']}") String baseDate
    ) {
        LocalDate end = resolveBaseDate(baseDate);
        LocalDate start = end.minusDays(MONTHLY_DAYS - 1);
        return new TopNRankProcessor<>(RankScorePolicy.TOP_N, start, end, MonthlyProductRankModel::new);
    }

    // ── 공통 ────────────────────────────────────────────────────────────────

    /** 두 MV가 같은 EMF로 persist(항상 새 행 — clear 이후 insert). */
    @Bean
    public JpaItemWriter<ProductRankSnapshotModel> rankWriter() {
        return new JpaItemWriterBuilder<ProductRankSnapshotModel>()
            .entityManagerFactory(entityManagerFactory)
            .usePersist(true)
            .build();
    }

    private JdbcCursorItemReader<ProductMetricsAggregate> buildReader(String name, LocalDate start, LocalDate end) {
        return new JdbcCursorItemReaderBuilder<ProductMetricsAggregate>()
            .name(name)
            .dataSource(dataSource)
            .sql(AGGREGATE_SQL)
            .preparedStatementSetter(ps -> {
                ps.setObject(1, start);
                ps.setObject(2, end);
            })
            .rowMapper((rs, rowNum) -> new ProductMetricsAggregate(
                rs.getLong("productId"),
                rs.getLong("viewSum"),
                rs.getLong("likeSum"),
                rs.getLong("salesSum")
            ))
            .build();
    }

    /** clear 스텝에서 JobParameter {@code baseDate}(String)를 읽는다 — 리더/프로세서와 동일 기간 창을 계산하기 위함. */
    private String baseDateParam(org.springframework.batch.core.scope.context.ChunkContext chunkContext) {
        Object value = chunkContext.getStepContext().getJobParameters().get("baseDate");
        return value == null ? null : value.toString();
    }

    private LocalDate resolveBaseDate(String baseDate) {
        if (baseDate == null || baseDate.isBlank()) {
            return LocalDate.now().minusDays(1);
        }
        try {
            return LocalDate.parse(baseDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("baseDate는 yyyy-MM-dd 형식이어야 합니다: " + baseDate, e);
        }
    }
}
