package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface RankingRepository {

    List<RankingEntry> findRankings(LocalDate date, long start, long end);

    long countRankings(LocalDate date);

    ProductRank findRank(LocalDate date, Long productId);

    List<RankingEntry> findHourlyRankings(LocalDateTime dateTime, long start, long end);

    long countHourlyRankings(LocalDateTime dateTime);
}
