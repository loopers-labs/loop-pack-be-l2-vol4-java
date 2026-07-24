package com.loopers.batch.job.rank;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MonthlyProductRankModel;
import com.loopers.domain.ranking.PeriodType;
import com.loopers.domain.ranking.ProductRankModel;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.WeeklyProductRankModel;
import com.loopers.infrastructure.ranking.MonthlyProductRankJpaRepository;
import com.loopers.infrastructure.ranking.WeeklyProductRankJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
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
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 주간/월간 상품 랭킹 집계 Job. 파라미터(baseDate, periodType)로 대상 기간을 정해
 * product_metrics를 기간 집계하고, TOP 100을 MV(mv_product_rank_weekly/monthly)에 적재한다.
 * <p>
 * 구조는 chunk-oriented(Reader/Processor/Writer). 집계·가중합 점수·정렬·순위(ROW_NUMBER)는
 * DB(Reader SQL)가 수행하고, 청크에는 정렬된 TOP 100행만 흐른다(A안). 재실행 멱등성을 위해
 * aggregate 전에 해당 기간 행을 먼저 비운다(clear → aggregate).
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingJobConfig {

    public static final String JOB_NAME = "productRankJob";
    private static final String CLEAR_STEP = "rankClearStep";
    private static final String AGGREGATE_STEP = "rankAggregateStep";
    private static final int CHUNK_SIZE = 100;
    private static final int TOP_N = 100;

    // 가중합 점수 정책: 구매가 좋아요보다 강한 신호라 판매량에 더 무게(0.7 : 0.3).
    private static final double W_SALES = 0.7;
    private static final double W_LIKE = 0.3;
    private static final String SCORE_EXPR = W_SALES + " * SUM(sales_count) + " + W_LIKE + " * SUM(like_count)";
    private static final String READ_SQL =
        "SELECT product_id, "
            + "SUM(like_count) AS like_sum, "
            + "SUM(sales_count) AS sales_sum, "
            + "(" + SCORE_EXPR + ") AS score, "
            + "ROW_NUMBER() OVER (ORDER BY (" + SCORE_EXPR + ") DESC) AS ranking "
            + "FROM product_metrics "
            + "WHERE stat_date BETWEEN ? AND ? "
            + "GROUP BY product_id "
            + "ORDER BY score DESC "
            + "LIMIT " + TOP_N;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;
    private final WeeklyProductRankJpaRepository weeklyRepository;
    private final MonthlyProductRankJpaRepository monthlyRepository;

    @Bean(JOB_NAME)
    public Job productRankJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(rankClearStep())
            .next(rankAggregateStep())
            .listener(jobListener)
            .build();
    }

    /** 재실행 멱등성: 대상 기간의 기존 랭킹 행을 삭제한다. */
    @JobScope
    @Bean(CLEAR_STEP)
    public Step rankClearStep() {
        return new StepBuilder(CLEAR_STEP, jobRepository)
            .tasklet(clearTasklet(null, null), transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean
    public Tasklet clearTasklet(@Value("#{jobParameters['baseDate']}") String baseDate,
                                @Value("#{jobParameters['periodType']}") String periodType) {
        return (contribution, chunkContext) -> {
            PeriodType type = PeriodType.valueOf(periodType);
            RankingPeriod period = RankingPeriod.of(type, parseDate(baseDate));
            if (type == PeriodType.WEEKLY) {
                weeklyRepository.deleteByPeriodStart(period.start());
            } else {
                monthlyRepository.deleteByPeriodStart(period.start());
            }
            return RepeatStatus.FINISHED;
        };
    }

    @JobScope
    @Bean(AGGREGATE_STEP)
    public Step rankAggregateStep() {
        return new StepBuilder(AGGREGATE_STEP, jobRepository)
            .<AggregatedRankRow, ProductRankModel>chunk(CHUNK_SIZE, transactionManager)
            .reader(rankReader(null, null))
            .processor(rankProcessor(null, null))
            .writer(rankWriter())
            .listener(stepMonitorListener)
            .build();
    }

    /** product_metrics를 기간 집계해 점수·순위가 매겨진 TOP 100행을 읽는다. */
    @StepScope
    @Bean
    public JdbcCursorItemReader<AggregatedRankRow> rankReader(
        @Value("#{jobParameters['baseDate']}") String baseDate,
        @Value("#{jobParameters['periodType']}") String periodType) {
        RankingPeriod period = RankingPeriod.of(PeriodType.valueOf(periodType), parseDate(baseDate));
        return new JdbcCursorItemReaderBuilder<AggregatedRankRow>()
            .name("rankReader")
            .dataSource(dataSource)
            .sql(READ_SQL)
            .preparedStatementSetter(ps -> {
                ps.setDate(1, Date.valueOf(period.start()));
                ps.setDate(2, Date.valueOf(period.end()));
            })
            .rowMapper((rs, rowNum) -> new AggregatedRankRow(
                rs.getLong("product_id"),
                rs.getLong("like_sum"),
                rs.getLong("sales_sum"),
                rs.getDouble("score"),
                rs.getInt("ranking")))
            .build();
    }

    /** 집계 행을 대상 기간의 MV 엔티티(주간/월간)로 변환한다. */
    @StepScope
    @Bean
    public ItemProcessor<AggregatedRankRow, ProductRankModel> rankProcessor(
        @Value("#{jobParameters['baseDate']}") String baseDate,
        @Value("#{jobParameters['periodType']}") String periodType) {
        PeriodType type = PeriodType.valueOf(periodType);
        RankingPeriod period = RankingPeriod.of(type, parseDate(baseDate));
        ZonedDateTime aggregatedAt = ZonedDateTime.now(KST);
        return row -> type == PeriodType.WEEKLY
            ? new WeeklyProductRankModel(period.start(), period.end(), row.productId(),
                row.ranking(), row.score(), row.likeSum(), row.salesSum(), aggregatedAt)
            : new MonthlyProductRankModel(period.start(), period.end(), row.productId(),
                row.ranking(), row.score(), row.likeSum(), row.salesSum(), aggregatedAt);
    }

    /** clear로 기간을 비운 뒤이므로 항상 신규 insert(persist). */
    @Bean
    public JpaItemWriter<ProductRankModel> rankWriter() {
        return new JpaItemWriterBuilder<ProductRankModel>()
            .entityManagerFactory(entityManagerFactory)
            .usePersist(true)
            .build();
    }

    private static LocalDate parseDate(String yyyyMMdd) {
        return LocalDate.parse(yyyyMMdd, DateTimeFormatter.BASIC_ISO_DATE);
    }
}
