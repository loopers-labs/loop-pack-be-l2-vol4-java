package com.loopers.ranking.application;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyRankingQuery {

    DailyRankingEntries findDaily(LocalDate date, long start, long end);

    Optional<Long> findDailyRank(LocalDate date, Long productId);
}
