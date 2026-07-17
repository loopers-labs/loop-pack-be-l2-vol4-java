package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {
    List<RankingEntry> getRankings(LocalDate date, int page, int size);
    Long getRank(LocalDate date, Long productId);
}
