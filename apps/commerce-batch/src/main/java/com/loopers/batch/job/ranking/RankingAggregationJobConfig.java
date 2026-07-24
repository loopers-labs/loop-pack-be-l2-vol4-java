package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MvProductRank;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.Period;
import com.loopers.domain.ranking.RankAggregate;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * product_metrics_hourly(시간 단위 SOT)를 기간(WEEKLY/MONTHLY)으로 집계해 주간·월간 랭킹 MV의 TOP 100을 적재하는 Job.
 * <pre>
 *   --job.name=rankingAggregationJob baseDate=yyyyMMdd period=WEEKLY|MONTHLY
 * </pre>
 * clearStep(기존 주기 삭제 → 재실행 멱등) → aggregateStep(Chunk: 집계 조회 → rank 부여 → MV 저장).
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingAggregationJobConfig {

    public static final String JOB_NAME = "rankingAggregationJob";
    private static final String CLEAR_STEP = "clearMvStep";
    private static final String AGGREGATE_STEP = "aggregateRankStep";
    private static final int TOP_N = 100;
    private static final int CHUNK_SIZE = 100;
    private static final DateTimeFormatter BASE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 집계 점수 = 0.1·조회 + 0.2·좋아요 + 0.6·주문금액. 실시간(RankingService)·RankAggregate.score()와 동일 가중치.
    // 원천은 시간 단위 SOT(product_metrics_hourly) — 유입 경로(source)별로 나뉜 행을 상품별로 모두 합산한다.
    private static final String AGGREGATE_QUERY = """
            SELECT new com.loopers.domain.ranking.RankAggregate(
                h.productId, SUM(h.likeDelta), SUM(h.orderAmount), SUM(h.viewCount))
            FROM ProductMetricsHourly h
            WHERE h.bucketHour >= :from AND h.bucketHour < :to
            GROUP BY h.productId
            ORDER BY (0.1 * SUM(h.viewCount) + 0.2 * SUM(h.likeDelta) + 0.6 * SUM(h.orderAmount)) DESC, h.productId ASC
            """;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final MvProductRankWeeklyJpaRepository weeklyRepository;
    private final MvProductRankMonthlyJpaRepository monthlyRepository;

    /**
     * incrementer(RunIdIncrementer)를 두지 않는다. 기간(baseDate, period)이 JobInstance를 식별해야 하므로,
     * 같은 기간을 다시 성공 완료하는 것을 Spring Batch가 막게 둔다. 재적재가 필요하면 clearMvStep이
     * 기존 결과를 지우고 다시 넣으므로, run.id로 새 인스턴스를 찍는 대신 파라미터로만 재실행을 표현한다.
     */
    @Bean(JOB_NAME)
    public Job rankingAggregationJob(Step clearMvStep, Step aggregateRankStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(clearMvStep)
                .next(aggregateRankStep)
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(CLEAR_STEP)
    public Step clearMvStep(Tasklet clearMvTasklet) {
        return new StepBuilder(CLEAR_STEP, jobRepository)
                .tasklet(clearMvTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    /** 이번 주기의 기존 MV 행을 지운다 → 같은 baseDate/period로 재실행해도 중복 없이 덮어쓴다. */
    @StepScope
    @Bean
    public Tasklet clearMvTasklet(
            @Value("#{jobParameters['baseDate']}") String baseDate,
            @Value("#{jobParameters['period']}") String period) {
        return (contribution, chunkContext) -> {
            String key = periodKey(baseDate, period);
            if (Period.valueOf(period) == Period.WEEKLY) {
                weeklyRepository.deleteByPeriodKey(key);
            } else {
                monthlyRepository.deleteByPeriodKey(key);
            }
            return RepeatStatus.FINISHED;
        };
    }

    @JobScope
    @Bean(AGGREGATE_STEP)
    public Step aggregateRankStep(
            JpaPagingItemReader<RankAggregate> rankReader,
            ItemProcessor<RankAggregate, MvProductRank> rankProcessor,
            JpaItemWriter<MvProductRank> rankWriter) {
        return new StepBuilder(AGGREGATE_STEP, jobRepository)
                .<RankAggregate, MvProductRank>chunk(CHUNK_SIZE, transactionManager)
                .reader(rankReader)
                .processor(rankProcessor)
                .writer(rankWriter)
                .listener(stepMonitorListener)
                .build();
    }

    /** 기간 범위로 상품별 지표를 GROUP BY 합산하고 점수 내림차순 상위 100건만 읽는다. */
    @StepScope
    @Bean
    public JpaPagingItemReader<RankAggregate> rankReader(
            @Value("#{jobParameters['baseDate']}") String baseDate,
            @Value("#{jobParameters['period']}") String period) {
        Period p = Period.valueOf(period);
        LocalDate base = LocalDate.parse(baseDate, BASE_DATE);
        // bucket_hour는 시각(timestamp)이라 날짜 범위를 [기간 시작 00:00, 기간 종료 다음날 00:00)로 변환한다.
        ZonedDateTime from = p.from(base).atStartOfDay(SEOUL);
        ZonedDateTime to = p.to(base).plusDays(1).atStartOfDay(SEOUL);
        return new JpaPagingItemReaderBuilder<RankAggregate>()
                .name("rankReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString(AGGREGATE_QUERY)
                .parameterValues(Map.of("from", from, "to", to))
                .pageSize(TOP_N)
                .maxItemCount(TOP_N)
                .build();
    }

    /** 읽힌 순서(점수 내림차순)대로 순위를 매기고 기간에 맞는 MV 엔티티로 변환한다. */
    @StepScope
    @Bean
    public ItemProcessor<RankAggregate, MvProductRank> rankProcessor(
            @Value("#{jobParameters['baseDate']}") String baseDate,
            @Value("#{jobParameters['period']}") String period) {
        Period p = Period.valueOf(period);
        String key = periodKey(baseDate, period);
        AtomicInteger rank = new AtomicInteger(0);
        return aggregate -> {
            int rankNo = rank.incrementAndGet();
            return p == Period.WEEKLY
                    ? new MvProductRankWeekly(key, rankNo, aggregate)
                    : new MvProductRankMonthly(key, rankNo, aggregate);
        };
    }

    /**
     * MV 행은 항상 신규 삽입이므로 persist를 쓴다.
     * 기본값인 merge는 BaseEntity의 id가 0L로 초기화돼 있어 detached로 판정돼,
     * INSERT 전에 행마다 SELECT를 한 번씩 더 날린다(100건 적재에 SELECT 100번).
     */
    @Bean
    public JpaItemWriter<MvProductRank> rankWriter() {
        return new JpaItemWriterBuilder<MvProductRank>()
                .entityManagerFactory(entityManagerFactory)
                .usePersist(true)
                .build();
    }

    private static String periodKey(String baseDate, String period) {
        return Period.valueOf(period).key(LocalDate.parse(baseDate, BASE_DATE));
    }
}
