package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface ProductRankRepository {
    List<RankingItem> findTopN(RankingPeriod period, LocalDate asOfDate, long limit, long offset);
    long countByAsOfDate(RankingPeriod period, LocalDate asOfDate);
}
