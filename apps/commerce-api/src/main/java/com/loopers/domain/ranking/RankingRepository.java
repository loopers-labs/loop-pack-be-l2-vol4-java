package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 랭킹 조회 전용 저장소 — 적재는 commerce-streamer가 담당한다.
 * 모든 조회는 양수 점수 상품만 대상으로 한다: 좋아요 취소가 좋아요보다 먼저(또는 다른 날짜에) 반영되면
 * 0 이하 점수의 유령 멤버가 생길 수 있는데, 이를 랭킹에 노출하지 않기 위함이다.
 */
public interface RankingRepository {

    /** 해당 날짜 랭킹판에서 점수 내림차순으로 offset부터 limit개의 상품 ID를 조회한다. */
    List<Long> findTopProductIds(LocalDate date, long offset, int limit);

    /** 해당 날짜 랭킹판의 전체 상품 수 (양수 점수만). */
    long countRanked(LocalDate date);

    /** 해당 날짜 랭킹판에서 상품의 순위(1-based)를 조회한다. 순위에 없으면(미등재·0 이하 점수) empty. */
    Optional<Long> findRank(LocalDate date, Long productId);
}
