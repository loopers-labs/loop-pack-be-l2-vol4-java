package com.loopers.domain.catalog.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {

    boolean incrementScoresOnce(LocalDate date, String eventId, List<Score> scores, Duration ttl);

    record Score(Long productId, double score) {
    }
}
