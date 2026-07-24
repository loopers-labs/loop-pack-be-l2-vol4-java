package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.ProductRankingCleanupService;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteObsoleteProductRankingCandidatesTaskletTest {

    private static final long TARGET_SNAPSHOT_ID = 120L;
    private static final ProductRankingSnapshotHeader TARGET =
        new ProductRankingSnapshotHeader(
            TARGET_SNAPSHOT_ID,
            new ProductRankingSnapshotKey(
                RankingPeriod.WEEKLY,
                LocalDate.of(2026, 7, 12),
                1
            ),
            new RankingScorePolicy(0.1, 0.2, 0.7, 10_000),
            Instant.parse("2026-07-13T02:00:00Z"),
            Instant.parse("2026-07-13T02:10:00Z")
        );

    @Mock
    private ProductRankingCleanupService cleanupService;

    @DisplayName("한 번에 1,000건을 삭제하면 다음 트랜잭션에서 삭제를 계속한다.")
    @Test
    void continuesWhenDeleteBatchIsFull() {
        // arrange
        when(cleanupService.validateCleanupTarget(TARGET_SNAPSHOT_ID))
            .thenReturn(TARGET);
        when(cleanupService.deleteCandidates(TARGET, 1_000))
            .thenReturn(1_000, 17);
        DeleteObsoleteProductRankingCandidatesTasklet tasklet =
            new DeleteObsoleteProductRankingCandidatesTasklet(
                cleanupService,
                TARGET_SNAPSHOT_ID
            );

        // act
        RepeatStatus first = tasklet.execute(null, null);
        RepeatStatus second = tasklet.execute(null, null);

        // assert
        assertAll(
            () -> assertThat(first).isEqualTo(RepeatStatus.CONTINUABLE),
            () -> assertThat(second).isEqualTo(RepeatStatus.FINISHED),
            () -> verify(cleanupService)
                .validateCleanupTarget(TARGET_SNAPSHOT_ID),
            () -> verify(cleanupService, times(2))
                .deleteCandidates(TARGET, 1_000)
        );
    }

    @DisplayName("삭제할 후보가 없으면 멱등하게 종료한다.")
    @Test
    void finishesWhenNoCandidateIsDeleted() {
        // arrange
        when(cleanupService.validateCleanupTarget(TARGET_SNAPSHOT_ID))
            .thenReturn(TARGET);
        when(cleanupService.deleteCandidates(TARGET, 1_000))
            .thenReturn(0);
        DeleteObsoleteProductRankingCandidatesTasklet tasklet =
            new DeleteObsoleteProductRankingCandidatesTasklet(
                cleanupService,
                TARGET_SNAPSHOT_ID
            );

        // act
        RepeatStatus result = tasklet.execute(null, null);

        // assert
        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }
}
