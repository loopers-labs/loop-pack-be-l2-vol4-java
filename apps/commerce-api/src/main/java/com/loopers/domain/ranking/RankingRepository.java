package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingRepository {

    List<Long> findProductIds(LocalDate date, int page, int size);

    Optional<Long> findRank(LocalDate date, Long productId);

    long count(LocalDate date);
}
