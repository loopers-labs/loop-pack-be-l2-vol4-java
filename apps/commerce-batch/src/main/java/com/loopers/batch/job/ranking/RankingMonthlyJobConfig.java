package com.loopers.batch.job.ranking;

import com.loopers.batch.domain.ranking.MonthlyProductRankMv;
import com.loopers.batch.domain.ranking.ProductScoreStaging;
import com.loopers.batch.domain.ranking.RankingScorePolicy;
import com.loopers.infrastructure.ranking.MonthlyProductRankMvJpaRepository;
import com.loopers.infrastructure.ranking.ProductScoreStagingJpaRepository;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;

/**
 * 월간 랭킹 MV 적재 잡. 구조는 주간(RankingWeeklyJobConfig)과 동일하며 윈도우(30일)·대상 MV(월간)·잡 이름만 다르다.
 * clearStep(멱등 교체) -> aggregateStep(step1: product_metrics 대량 read -> Java 점수 계산 -> staging) -> rankStep(step2: staging 정렬 top100 -> MV).
 * jobParameters['snapshotDate'] 기준일로부터 지난 30일이 집계 윈도우 (week10 qna Q5, rolling).
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingMonthlyJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingMonthlyJobConfig {

    public static final String JOB_NAME = "rankingMonthlyJob";
    private static final int WINDOW_DAYS = 30;
    private static final int TOP_N = 100;
    private static final int CHUNK_SIZE = 1000;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final ProductScoreStagingJpaRepository stagingRepository;
    private final MonthlyProductRankMvJpaRepository mvRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;

    @Bean(JOB_NAME)
    public Job rankingMonthlyJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(clearStep(null))
            .next(aggregateStep(null))
            .next(rankStep(null))
            .listener(jobListener)
            .build();
    }

    /** 재실행 멱등: 해당 스냅샷 MV 를 지우고, 이전 실행의 중간 테이블 잔여도 비운다 (week10 qna Q4). */
    @Bean
    @JobScope
    public Step clearStep(@Value("#{jobParameters['snapshotDate']}") LocalDate snapshotDate) {
        return new StepBuilder("monthlyClearStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                mvRepository.deleteBySnapshotDate(snapshotDate);
                stagingRepository.deleteAllInBatch();
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    /** step1: 윈도우 내 product_metrics 를 상품별로 합산해 대량 read, Java 로 가중 점수 계산해 중간 테이블에 적재. */
    @Bean
    @JobScope
    public Step aggregateStep(@Value("#{jobParameters['snapshotDate']}") LocalDate snapshotDate) {
        return new StepBuilder("monthlyAggregateStep", jobRepository)
            .<DailyAggRow, ProductScoreStaging>chunk(CHUNK_SIZE, transactionManager)
            .reader(aggregateReader(snapshotDate))
            .processor(row -> new ProductScoreStaging(
                row.productId(),
                RankingScorePolicy.viewScore() * row.viewSum()
                    + RankingScorePolicy.likeScore() * row.likeSum()
                    + row.orderSum()))
            .writer(chunk -> stagingRepository.saveAll(chunk.getItems()))
            .listener(stepMonitorListener)
            .build();
    }

    /** step2: 중간 테이블을 점수순 top100 으로 정렬(전역 연산이라 DB 가 ROW_NUMBER 로 순위까지) 읽어 MV 로 적재. */
    @Bean
    @JobScope
    public Step rankStep(@Value("#{jobParameters['snapshotDate']}") LocalDate snapshotDate) {
        return new StepBuilder("monthlyRankStep", jobRepository)
            .<RankedRow, MonthlyProductRankMv>chunk(TOP_N, transactionManager)
            .reader(rankReader())
            .processor(row -> new MonthlyProductRankMv(snapshotDate, row.rank(), row.productId(), row.score()))
            .writer(chunk -> mvRepository.saveAll(chunk.getItems()))
            .listener(stepMonitorListener)
            .build();
    }

    private JdbcCursorItemReader<DailyAggRow> aggregateReader(LocalDate snapshotDate) {
        LocalDate from = snapshotDate.minusDays(WINDOW_DAYS - 1L);
        return new JdbcCursorItemReaderBuilder<DailyAggRow>()
            .name("monthlyAggregateReader")
            .dataSource(dataSource)
            .sql("SELECT product_id, SUM(view_count) AS view_sum, SUM(like_count) AS like_sum, SUM(order_score) AS order_sum "
                + "FROM product_metrics WHERE metric_date BETWEEN ? AND ? GROUP BY product_id")
            .preparedStatementSetter(ps -> {
                ps.setObject(1, from);
                ps.setObject(2, snapshotDate);
            })
            .rowMapper((rs, rowNum) -> new DailyAggRow(
                rs.getLong("product_id"), rs.getLong("view_sum"), rs.getLong("like_sum"), rs.getDouble("order_sum")))
            .build();
    }

    private JdbcCursorItemReader<RankedRow> rankReader() {
        return new JdbcCursorItemReaderBuilder<RankedRow>()
            .name("monthlyRankReader")
            .dataSource(dataSource)
            .sql("SELECT product_id, score, ROW_NUMBER() OVER (ORDER BY score DESC) AS rank_no "
                + "FROM product_score_staging ORDER BY score DESC LIMIT " + TOP_N)
            .rowMapper((rs, rowNum) -> new RankedRow(
                rs.getInt("rank_no"), rs.getLong("product_id"), rs.getDouble("score")))
            .build();
    }

    private record DailyAggRow(long productId, long viewSum, long likeSum, double orderSum) {
    }

    private record RankedRow(int rank, long productId, double score) {
    }
}
