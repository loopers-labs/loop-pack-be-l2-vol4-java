package com.loopers.batch.job.ranking;

import com.loopers.infrastructure.metrics.DailyProductMetrics;
import com.loopers.infrastructure.metrics.DailyProductMetricsJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthly;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME)
class MonthlyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job job;

    private final DailyProductMetricsJpaRepository dailyRepository;
    private final MvProductRankMonthlyJpaRepository mvRepository;

    @Autowired
    MonthlyRankingJobE2ETest(
        DailyProductMetricsJpaRepository dailyRepository,
        MvProductRankMonthlyJpaRepository mvRepository
    ) {
        this.dailyRepository = dailyRepository;
        this.mvRepository = mvRepository;
    }

    @AfterEach
    void tearDown() {
        mvRepository.deleteAllInBatch();
        dailyRepository.deleteAllInBatch();
    }

    @DisplayName("월간 잡을 실행하면 그 달(2026-07)의 daily 를 상품별로 집계해 score 내림차순 rank 로 MV 에 적재하며, 다른 달은 제외한다.")
    @Test
    void aggregatesMonthDailyIntoMv_rankedByScoreDesc() throws Exception {
        // given : 2026-07 = 2026-07-01 ~ 2026-07-31
        // 상품 1: sales 10 (07-31, 월 경계 말일 포함) → 7.0
        // 상품 2: like 10 (07-01, 월 경계 첫날 포함)  → 2.0
        // 상품 8: 다른 달(2026-06-30)                  → 집계 제외
        dailyRepository.saveAll(List.of(
            DailyProductMetrics.of(1L, LocalDate.of(2026, 7, 31), 0, 10, 0),
            DailyProductMetrics.of(2L, LocalDate.of(2026, 7, 1), 10, 0, 0),
            DailyProductMetrics.of(8L, LocalDate.of(2026, 6, 30), 0, 100, 0)
        ));

        // when : 그 달에 속한 아무 하루로 잡 실행
        jobLauncherTestUtils.setJob(job);
        var jobParameters = new JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.of(2026, 7, 21))
            .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        List<MvProductRankMonthly> ranked = mvRepository.findByYearMonthOrderByRankNoAsc("2026-07");
        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(ranked).hasSize(2),
            () -> assertThat(ranked.get(0).getProductId()).isEqualTo(1L),
            () -> assertThat(ranked.get(0).getRankNo()).isEqualTo(1),
            () -> assertThat(ranked.get(0).getScore()).isEqualTo(7.0),
            () -> assertThat(ranked.get(1).getProductId()).isEqualTo(2L),
            () -> assertThat(ranked.get(1).getRankNo()).isEqualTo(2)
        );
    }
}
