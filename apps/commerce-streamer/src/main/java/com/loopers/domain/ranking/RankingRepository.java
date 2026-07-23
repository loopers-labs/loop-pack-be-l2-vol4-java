package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {

    /**
     * 해당 날짜의 랭킹판에서 상품 점수를 delta만큼 누적한다.
     */
    void incrementScore(LocalDate date, Long productId, double scoreDelta);

    /**
     * 해당 날짜 랭킹판의 상위 항목을 점수 내림차순으로 조회한다 (양수 점수만 — Carry-Over 읽기용).
     */
    List<RankingEntry> findTopEntries(LocalDate date, int limit);

    /**
     * 해당 날짜 랭킹판에 항목이 없을 때만 점수를 기록한다 (ZADD NX — Carry-Over 쓰기용).
     * 이미 점수가 쌓인 상품은 덮어쓰지 않으므로 스케줄러가 중복 실행되어도 안전하다.
     */
    void saveScoreIfAbsent(LocalDate date, Long productId, double score);
}
