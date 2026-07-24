package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingEntry;
import com.loopers.application.ranking.RankingSnapshotRepository;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RankingSnapshotRepositoryImpl implements RankingSnapshotRepository {

    private static final DateTimeFormatter DATE_KEY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final ProductRankWeeklyMvJpaRepository weeklyRepository;
    private final ProductRankMonthlyMvJpaRepository monthlyRepository;

    @Override
    public List<RankingEntry> findRankings(RankingPeriod period, String startDate, String endDate, int page, int size) {
        LocalDate rankStartDate = LocalDate.parse(startDate, DATE_KEY_FORMATTER);
        LocalDate rankEndDate = LocalDate.parse(endDate, DATE_KEY_FORMATTER);
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), size);

        if (period == RankingPeriod.WEEKLY) {
            return weeklyRepository.findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
                    rankStartDate,
                    rankEndDate,
                    pageable
                )
                .stream()
                .map(entity -> new RankingEntry(entity.getProductId(), entity.getRankNo(), entity.getScore()))
                .toList();
        }

        return monthlyRepository.findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
                rankStartDate,
                rankEndDate,
                pageable
            )
            .stream()
            .map(entity -> new RankingEntry(entity.getProductId(), entity.getRankNo(), entity.getScore()))
            .toList();
    }

    @Override
    public long count(RankingPeriod period, String startDate, String endDate) {
        LocalDate rankStartDate = LocalDate.parse(startDate, DATE_KEY_FORMATTER);
        LocalDate rankEndDate = LocalDate.parse(endDate, DATE_KEY_FORMATTER);

        if (period == RankingPeriod.WEEKLY) {
            return weeklyRepository.countByRankStartDateAndRankEndDateAndActiveTrue(rankStartDate, rankEndDate);
        }

        return monthlyRepository.countByRankStartDateAndRankEndDateAndActiveTrue(rankStartDate, rankEndDate);
    }
}
