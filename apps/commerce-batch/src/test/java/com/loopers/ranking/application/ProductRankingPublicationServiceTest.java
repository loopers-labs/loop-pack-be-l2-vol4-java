package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRankingPublicationServiceTest {

    private static final ProductRankingSnapshotKey SNAPSHOT_KEY = new ProductRankingSnapshotKey(
        RankingPeriod.WEEKLY,
        LocalDate.of(2026, 7, 19),
        1
    );
    private static final RankingScorePolicy SCORE_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-20T02:10:00Z");
    private static final Clock CLOCK = Clock.fixed(COMPLETED_AT, ZoneOffset.UTC);

    @Mock
    private ProductRankingSnapshotRepository snapshotRepository;

    @Mock
    private ProductRankingCandidateRepository candidateRepository;

    @Mock
    private ProductRankingResultRepository resultRepository;

    @Mock
    private ProductRankingSourceQuery sourceQuery;

    @DisplayName("상품 랭킹 Snapshot을 공개할 때")
    @Nested
    class Publish {

        @DisplayName("원천과 후보가 일치하면 DB 조회 순서대로 순위를 부여하고 완료 처리한다.")
        @Test
        void publishesValidatedCandidates() {
            // arrange
            ProductRankingSnapshotHeader snapshot = incompleteSnapshot();
            List<RankingCandidate> topCandidates = List.of(
                new RankingCandidate(snapshot.id(), 303L, 10.0),
                new RankingCandidate(snapshot.id(), 101L, 5.3),
                new RankingCandidate(snapshot.id(), 202L, 3.0)
            );
            List<ProductRankingAssignment> expectedRankings = List.of(
                new ProductRankingAssignment(303L, 10.0, 1),
                new ProductRankingAssignment(101L, 5.3, 2),
                new ProductRankingAssignment(202L, 3.0, 3)
            );
            when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(snapshot));
            when(sourceQuery.countProductsWithMetrics(
                SNAPSHOT_KEY.periodStart(),
                SNAPSHOT_KEY.aggregationEndDate()
            )).thenReturn(3L);
            when(candidateRepository.countCandidates(snapshot.id()))
                .thenReturn(3L);
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                1
            ))
                .thenReturn(List.of());
            when(candidateRepository.findTopCandidates(
                snapshot.id(),
                ProductRankingPublicationService.TOP_LIMIT
            )).thenReturn(topCandidates);
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                ProductRankingPublicationService.VERIFICATION_LIMIT
            )).thenReturn(expectedRankings);
            when(snapshotRepository.completeIfIncomplete(snapshot.id(), COMPLETED_AT))
                .thenReturn(true);
            ProductRankingPublicationService service = service();

            // act
            service.publish(SNAPSHOT_KEY);

            // assert
            verify(resultRepository).insertAll(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                expectedRankings
            );
            verify(snapshotRepository).completeIfIncomplete(snapshot.id(), COMPLETED_AT);
        }

        @DisplayName("원천 상품 수와 후보 수가 다르면 불완전한 결과를 공개하지 않는다.")
        @Test
        void rejectsCandidateCountMismatch() {
            // arrange
            ProductRankingSnapshotHeader snapshot = incompleteSnapshot();
            when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(snapshot));
            when(sourceQuery.countProductsWithMetrics(
                SNAPSHOT_KEY.periodStart(),
                SNAPSHOT_KEY.aggregationEndDate()
            )).thenReturn(3L);
            when(candidateRepository.countCandidates(snapshot.id()))
                .thenReturn(2L);
            ProductRankingPublicationService service = service();

            // act & assert
            assertThatThrownBy(() -> service.publish(SNAPSHOT_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source")
                .hasMessageContaining("candidate");
            verify(resultRepository, never()).insertAll(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList()
            );
            verify(snapshotRepository, never()).completeIfIncomplete(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
            );
        }

        @DisplayName("미완성 Snapshot에 이미 순위가 있으면 외부 변경으로 보고 공개하지 않는다.")
        @Test
        void rejectsExistingRanksInIncompleteSnapshot() {
            // arrange
            ProductRankingSnapshotHeader snapshot = incompleteSnapshot();
            when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(snapshot));
            when(sourceQuery.countProductsWithMetrics(
                SNAPSHOT_KEY.periodStart(),
                SNAPSHOT_KEY.aggregationEndDate()
            )).thenReturn(1L);
            when(candidateRepository.countCandidates(snapshot.id()))
                .thenReturn(1L);
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                1
            )).thenReturn(List.of(new ProductRankingAssignment(101L, 5.3, 1)));
            ProductRankingPublicationService service = service();

            // act & assert
            assertThatThrownBy(() -> service.publish(SNAPSHOT_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rank");
            verify(candidateRepository, never()).findTopCandidates(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt()
            );
            verify(snapshotRepository, never()).completeIfIncomplete(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
            );
        }

        @DisplayName("원천과 후보가 모두 0건이면 완료된 Empty Snapshot으로 공개한다.")
        @Test
        void publishesEmptySnapshot() {
            // arrange
            ProductRankingSnapshotHeader snapshot = incompleteSnapshot();
            when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(snapshot));
            when(sourceQuery.countProductsWithMetrics(
                SNAPSHOT_KEY.periodStart(),
                SNAPSHOT_KEY.aggregationEndDate()
            )).thenReturn(0L);
            when(candidateRepository.countCandidates(snapshot.id()))
                .thenReturn(0L);
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                1
            ))
                .thenReturn(List.of());
            when(candidateRepository.findTopCandidates(
                snapshot.id(),
                ProductRankingPublicationService.TOP_LIMIT
            )).thenReturn(List.of());
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                ProductRankingPublicationService.VERIFICATION_LIMIT
            )).thenReturn(List.of());
            when(snapshotRepository.completeIfIncomplete(snapshot.id(), COMPLETED_AT))
                .thenReturn(true);
            ProductRankingPublicationService service = service();

            // act
            service.publish(SNAPSHOT_KEY);

            // assert
            verify(resultRepository).insertAll(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                List.of()
            );
            verify(snapshotRepository).completeIfIncomplete(snapshot.id(), COMPLETED_AT);
        }

        @DisplayName("이미 완료된 Snapshot은 공개 순위의 최소 불변식만 확인하고 멱등하게 종료한다.")
        @Test
        void validatesCompletedSnapshotWithoutRepublishing() {
            // arrange
            ProductRankingSnapshotHeader snapshot = completedSnapshot();
            when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(snapshot));
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                ProductRankingPublicationService.VERIFICATION_LIMIT
            )).thenReturn(List.of(
                new ProductRankingAssignment(303L, 10.0, 1),
                new ProductRankingAssignment(101L, 5.3, 2),
                new ProductRankingAssignment(202L, 3.0, 3)
            ));
            ProductRankingPublicationService service = service();

            // act
            service.publish(SNAPSHOT_KEY);

            // assert
            verify(sourceQuery, never()).countProductsWithMetrics(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
            );
            verify(candidateRepository, never()).countCandidates(
                org.mockito.ArgumentMatchers.anyLong()
            );
            verify(resultRepository, never()).insertAll(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList()
            );
            verify(snapshotRepository, never()).completeIfIncomplete(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
            );
        }

        @DisplayName("완료된 Snapshot의 순위가 연속되지 않으면 손상된 결과로 판단한다.")
        @Test
        void rejectsInvalidCompletedSnapshot() {
            // arrange
            ProductRankingSnapshotHeader snapshot = completedSnapshot();
            when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(snapshot));
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                ProductRankingPublicationService.VERIFICATION_LIMIT
            )).thenReturn(List.of(
                new ProductRankingAssignment(303L, 10.0, 1),
                new ProductRankingAssignment(101L, 5.3, 3)
            ));
            ProductRankingPublicationService service = service();

            // act & assert
            assertThatThrownBy(() -> service.publish(SNAPSHOT_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rank");
            verify(snapshotRepository, never()).completeIfIncomplete(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
            );
        }

        @DisplayName("순위 저장 결과가 예상 Top 목록과 다르면 Snapshot을 완료하지 않는다.")
        @Test
        void rejectsStoredRankingMismatch() {
            // arrange
            ProductRankingSnapshotHeader snapshot = incompleteSnapshot();
            List<RankingCandidate> topCandidates = List.of(
                new RankingCandidate(snapshot.id(), 101L, 5.3)
            );
            when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(snapshot));
            when(sourceQuery.countProductsWithMetrics(
                SNAPSHOT_KEY.periodStart(),
                SNAPSHOT_KEY.aggregationEndDate()
            )).thenReturn(1L);
            when(candidateRepository.countCandidates(snapshot.id()))
                .thenReturn(1L);
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                1
            )).thenReturn(List.of());
            when(candidateRepository.findTopCandidates(
                snapshot.id(),
                ProductRankingPublicationService.TOP_LIMIT
            )).thenReturn(topCandidates);
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                ProductRankingPublicationService.VERIFICATION_LIMIT
            )).thenReturn(List.of(new ProductRankingAssignment(202L, 3.0, 1)));
            ProductRankingPublicationService service = service();

            // act & assert
            assertThatThrownBy(() -> service.publish(SNAPSHOT_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published");
            verify(snapshotRepository, never()).completeIfIncomplete(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
            );
        }

        @DisplayName("완료 갱신이 다른 실행과 충돌하면 공개를 실패시킨다.")
        @Test
        void rejectsCompletionConflict() {
            // arrange
            ProductRankingSnapshotHeader snapshot = incompleteSnapshot();
            List<RankingCandidate> topCandidates = List.of(
                new RankingCandidate(snapshot.id(), 101L, 5.3)
            );
            List<ProductRankingAssignment> rankings = List.of(
                new ProductRankingAssignment(101L, 5.3, 1)
            );
            when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(snapshot));
            when(sourceQuery.countProductsWithMetrics(
                SNAPSHOT_KEY.periodStart(),
                SNAPSHOT_KEY.aggregationEndDate()
            )).thenReturn(1L);
            when(candidateRepository.countCandidates(snapshot.id()))
                .thenReturn(1L);
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                1
            ))
                .thenReturn(List.of());
            when(candidateRepository.findTopCandidates(
                snapshot.id(),
                ProductRankingPublicationService.TOP_LIMIT
            )).thenReturn(topCandidates);
            when(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                snapshot.id(),
                ProductRankingPublicationService.VERIFICATION_LIMIT
            )).thenReturn(rankings);
            when(snapshotRepository.completeIfIncomplete(snapshot.id(), COMPLETED_AT))
                .thenReturn(false);
            ProductRankingPublicationService service = service();

            // act & assert
            assertThatThrownBy(() -> service.publish(SNAPSHOT_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completion");
        }
    }

    private ProductRankingPublicationService service() {
        return new ProductRankingPublicationService(
            snapshotRepository,
            candidateRepository,
            resultRepository,
            sourceQuery,
            CLOCK
        );
    }

    private ProductRankingSnapshotHeader incompleteSnapshot() {
        return new ProductRankingSnapshotHeader(
            10L,
            SNAPSHOT_KEY,
            SCORE_POLICY,
            Instant.parse("2026-07-20T02:00:00Z"),
            null
        );
    }

    private ProductRankingSnapshotHeader completedSnapshot() {
        return new ProductRankingSnapshotHeader(
            10L,
            SNAPSHOT_KEY,
            SCORE_POLICY,
            Instant.parse("2026-07-20T02:00:00Z"),
            COMPLETED_AT
        );
    }
}
