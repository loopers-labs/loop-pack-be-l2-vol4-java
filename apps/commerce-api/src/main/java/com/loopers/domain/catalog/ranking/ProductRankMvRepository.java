package com.loopers.domain.catalog.ranking;

import java.time.LocalDate;
import java.util.List;

public interface ProductRankMvRepository {

    List<RankingRepository.Entry> findRankings(
        RankingPeriod period,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        int page,
        int size
    );

    long count(RankingPeriod period, LocalDate periodStartDate, LocalDate periodEndDate);
}
