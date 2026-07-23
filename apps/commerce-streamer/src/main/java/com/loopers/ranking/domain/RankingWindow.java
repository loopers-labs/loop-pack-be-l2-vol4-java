package com.loopers.ranking.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 이벤트 발생시각(occurredAt)이 어느 날 랭킹에 속하는지, 아니면 창 밖(미래/너무 과거)인지 판정한다.
 * 창 = 오늘·어제(2일). 처리시각이 아니라 occurredAt(KST)으로 판정한다. 순수 로직(I/O 없음).
 * 발생시각이 없으면 던지지 않고 UNDATED 를 돌려준다 — 컨슈머가 판정을 빠뜨리면 컴파일이 막히도록.
 */
public final class RankingWindow {

    public enum Verdict {
        IN_WINDOW,
        FUTURE,
        TOO_OLD,
        UNDATED
    }

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private RankingWindow() {
    }

    public static Classification classify(ZonedDateTime occurredAt, LocalDate today) {
        if (occurredAt == null) {
            return new Classification(Verdict.UNDATED, null);
        }
        LocalDate date = occurredAt.withZoneSameInstant(SEOUL).toLocalDate();
        if (date.isAfter(today)) {
            return new Classification(Verdict.FUTURE, date);
        }
        if (date.isBefore(today.minusDays(1))) {
            return new Classification(Verdict.TOO_OLD, date);
        }
        return new Classification(Verdict.IN_WINDOW, date);
    }

    public record Classification(Verdict verdict, LocalDate date) {
    }
}
