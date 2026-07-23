package com.loopers.batch.job.ranking;

import com.loopers.infrastructure.metrics.DailyProductMetrics;
import com.loopers.infrastructure.metrics.DailyProductMetricsJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeekly;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
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
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job job;

    private final DailyProductMetricsJpaRepository dailyRepository;
    private final MvProductRankWeeklyJpaRepository mvRepository;

    @Autowired
    WeeklyRankingJobE2ETest(
        DailyProductMetricsJpaRepository dailyRepository,
        MvProductRankWeeklyJpaRepository mvRepository
    ) {
        this.dailyRepository = dailyRepository;
        this.mvRepository = mvRepository;
    }

    @AfterEach
    void tearDown() {
        mvRepository.deleteAllInBatch();
        dailyRepository.deleteAllInBatch();
    }

    @DisplayName("주간 잡을 실행하면 해당 주(2026-W30)의 daily 를 상품별로 집계해 score 내림차순 rank 로 MV 에 적재한다.")
    @Test
    void aggregatesWeekDailyIntoMv_rankedByScoreDesc() throws Exception {
        // given : 2026-W30 = 2026-07-20(월) ~ 2026-07-26(일)
        // 상품 1: sales 10          → 10*0.7 = 7.0
        // 상품 2: like 10 + view 10 → 10*0.2 + 10*0.1 = 3.0 (서로 다른 날짜 합산)
        // 상품 3: view 5            → 5*0.1 = 0.5
        // 상품 9: 주 밖(2026-07-19) → 집계 제외
        dailyRepository.saveAll(List.of(
            DailyProductMetrics.of(1L, LocalDate.of(2026, 7, 21), 0, 10, 0),
            DailyProductMetrics.of(2L, LocalDate.of(2026, 7, 22), 10, 0, 0),
            DailyProductMetrics.of(2L, LocalDate.of(2026, 7, 23), 0, 0, 10),
            DailyProductMetrics.of(3L, LocalDate.of(2026, 7, 20), 0, 0, 5),
            DailyProductMetrics.of(9L, LocalDate.of(2026, 7, 19), 0, 100, 0)
        ));

        // when : 그 주에 속한 아무 하루(화요일)로 잡 실행
        jobLauncherTestUtils.setJob(job);
        var jobParameters = new JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.of(2026, 7, 21))
            .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        List<MvProductRankWeekly> ranked = mvRepository.findByYearWeekOrderByRankNoAsc("2026-W30");
        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(ranked).hasSize(3),
            () -> assertThat(ranked.get(0).getProductId()).isEqualTo(1L),
            () -> assertThat(ranked.get(0).getRankNo()).isEqualTo(1),
            () -> assertThat(ranked.get(0).getScore()).isEqualTo(7.0),
            () -> assertThat(ranked.get(1).getProductId()).isEqualTo(2L),
            () -> assertThat(ranked.get(1).getRankNo()).isEqualTo(2),
            () -> assertThat(ranked.get(1).getScore()).isEqualTo(3.0),
            () -> assertThat(ranked.get(2).getProductId()).isEqualTo(3L),
            () -> assertThat(ranked.get(2).getRankNo()).isEqualTo(3)
        );
    }

    @DisplayName("같은 주를 두 번 집계해도 MV 는 그 주 행을 덮어써 중복 없이 최신 상태만 남긴다 — 재집계 멱등.")
    @Test
    void reaggregatesIdempotently_whenSameWeekRunTwice() throws Exception {
        // given : 첫 집계 대상 daily
        dailyRepository.saveAll(List.of(
            DailyProductMetrics.of(1L, LocalDate.of(2026, 7, 21), 0, 10, 0),
            DailyProductMetrics.of(2L, LocalDate.of(2026, 7, 22), 10, 0, 0)
        ));
        jobLauncherTestUtils.setJob(job);
        runWeekly(LocalDate.of(2026, 7, 21), 1L);

        // when : 같은 주(같은 requestDate)를 다시 집계 — 별개 실행 인스턴스로 구분
        runWeekly(LocalDate.of(2026, 7, 21), 2L);

        // then : 유령 행 없이 상품 2건만 유지
        assertThat(mvRepository.findByYearWeekOrderByRankNoAsc("2026-W30")).hasSize(2);
    }

    private void runWeekly(LocalDate requestDate, long runId) throws Exception {
        var jobParameters = new JobParametersBuilder()
            .addLocalDate("requestDate", requestDate)
            .addLong("run.id", runId)
            .toJobParameters();
        jobLauncherTestUtils.launchJob(jobParameters);
    }
}
