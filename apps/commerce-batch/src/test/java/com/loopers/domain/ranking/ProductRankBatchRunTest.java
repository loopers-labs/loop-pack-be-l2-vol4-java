package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRankBatchRunTest {

    @Test
    @DisplayName("랭킹 배치 실행 이력은 RUNNING 상태로 생성된다.")
    void create_ShouldCreateRunningBatchRun() {
        ProductRankBatchRun batchRun = ProductRankBatchRun.create(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26)
        );

        assertThat(batchRun.getPeriod()).isEqualTo(RankingPeriod.WEEKLY);
        assertThat(batchRun.getRankStartDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(batchRun.getRankEndDate()).isEqualTo(LocalDate.of(2026, 7, 26));
        assertThat(batchRun.getStatus()).isEqualTo(BatchRunStatus.RUNNING);
    }

    @Test
    @DisplayName("랭킹 배치 실행 이력은 완료 또는 실패 상태로 전환할 수 있다.")
    void markCompletedAndFailed_ShouldChangeStatus() {
        ProductRankBatchRun completedRun = ProductRankBatchRun.create(
            RankingPeriod.MONTHLY,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)
        );
        ProductRankBatchRun failedRun = ProductRankBatchRun.create(
            RankingPeriod.MONTHLY,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)
        );

        completedRun.markCompleted();
        failedRun.markFailed();

        assertThat(completedRun.getStatus()).isEqualTo(BatchRunStatus.COMPLETED);
        assertThat(failedRun.getStatus()).isEqualTo(BatchRunStatus.FAILED);
    }
}
