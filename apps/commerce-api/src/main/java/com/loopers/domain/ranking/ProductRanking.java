package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductRanking {

    /** 해당 일자 랭킹의 [page*size, page*size+size-1] 구간을 score 내림차순으로 반환한다. */
    List<RankedProduct> page(LocalDate date, int page, int size);

    /** 해당 일자 랭킹에 진입한 전체 상품 수(페이지 total). */
    long totalCount(LocalDate date);

    /** 해당 일자 랭킹에서 상품의 순위(0-based). 랭킹에 없으면 empty. */
    Optional<Long> rankOf(LocalDate date, Long productId);
}
