package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingRepository {

    /** 이 구현체가 담당하는 기간. Facade 가 period 로 구현체를 선택하는 라우팅 키다. */
    RankingPeriod period();

    List<Long> findProductIds(LocalDate date, int page, int size);

    Optional<Long> findRank(LocalDate date, Long productId);

    long count(LocalDate date);
}
