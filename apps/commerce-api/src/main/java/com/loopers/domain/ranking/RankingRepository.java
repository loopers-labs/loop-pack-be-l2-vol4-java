package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {

    List<RankingEntry> findRankings(LocalDate date, long start, long end);

    long countRankings(LocalDate date);

    ProductRank findRank(LocalDate date, Long productId);
}
