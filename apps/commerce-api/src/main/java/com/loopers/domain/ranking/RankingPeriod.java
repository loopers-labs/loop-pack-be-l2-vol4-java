package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.Locale;

/**
 * 랭킹 조회 기간. 기준일 하나로 조회 키를 결정한다.
 * commerce-batch 의 com.loopers.domain.ranking.RankingPeriod 와 크로스-앱 계약이다
 * (배치가 적재한 period_key 와 여기서 만든 키가 정확히 같아야 조회가 맞는다).
 *
 * <p>일간만 실시간 Redis ZSET 을 보고, 주간/월간은 배치가 만든 MV 를 본다.
 */
public enum RankingPeriod {

    DAILY,
    WEEKLY,
    MONTHLY;

    private static final DateTimeFormatter BASIC_ISO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public static RankingPeriod from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAILY; // 기존 API 호환 — period 미지정은 일간
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period 는 DAILY|WEEKLY|MONTHLY 입니다: " + raw);
        }
    }

    /**
     * 조회 키. 주간은 ISO 주 기준 연도를 쓴다 — 연말/연초의 주가 달력 연도와 어긋나므로
     * (2027-01-01 은 2026-W53) 달력 연도를 쓰면 한 주가 두 키로 쪼개진다.
     */
    public String periodKey(LocalDate date) {
        return switch (this) {
            case DAILY -> BASIC_ISO_DATE.format(date);
            case WEEKLY -> "%d-W%02d".formatted(
                date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTHLY -> "%d-%02d".formatted(date.getYear(), date.getMonthValue());
        };
    }
}
