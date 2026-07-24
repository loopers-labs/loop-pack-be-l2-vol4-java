package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingSnapshotRepositoryImplTest {

    @Test
    @DisplayName("WEEKLY 랭킹은 주간 MV 저장소에서 active Snapshot을 조회한다.")
    void findRankings_WhenWeekly_ShouldUseWeeklyRepository() {
        ProductRankWeeklyMvJpaRepository weeklyRepository = mock(ProductRankWeeklyMvJpaRepository.class);
        ProductRankMonthlyMvJpaRepository monthlyRepository = mock(ProductRankMonthlyMvJpaRepository.class);
        RankingSnapshotRepositoryImpl repository = new RankingSnapshotRepositoryImpl(
            weeklyRepository,
            monthlyRepository
        );
        when(weeklyRepository.findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            PageRequest.of(0, 20)
        )).thenReturn(List.of(new ProductRankWeeklyMvJpaEntity(1L, 1, 30.0)));

        List<RankingEntry> result = repository.findRankings(
            RankingPeriod.WEEKLY,
            "20260720",
            "20260726",
            1,
            20
        );

        assertThat(result).containsExactly(new RankingEntry(1L, 1, 30.0));
        verify(weeklyRepository).findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            PageRequest.of(0, 20)
        );
    }

    @Test
    @DisplayName("MONTHLY 랭킹은 월간 MV 저장소에서 active Snapshot을 조회한다.")
    void findRankings_WhenMonthly_ShouldUseMonthlyRepository() {
        ProductRankWeeklyMvJpaRepository weeklyRepository = mock(ProductRankWeeklyMvJpaRepository.class);
        ProductRankMonthlyMvJpaRepository monthlyRepository = mock(ProductRankMonthlyMvJpaRepository.class);
        RankingSnapshotRepositoryImpl repository = new RankingSnapshotRepositoryImpl(
            weeklyRepository,
            monthlyRepository
        );
        when(monthlyRepository.findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PageRequest.of(1, 10)
        )).thenReturn(List.of(new ProductRankMonthlyMvJpaEntity(2L, 11, 20.0)));

        List<RankingEntry> result = repository.findRankings(
            RankingPeriod.MONTHLY,
            "20260701",
            "20260731",
            2,
            10
        );

        assertThat(result).containsExactly(new RankingEntry(2L, 11, 20.0));
        verify(monthlyRepository).findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PageRequest.of(1, 10)
        );
    }

    @Test
    @DisplayName("count는 period에 맞는 MV 저장소로 위임한다.")
    void count_ShouldDelegateByPeriod() {
        ProductRankWeeklyMvJpaRepository weeklyRepository = mock(ProductRankWeeklyMvJpaRepository.class);
        ProductRankMonthlyMvJpaRepository monthlyRepository = mock(ProductRankMonthlyMvJpaRepository.class);
        RankingSnapshotRepositoryImpl repository = new RankingSnapshotRepositoryImpl(
            weeklyRepository,
            monthlyRepository
        );
        when(monthlyRepository.countByRankStartDateAndRankEndDateAndActiveTrue(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)
        )).thenReturn(100L);

        long result = repository.count(RankingPeriod.MONTHLY, "20260701", "20260731");

        assertThat(result).isEqualTo(100L);
    }
}
