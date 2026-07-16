package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.Map;

public interface RankingRepository {

    void incrementScores(LocalDate date, Map<Long, Double> productScoreDeltas);
}
