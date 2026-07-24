package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.period.PeriodMetricsSum;
import com.loopers.batch.job.ranking.period.PeriodRange;
import com.loopers.batch.job.ranking.period.PeriodScore;
import com.loopers.batch.job.ranking.period.RankingPeriodType;
import com.loopers.batch.job.ranking.period.step.PeriodRankConfirmTasklet;
import com.loopers.batch.job.ranking.period.step.PeriodRankingComponents;
import com.loopers.batch.job.ranking.period.step.PeriodScoreProcessor;
import com.loopers.batch.job.ranking.period.step.PeriodStagingCleanupTasklet;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 주간/월간 기간 랭킹 잡(week10). {@code product_metrics_daily} 를 구간 집계해 MV 로 내린다.
 *
 * <pre>
 *   실행: --job.name=weeklyRankingJob   (+ 선택 baseDate=yyyyMMdd)
 *        --job.name=monthlyRankingJob
 * </pre>
 * {@code baseDate} 를 주면 <b>그 날짜가 속한 기간</b>을, 없으면 <b>직전 확정 기간</b>(지난주/지난달)을 집계한다.
 * 주간은 매주 월요일, 월간은 매월 1일 새벽에 돌리면 된다.
 *
 * <p><b>3-Step 구조</b>:
 * <ol>
 *   <li><b>cleanup</b>(Tasklet) — 해당 기간 staging 제거. 재실행 멱등을 위해 필요하다.</li>
 *   <li><b>aggregate</b>(<b>Chunk</b>) — 커서로 구간 집계를 스트리밍하며 스코어를 계산해 staging 에 적재.
 *       전 상품이 대상이라 여기가 대량 처리 구간이다.</li>
 *   <li><b>confirm</b>(Tasklet) — staging 을 전역 정렬해 상위 N 의 순위를 확정하고 MV 로 옮긴다.</li>
 * </ol>
 * 순위 확정을 청크로 못 하는 이유는 {@link PeriodRankConfirmTasklet} javadoc 참고.
 *
 * <p>Demo 잡과 달리 <b>실제 트랜잭션 매니저</b>를 쓴다 — 모든 스텝이 DB 에 쓰기 때문이다
 * ({@code RankingSnapshotJobConfig} 와 동일).
 */
@RequiredArgsConstructor
@Configuration
public class PeriodRankingJobConfig {

    public static final String WEEKLY_JOB_NAME = "weeklyRankingJob";
    public static final String MONTHLY_JOB_NAME = "monthlyRankingJob";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PeriodScoreProcessor periodScoreProcessor;

    /** MV 에 남길 상위 N. 과제 요구사항은 TOP 100. */
    @Value("${ranking.period.top-n:100}")
    private int topN;

    /**
     * 청크 크기. 커밋 단위이자 Writer 의 batch 크기다. 너무 작으면 왕복이 늘고, 너무 크면 실패 시
     * 되돌리는 양과 메모리가 커진다. 행이 {@code (id, score)} 수준으로 가벼워 500 으로 둔다.
     */
    @Value("${ranking.period.chunk-size:500}")
    private int chunkSize;

    // =====================================================================
    // 주간
    // =====================================================================

    @Bean(WEEKLY_JOB_NAME)
    @ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WEEKLY_JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(WEEKLY_JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(weeklyStagingCleanupStep(null))
                .next(weeklyScoreAggregateStep(null))
                .next(weeklyRankConfirmStep(null))
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean("weeklyStagingCleanupStep")
    public Step weeklyStagingCleanupStep(@Value("#{jobParameters['baseDate']}") String baseDate) {
        return cleanupStep("weeklyStagingCleanupStep", RankingPeriodType.WEEKLY, baseDate);
    }

    @JobScope
    @Bean("weeklyScoreAggregateStep")
    public Step weeklyScoreAggregateStep(@Value("#{jobParameters['baseDate']}") String baseDate) {
        return aggregateStep("weeklyScoreAggregateStep", RankingPeriodType.WEEKLY, baseDate);
    }

    @JobScope
    @Bean("weeklyRankConfirmStep")
    public Step weeklyRankConfirmStep(@Value("#{jobParameters['baseDate']}") String baseDate) {
        return confirmStep("weeklyRankConfirmStep", RankingPeriodType.WEEKLY, baseDate);
    }

    // =====================================================================
    // 월간
    // =====================================================================

    @Bean(MONTHLY_JOB_NAME)
    @ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MONTHLY_JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(MONTHLY_JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(monthlyStagingCleanupStep(null))
                .next(monthlyScoreAggregateStep(null))
                .next(monthlyRankConfirmStep(null))
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean("monthlyStagingCleanupStep")
    public Step monthlyStagingCleanupStep(@Value("#{jobParameters['baseDate']}") String baseDate) {
        return cleanupStep("monthlyStagingCleanupStep", RankingPeriodType.MONTHLY, baseDate);
    }

    @JobScope
    @Bean("monthlyScoreAggregateStep")
    public Step monthlyScoreAggregateStep(@Value("#{jobParameters['baseDate']}") String baseDate) {
        return aggregateStep("monthlyScoreAggregateStep", RankingPeriodType.MONTHLY, baseDate);
    }

    @JobScope
    @Bean("monthlyRankConfirmStep")
    public Step monthlyRankConfirmStep(@Value("#{jobParameters['baseDate']}") String baseDate) {
        return confirmStep("monthlyRankConfirmStep", RankingPeriodType.MONTHLY, baseDate);
    }

    // =====================================================================
    // 공통 스텝 빌더 — 주간/월간은 기간 타입만 다르다
    // =====================================================================

    private Step cleanupStep(String name, RankingPeriodType type, String baseDate) {
        PeriodRange range = PeriodRange.resolve(type, baseDate);
        return new StepBuilder(name, jobRepository)
                .tasklet(new PeriodStagingCleanupTasklet(jdbcTemplate, type, range), transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    /** Chunk-Oriented: Reader(커서 스트리밍) → Processor(스코어) → Writer(staging 적재). */
    private Step aggregateStep(String name, RankingPeriodType type, String baseDate) {
        PeriodRange range = PeriodRange.resolve(type, baseDate);
        return new StepBuilder(name, jobRepository)
                .<PeriodMetricsSum, PeriodScore>chunk(chunkSize, transactionManager)
                .reader(PeriodRankingComponents.reader(dataSource, range))
                .processor(periodScoreProcessor)
                .writer(PeriodRankingComponents.stagingWriter(dataSource, type, range))
                .listener(stepMonitorListener)
                .build();
    }

    private Step confirmStep(String name, RankingPeriodType type, String baseDate) {
        PeriodRange range = PeriodRange.resolve(type, baseDate);
        return new StepBuilder(name, jobRepository)
                .tasklet(new PeriodRankConfirmTasklet(jdbcTemplate, type, range, topN), transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
