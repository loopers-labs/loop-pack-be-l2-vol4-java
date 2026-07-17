package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 랭킹 display 보드 조회 포트 (Serving 구간). 읽기 전용 — 점수 생성·합성은 commerce-streamer 소관.
 */
public interface RankingRepository {

    /** 점수 내림차순 페이지 조회 (ZREVRANGE). 보드가 없으면 빈 목록. */
    List<RankedProduct> page(LocalDate date, int page, int size);

    /** 보드의 전체 멤버 수 (ZCARD). 보드가 없으면 0. */
    long total(LocalDate date);

    /** 특정 상품의 순위·점수 (ZREVRANK). 보드에 없으면 empty — "오늘 이벤트 없음"은 정상 상태다. */
    Optional<ProductRank> rankOf(LocalDate date, long productId);
}
