package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 일간 실시간 랭킹 조회 포트. 구현({@code infrastructure.ranking.RankingRedisRepository})은 Redis ZSET 을
 * 읽어 순위/스코어를 돌려준다 — 도메인은 저장 기술(Redis)을 모른다(레이어드 의존 규칙).
 *
 * <p>쓰기(집계)는 commerce-streamer 가 담당하고, 이 포트는 <b>읽기 전용</b>이다.
 */
public interface RankingRepository {

    /**
     * 지정 일자 랭킹의 한 페이지(내림차순 상위)를 반환한다.
     *
     * @param date 랭킹 일자(KST)
     * @param page 1부터 시작하는 페이지 번호
     * @param size 페이지 크기
     * @return 순위·상품·스코어 목록(비면 빈 리스트)
     */
    List<RankedProduct> findPage(LocalDate date, int page, int size);

    /** 지정 일자 랭킹에 오른 상품 수(ZCARD). 페이지네이션 total 로 쓴다. */
    long size(LocalDate date);

    /** 지정 일자 랭킹에서 상품의 순위(1-based). 랭킹에 없으면 empty. */
    Optional<Long> findRank(LocalDate date, Long productId);
}
