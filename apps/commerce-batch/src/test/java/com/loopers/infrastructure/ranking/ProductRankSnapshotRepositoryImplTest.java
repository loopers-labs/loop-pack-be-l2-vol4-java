package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductRankSnapshotRepositoryImplTest {

    @Test
    @DisplayName("WEEKLY Snapshot은 주간 MV 테이블에 저장한다.")
    void saveAll_WhenWeekly_ShouldSaveWeeklySnapshots() {
        ProductRankWeeklyMvJpaRepository weeklyRepository = mock(ProductRankWeeklyMvJpaRepository.class);
        ProductRankMonthlyMvJpaRepository monthlyRepository = mock(ProductRankMonthlyMvJpaRepository.class);
        ProductRankSnapshotRepositoryImpl repository = new ProductRankSnapshotRepositoryImpl(
            weeklyRepository,
            monthlyRepository
        );
        ProductRankSnapshot snapshot = ProductRankSnapshot.createInactive(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            1L,
            10L,
            1,
            98.1
        );

        repository.saveAll(List.of(snapshot));

        verify(weeklyRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("MONTHLY Snapshot은 월간 MV 테이블에 저장한다.")
    void saveAll_WhenMonthly_ShouldSaveMonthlySnapshots() {
        ProductRankWeeklyMvJpaRepository weeklyRepository = mock(ProductRankWeeklyMvJpaRepository.class);
        ProductRankMonthlyMvJpaRepository monthlyRepository = mock(ProductRankMonthlyMvJpaRepository.class);
        ProductRankSnapshotRepositoryImpl repository = new ProductRankSnapshotRepositoryImpl(
            weeklyRepository,
            monthlyRepository
        );
        ProductRankSnapshot snapshot = ProductRankSnapshot.createInactive(
            RankingPeriod.MONTHLY,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            2L,
            20L,
            1,
            120.5
        );

        repository.saveAll(List.of(snapshot));

        verify(monthlyRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Snapshot 검증은 rank 또는 product 중복 존재 여부를 확인한다.")
    void existsInvalidSnapshot_ShouldCheckDuplicateRankOrProduct() {
        ProductRankWeeklyMvJpaRepository weeklyRepository = mock(ProductRankWeeklyMvJpaRepository.class);
        ProductRankMonthlyMvJpaRepository monthlyRepository = mock(ProductRankMonthlyMvJpaRepository.class);
        ProductRankSnapshotRepositoryImpl repository = new ProductRankSnapshotRepositoryImpl(
            weeklyRepository,
            monthlyRepository
        );
        when(weeklyRepository.existsDuplicateRankNo(1L)).thenReturn(false);
        when(weeklyRepository.existsDuplicateProductId(1L)).thenReturn(true);

        assertThat(repository.existsInvalidSnapshot(1L)).isTrue();
    }

    @Test
    @DisplayName("active 전환은 주간/월간 저장소에 위임한다.")
    void activateSnapshot_ShouldDelegateToBothRepositories() {
        ProductRankWeeklyMvJpaRepository weeklyRepository = mock(ProductRankWeeklyMvJpaRepository.class);
        ProductRankMonthlyMvJpaRepository monthlyRepository = mock(ProductRankMonthlyMvJpaRepository.class);
        ProductRankSnapshotRepositoryImpl repository = new ProductRankSnapshotRepositoryImpl(
            weeklyRepository,
            monthlyRepository
        );

        repository.deactivateActiveSnapshot(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26)
        );
        repository.activateSnapshot(1L);

        verify(weeklyRepository).deactivateActiveSnapshot(
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26)
        );
        verify(weeklyRepository).activateSnapshot(1L);
        verify(monthlyRepository).activateSnapshot(1L);
    }
}
