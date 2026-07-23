package com.loopers.metrics.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 이벤트가 어느 날짜 행에 쌓일지 정한다. 처리시각이 아니라 발생시각(KST)을 쓴다.
 * 랭킹 ZSET 과 달리 오래된 이벤트는 그대로 받는다 — 배치가 과거 기간을 다시 집계할 수 있기 때문이다.
 * 값을 정할 수 없거나(null) 존재하지 않는 날(미래)이면 빈 값을 준다. 순수 로직(I/O 없음).
 */
public final class MetricStatDate {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private MetricStatDate() {
    }

    public static Optional<LocalDate> of(ZonedDateTime occurredAt, LocalDate today) {
        if (occurredAt == null) {
            return Optional.empty();
        }
        LocalDate date = occurredAt.withZoneSameInstant(SEOUL).toLocalDate();
        return date.isAfter(today) ? Optional.empty() : Optional.of(date);
    }

    public static LocalDate today() {
        return LocalDate.now(SEOUL);
    }
}
