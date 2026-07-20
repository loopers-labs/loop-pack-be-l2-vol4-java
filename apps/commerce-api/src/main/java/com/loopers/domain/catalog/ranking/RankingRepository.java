package com.loopers.domain.catalog.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingRepository {

    void incrementScore(LocalDate date, Long productId, double score, Duration ttl);

    List<Entry> findRankings(LocalDate date, int page, int size);

    Optional<Entry> findRank(LocalDate date, Long productId);

    long count(LocalDate date);

    record Entry(Long productId, Long rank, Double score) {
    }
}
