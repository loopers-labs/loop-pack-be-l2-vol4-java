package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public interface RankingRepository {

    void incrementScores(LocalDate date, Map<Long, Double> productScoreDeltas);

    void incrementHourlyScores(LocalDateTime dateTime, Map<Long, Double> productScoreDeltas);

    // 콜드 스타트 완화: from 날짜의 점수에 ratio를 곱해 to 날짜 키에 반영한다(기존 to 값은 보존, 합산됨).
    void carryOverScores(LocalDate from, LocalDate to, double ratio);

    void carryOverHourlyScores(LocalDateTime from, LocalDateTime to, double ratio);
}
