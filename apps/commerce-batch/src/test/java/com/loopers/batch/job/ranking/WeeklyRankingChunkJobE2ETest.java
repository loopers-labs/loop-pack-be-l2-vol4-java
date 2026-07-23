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
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingChunkJobConfig.JOB_NAME)
class WeeklyRankingChunkJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingChunkJobConfig.JOB_NAME)
    private Job job;

    private final DailyProductMetricsJpaRepository dailyRepository;
    private final MvProductRankWeeklyJpaRepository mvRepository;

    @Autowired
    WeeklyRankingChunkJobE2ETest(
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

    @DisplayName("Chunk 잡을 실행하면 해당 주(2026-W30)의 daily 를 Reader→Processor→Writer 로 집계·rank 부여해 MV 에 적재한다.")
    @Test
    void aggregatesWeekDailyViaChunk_rankedByScoreDesc() throws Exception {
        // given : 2026-W30 = 2026-07-20 ~ 2026-07-26
        // 상품 1: sales 10          → 7.0 (rank 1)
        // 상품 2: like 10 + view 10 → 3.0 (rank 2, 서로 다른 날짜 합산)
        // 상품 3: view 5            → 0.5 (rank 3)
        // 상품 9: 주 밖(2026-07-19) → 제외
        dailyRepository.saveAll(List.of(
            DailyProductMetrics.of(1L, LocalDate.of(2026, 7, 21), 0, 10, 0),
            DailyProductMetrics.of(2L, LocalDate.of(2026, 7, 22), 10, 0, 0),
            DailyProductMetrics.of(2L, LocalDate.of(2026, 7, 23), 0, 0, 10),
            DailyProductMetrics.of(3L, LocalDate.of(2026, 7, 20), 0, 0, 5),
            DailyProductMetrics.of(9L, LocalDate.of(2026, 7, 19), 0, 100, 0)
        ));

        // when
        jobLauncherTestUtils.setJob(job);
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.of(2026, 7, 21))
            .toJobParameters());

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

    @DisplayName("선행 DELETE Step 이 그 주를 비운 뒤 Chunk 가 다시 채우므로, 재실행해도 유령 행 없이 최신 상태만 남는다.")
    @Test
    void reaggregatesIdempotently_whenSameWeekRunTwice() throws Exception {
        // given
        dailyRepository.saveAll(List.of(
            DailyProductMetrics.of(1L, LocalDate.of(2026, 7, 21), 0, 10, 0),
            DailyProductMetrics.of(2L, LocalDate.of(2026, 7, 22), 10, 0, 0)
        ));
        jobLauncherTestUtils.setJob(job);
        runChunk(1L);

        // when
        runChunk(2L);

        // then
        assertThat(mvRepository.findByYearWeekOrderByRankNoAsc("2026-W30")).hasSize(2);
    }

    private void runChunk(long runId) throws Exception {
        jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.of(2026, 7, 21))
            .addLong("run.id", runId)
            .toJobParameters());
    }
}
