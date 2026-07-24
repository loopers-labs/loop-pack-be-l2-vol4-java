package com.loopers.domain.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 랭킹 조회 단위(week10). 요청 날짜를 기간 경계로 환산하는 규칙을 담는다.
 *
 * <p><b>주간은 ISO-8601 기준 월요일 시작</b>이다. commerce-batch 의 {@code RankingPeriodType} 과
 * <b>경계 규칙이 반드시 같아야 한다</b> — 배치가 적재한 {@code period_start} 로 조회해야 하므로,
 * 한쪽만 일요일 시작으로 바꾸면 조회가 영영 빈 결과를 낸다(앱 경계라 코드 공유 없이 규칙만 맞춘다).
 *
 * <p>{@link #DAILY} 는 하루가 곧 기간이라 시작=종료=그 날짜다. 덕분에 응답의 기간 표기가
 * 세 단위에서 동일한 형태를 갖는다.
 */
public enum RankingPeriod {

    /** 일간 — 실시간 ZSET / 영속 스냅샷에서 읽는다. */
    DAILY {
        @Override
        public LocalDate resolveStart(LocalDate date) {
            return date;
        }

        @Override
        public LocalDate resolveEnd(LocalDate start) {
            return start;
        }
    },

    /** 주간 — 월요일 ~ 일요일. MV 에서 읽는다. */
    WEEKLY {
        @Override
        public LocalDate resolveStart(LocalDate date) {
            return date.with(DayOfWeek.MONDAY);
        }

        @Override
        public LocalDate resolveEnd(LocalDate start) {
            return start.plusDays(6);
        }
    },

    /** 월간 — 1일 ~ 말일. MV 에서 읽는다. */
    MONTHLY {
        @Override
        public LocalDate resolveStart(LocalDate date) {
            return date.withDayOfMonth(1);
        }

        @Override
        public LocalDate resolveEnd(LocalDate start) {
            return start.plusMonths(1).minusDays(1);
        }
    };

    /** 주어진 날짜가 속한 기간의 시작일. MV 조회 키이자 응답의 기간 표기. */
    public abstract LocalDate resolveStart(LocalDate date);

    /** 기간 시작일로부터의 종료일(포함). */
    public abstract LocalDate resolveEnd(LocalDate start);

    /** 일간인가 — 읽기 소스(ZSET/스냅샷 vs MV)를 가르는 기준. */
    public boolean isDaily() {
        return this == DAILY;
    }

    /**
     * 요청 파라미터 문자열 → enum. <b>대소문자를 가리지 않고</b>, 비어 있으면 {@link #DAILY} 로 본다
     * (period 없이 호출하던 기존 클라이언트가 그대로 동작해야 한다).
     *
     * @throws IllegalArgumentException 알 수 없는 값 — 조용히 DAILY 로 넘기면 클라이언트가 주간을
     *                                  요청했다고 믿는 채 일간을 받게 되므로 명시적으로 실패시킨다.
     */
    public static RankingPeriod from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAILY;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 period: " + raw + " (DAILY | WEEKLY | MONTHLY)");
        }
    }
}
