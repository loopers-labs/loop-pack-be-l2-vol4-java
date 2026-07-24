package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.ProductMetricAggregate;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.ranking.application.RankingCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRankingScoreProcessorTest {

    private static final ProductRankingSnapshotKey SNAPSHOT_KEY = new ProductRankingSnapshotKey(
        RankingPeriod.WEEKLY,
        LocalDate.of(2026, 7, 19),
        1
    );
    private static final RankingScorePolicy STORED_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);

    @Mock
    private ProductRankingSnapshotRepository snapshotRepository;

    @DisplayName("Snapshot에 고정된 정책으로 여러 상품의 점수를 계산하고 헤더는 한 번만 조회한다.")
    @Test
    void calculatesScoresWithStoredSnapshotPolicy() throws Exception {
        // arrange
        when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(
            Optional.of(incompleteSnapshot(STORED_POLICY))
        );
        ProductRankingScoreProcessor processor = processor();
        ProductMetricAggregate first = new ProductMetricAggregate(101L, 30, 1, 30_000);
        ProductMetricAggregate second = new ProductMetricAggregate(202L, 30, 0, 0);

        // act
        RankingCandidate firstCandidate = processor.process(first);
        RankingCandidate secondCandidate = processor.process(second);

        // assert
        assertAll(
            () -> assertThat(firstCandidate.snapshotId()).isEqualTo(10L),
            () -> assertThat(firstCandidate.productId()).isEqualTo(101L),
            () -> assertThat(firstCandidate.score()).isCloseTo(5.3, offset(1.0e-10)),
            () -> assertThat(secondCandidate.snapshotId()).isEqualTo(10L),
            () -> assertThat(secondCandidate.productId()).isEqualTo(202L),
            () -> assertThat(secondCandidate.score()).isCloseTo(3.0, offset(1.0e-10)),
            () -> verify(snapshotRepository, times(1)).findBy(SNAPSHOT_KEY)
        );
    }

    @DisplayName("Snapshot 헤더가 없으면 현재 설정으로 대신 계산하지 않는다.")
    @Test
    void rejectsMissingSnapshot() {
        // arrange
        when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.empty());

        // act & assert
        assertThatThrownBy(this::processor)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("snapshot");
    }

    @DisplayName("이미 완료된 Snapshot의 후보를 다시 계산하지 않는다.")
    @Test
    void rejectsCompletedSnapshot() {
        // arrange
        ProductRankingSnapshotHeader completed = new ProductRankingSnapshotHeader(
            10L,
            SNAPSHOT_KEY,
            STORED_POLICY,
            Instant.parse("2026-07-20T01:00:00Z"),
            Instant.parse("2026-07-20T02:00:00Z")
        );
        when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(completed));

        // act & assert
        assertThatThrownBy(this::processor)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("completed");
    }

    @DisplayName("계산 결과가 유한한 숫자가 아니면 후보를 만들지 않는다.")
    @Test
    void rejectsNonFiniteScore() {
        // arrange
        RankingScorePolicy overflowPolicy =
            new RankingScorePolicy(Double.MAX_VALUE, 0.2, 0.7, 10_000);
        when(snapshotRepository.findBy(SNAPSHOT_KEY)).thenReturn(
            Optional.of(incompleteSnapshot(overflowPolicy))
        );
        ProductRankingScoreProcessor processor = processor();
        ProductMetricAggregate aggregate = new ProductMetricAggregate(101L, 2, 0, 0);

        // act & assert
        assertThatThrownBy(() -> processor.process(aggregate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("score");
    }

    private ProductRankingScoreProcessor processor() {
        return new ProductRankingScoreProcessor(
            snapshotRepository,
            "WEEKLY",
            "20260719",
            1L
        );
    }

    private ProductRankingSnapshotHeader incompleteSnapshot(RankingScorePolicy scorePolicy) {
        return new ProductRankingSnapshotHeader(
            10L,
            SNAPSHOT_KEY,
            scorePolicy,
            Instant.parse("2026-07-20T01:00:00Z"),
            null
        );
    }
}
