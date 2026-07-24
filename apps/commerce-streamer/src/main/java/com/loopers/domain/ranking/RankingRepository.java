package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public interface RankingRepository {

    /**
     * eventId 최초 처리일 때만 deltas 를 date 일간 키에 반영한다(dedup + 반영 원자화).
     * 반영했으면 true, 중복(이미 처리)·빈 delta 면 false.
     */
    boolean applyOnce(String eventId, LocalDate date, Map<Long, Double> deltas);

    Optional<Double> findScore(LocalDate date, Long productId);

    /** from 일간 키 점수 × rate 를 to 일간 키로 미리 복사한다(콜드 스타트 완화). */
    void carryOver(LocalDate from, LocalDate to, double rate);
}
