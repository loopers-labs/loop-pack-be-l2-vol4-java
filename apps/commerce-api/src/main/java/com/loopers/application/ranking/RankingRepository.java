package com.loopers.application.ranking;

import com.loopers.ranking.RankingPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;

public interface RankingRepository {
    List<RankedProduct> findRankedProducts(LocalDate date, int page, int size);
    List<RankedProduct> findRankedProducts(RankingPeriod period, LocalDate date, int page, int size);
    OptionalLong findRank(LocalDate date, Long productId);
}
