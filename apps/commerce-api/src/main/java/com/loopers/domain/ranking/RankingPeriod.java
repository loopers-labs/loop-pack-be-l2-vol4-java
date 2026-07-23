package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;

/**
 * 랭킹 조회 기간. 일간은 실시간 Redis ZSET, 주간/월간은 배치가 적재한 MV 를 읽는다.
 * periodKey 산출 규칙은 commerce-batch 의 RankingPeriod 와 반드시 동일해야 조회가 맞는다(주 ISO yyyy'W'ww / 월 yyyyMM).
 */
public enum RankingPeriod {

    DAILY {
        @Override
        public String periodKey(LocalDate date) {
            return date.format(DateTimeFormatter.BASIC_ISO_DATE);
        }
    },

    WEEKLY {
        @Override
        public String periodKey(LocalDate date) {
            int year = date.get(WeekFields.ISO.weekBasedYear());
            int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            return String.format("%dW%02d", year, week);
        }
    },

    MONTHLY {
        @Override
        public String periodKey(LocalDate date) {
            return date.format(MONTH_KEY);
        }
    };

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    /** date 가 속한 기간 버킷의 키. */
    public abstract String periodKey(LocalDate date);
}
