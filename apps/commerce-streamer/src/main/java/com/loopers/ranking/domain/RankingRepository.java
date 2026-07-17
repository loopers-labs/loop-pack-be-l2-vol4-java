package com.loopers.ranking.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 랭킹 ZSET 적재 포트. 구현(infrastructure)이 pipeline 으로 한 번에 ZINCRBY 한다.
 */
public interface RankingRepository {
    void incrBy(List<RankingScoreDelta> deltas);

    /** from 판의 점수에 weight 를 곱해 to 판에 심는다(carry-over). to 키에 절대 만료(to+2일)를 건다. */
    void carryOver(LocalDate from, LocalDate to, double weight);

    /** date 판을 내림차순으로 전부 읽는다(finalize 스냅샷용). */
    List<RankingEntry> readDesc(LocalDate date);
}
