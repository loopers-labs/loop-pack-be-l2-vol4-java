package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRankingCleanupServiceTest {

    private static final long TARGET_SNAPSHOT_ID = 120L;
    private static final ProductRankingSnapshotKey TARGET_KEY =
        new ProductRankingSnapshotKey(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 12),
            1
        );
    private static final RankingScorePolicy SCORE_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);
    private static final Instant CREATED_AT = Instant.parse("2026-07-13T02:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-13T02:10:00Z");

    @Mock
    private ProductRankingSnapshotRepository snapshotRepository;

    @Mock
    private ProductRankingCandidateRepository candidateRepository;

    @DisplayName("Cleanup 대상을 검증할 때")
    @Nested
    class ValidateCleanupTarget {

        @DisplayName("완료됐고 더 새로운 같은 기간 완료본이 있으면 삭제 대상으로 반환한다.")
        @Test
        void returnsEligibleTarget() {
            // arrange
            ProductRankingSnapshotHeader target = completedTarget();
            when(snapshotRepository.findById(TARGET_SNAPSHOT_ID))
                .thenReturn(Optional.of(target));
            when(snapshotRepository.existsNewerCompletedThan(TARGET_KEY))
                .thenReturn(true);
            ProductRankingCleanupService service = service();

            // act
            ProductRankingSnapshotHeader result =
                service.validateCleanupTarget(TARGET_SNAPSHOT_ID);

            // assert
            assertThat(result).isEqualTo(target);
        }

        @DisplayName("대상 스냅샷이 없으면 삭제 전에 실패한다.")
        @Test
        void rejectsMissingTarget() {
            // arrange
            when(snapshotRepository.findById(TARGET_SNAPSHOT_ID))
                .thenReturn(Optional.empty());
            ProductRankingCleanupService service = service();

            // act & assert
            assertThatThrownBy(
                () -> service.validateCleanupTarget(TARGET_SNAPSHOT_ID)
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
            verify(snapshotRepository, never())
                .existsNewerCompletedThan(TARGET_KEY);
        }

        @DisplayName("미완성 스냅샷이면 삭제 전에 실패한다.")
        @Test
        void rejectsIncompleteTarget() {
            // arrange
            ProductRankingSnapshotHeader incomplete = new ProductRankingSnapshotHeader(
                TARGET_SNAPSHOT_ID,
                TARGET_KEY,
                SCORE_POLICY,
                CREATED_AT,
                null
            );
            when(snapshotRepository.findById(TARGET_SNAPSHOT_ID))
                .thenReturn(Optional.of(incomplete));
            ProductRankingCleanupService service = service();

            // act & assert
            assertThatThrownBy(
                () -> service.validateCleanupTarget(TARGET_SNAPSHOT_ID)
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed");
            verify(snapshotRepository, never())
                .existsNewerCompletedThan(TARGET_KEY);
        }

        @DisplayName("같은 기간에 더 새로운 완료본이 없으면 최신 후보를 보호하고 실패한다.")
        @Test
        void rejectsLatestCompletedTarget() {
            // arrange
            when(snapshotRepository.findById(TARGET_SNAPSHOT_ID))
                .thenReturn(Optional.of(completedTarget()));
            when(snapshotRepository.existsNewerCompletedThan(TARGET_KEY))
                .thenReturn(false);
            ProductRankingCleanupService service = service();

            // act & assert
            assertThatThrownBy(
                () -> service.validateCleanupTarget(TARGET_SNAPSHOT_ID)
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("newer completed");
        }
    }

    @DisplayName("후보를 제한된 크기로 삭제할 때")
    @Nested
    class DeleteCandidates {

        @DisplayName("검증된 대상의 후보를 삭제하고 실제 삭제 건수를 반환한다.")
        @Test
        void deletesCandidatesFromTargetSnapshot() {
            // arrange
            ProductRankingSnapshotHeader target = completedTarget();
            when(candidateRepository.deleteCandidates(
                TARGET_SNAPSHOT_ID,
                1_000
            )).thenReturn(1_000);
            ProductRankingCleanupService service = service();

            // act
            int result = service.deleteCandidates(target, 1_000);

            // assert
            assertThat(result).isEqualTo(1_000);
        }
    }

    private ProductRankingCleanupService service() {
        return new ProductRankingCleanupService(
            snapshotRepository,
            candidateRepository
        );
    }

    private ProductRankingSnapshotHeader completedTarget() {
        return new ProductRankingSnapshotHeader(
            TARGET_SNAPSHOT_ID,
            TARGET_KEY,
            SCORE_POLICY,
            CREATED_AT,
            COMPLETED_AT
        );
    }
}
