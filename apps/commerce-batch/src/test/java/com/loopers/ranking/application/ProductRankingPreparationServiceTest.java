package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRankingPreparationServiceTest {

    private static final ProductRankingSnapshotKey SNAPSHOT_KEY = new ProductRankingSnapshotKey(
        RankingPeriod.WEEKLY,
        LocalDate.of(2026, 7, 19),
        1
    );
    private static final RankingScorePolicy CURRENT_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);
    private static final Instant CREATED_AT = Instant.parse("2026-07-20T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

    @Mock
    private ProductRankingSnapshotRepository repository;

    @DisplayName("상품 랭킹 Snapshot을 준비할 때")
    @Nested
    class Prepare {

        @DisplayName("기존 헤더가 없으면 현재 점수 정책과 UTC 시각으로 미완성 헤더를 생성한다.")
        @Test
        void insertsIncompleteSnapshotWithCurrentPolicy_whenSnapshotDoesNotExist() {
            // arrange
            when(repository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.empty());
            ProductRankingPreparationService service =
                new ProductRankingPreparationService(repository, CURRENT_POLICY, CLOCK);

            // act
            service.prepare(SNAPSHOT_KEY);

            // assert
            ArgumentCaptor<NewProductRankingSnapshot> captor =
                ArgumentCaptor.forClass(NewProductRankingSnapshot.class);
            verify(repository).insert(captor.capture());
            assertThat(captor.getValue()).isEqualTo(
                new NewProductRankingSnapshot(SNAPSHOT_KEY, CURRENT_POLICY, CREATED_AT)
            );
        }

        @DisplayName("기존 미완성 헤더가 있으면 현재 설정으로 덮어쓰지 않고 그대로 재사용한다.")
        @Test
        void reusesExistingIncompleteSnapshot() {
            // arrange
            RankingScorePolicy storedPolicy = new RankingScorePolicy(0.3, 0.4, 0.5, 20_000);
            ProductRankingSnapshotHeader existing = new ProductRankingSnapshotHeader(
                10L,
                SNAPSHOT_KEY,
                storedPolicy,
                Instant.parse("2026-07-20T01:00:00Z"),
                null
            );
            when(repository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(existing));
            ProductRankingPreparationService service =
                new ProductRankingPreparationService(repository, CURRENT_POLICY, CLOCK);

            // act
            service.prepare(SNAPSHOT_KEY);

            // assert
            verify(repository, never()).insert(org.mockito.ArgumentMatchers.any());
        }

        @DisplayName("동일 키의 완료된 헤더가 있으면 공개 결과를 다시 준비하지 않는다.")
        @Test
        void rejectsExistingCompletedSnapshot() {
            // arrange
            ProductRankingSnapshotHeader completed = new ProductRankingSnapshotHeader(
                10L,
                SNAPSHOT_KEY,
                CURRENT_POLICY,
                Instant.parse("2026-07-20T01:00:00Z"),
                Instant.parse("2026-07-20T02:00:00Z")
            );
            when(repository.findBy(SNAPSHOT_KEY)).thenReturn(Optional.of(completed));
            ProductRankingPreparationService service =
                new ProductRankingPreparationService(repository, CURRENT_POLICY, CLOCK);

            // act & assert
            assertThatThrownBy(() -> service.prepare(SNAPSHOT_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed");
            verify(repository, never()).insert(org.mockito.ArgumentMatchers.any());
        }
    }
}
