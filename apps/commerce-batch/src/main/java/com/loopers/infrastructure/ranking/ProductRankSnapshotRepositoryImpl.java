package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.ProductRankSnapshotRepository;
import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductRankSnapshotRepositoryImpl implements ProductRankSnapshotRepository {

    private final ProductRankWeeklyMvJpaRepository weeklyRepository;
    private final ProductRankMonthlyMvJpaRepository monthlyRepository;

    @Override
    public void saveAll(List<ProductRankSnapshot> snapshots) {
        List<ProductRankWeeklyMvJpaEntity> weeklySnapshots = snapshots.stream()
            .filter(snapshot -> snapshot.getPeriod() == RankingPeriod.WEEKLY)
            .map(ProductRankWeeklyMvJpaEntity::from)
            .toList();
        List<ProductRankMonthlyMvJpaEntity> monthlySnapshots = snapshots.stream()
            .filter(snapshot -> snapshot.getPeriod() == RankingPeriod.MONTHLY)
            .map(ProductRankMonthlyMvJpaEntity::from)
            .toList();

        if (!weeklySnapshots.isEmpty()) {
            weeklyRepository.saveAll(weeklySnapshots);
        }
        if (!monthlySnapshots.isEmpty()) {
            monthlyRepository.saveAll(monthlySnapshots);
        }
    }

    @Override
    public boolean existsInvalidSnapshot(Long batchRunId) {
        return weeklyRepository.existsDuplicateRankNo(batchRunId)
            || weeklyRepository.existsDuplicateProductId(batchRunId)
            || monthlyRepository.existsDuplicateRankNo(batchRunId)
            || monthlyRepository.existsDuplicateProductId(batchRunId);
    }

    @Override
    public void deactivateActiveSnapshot(RankingPeriod period, LocalDate rankStartDate, LocalDate rankEndDate) {
        if (period == RankingPeriod.WEEKLY) {
            weeklyRepository.deactivateActiveSnapshot(rankStartDate, rankEndDate);
            return;
        }
        if (period == RankingPeriod.MONTHLY) {
            monthlyRepository.deactivateActiveSnapshot(rankStartDate, rankEndDate);
        }
    }

    @Override
    public void activateSnapshot(Long batchRunId) {
        weeklyRepository.activateSnapshot(batchRunId);
        monthlyRepository.activateSnapshot(batchRunId);
    }
}
