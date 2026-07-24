package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class RankingService {

    private final RankingRepository rankingRepository;
    private final ProductRankMvRepository productRankMvRepository;

    public List<RankingEntry> getRankings(RankingQueryCondition condition) {
        return rankingRepository.findRankings(condition.date(), condition.start(), condition.end());
    }

    public long countRankings(LocalDate date) {
        return rankingRepository.countRankings(date);
    }

    public List<RankingEntry> getMvRankings(RankingMvQueryCondition condition) {
        return productRankMvRepository.findRankings(condition.period(), condition.aggregateDate(), condition.page(), condition.size());
    }

    public long countMvRankings(RankingMvQueryCondition condition) {
        return productRankMvRepository.count(condition.period(), condition.aggregateDate());
    }

    public ProductRank getRank(LocalDate date, Long productId) {
        return rankingRepository.findRank(date, productId);
    }

    public List<RankingEntry> getHourlyRankings(RankingHourlyQueryCondition condition) {
        return rankingRepository.findHourlyRankings(condition.dateTime(), condition.start(), condition.end());
    }

    public long countHourlyRankings(RankingHourlyQueryCondition condition) {
        return rankingRepository.countHourlyRankings(condition.dateTime());
    }
}
