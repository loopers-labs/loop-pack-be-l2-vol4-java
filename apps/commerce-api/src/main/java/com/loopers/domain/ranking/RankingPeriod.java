package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.Locale;

/**
 * 랭킹 조회 주기. 일간은 Redis ZSET(실시간)에서, 주간·월간은 배치가 적재한 MV 테이블에서 읽는다.
 * MV의 period_key는 배치(commerce-batch)의 Period와 같은 규칙(ISO 주 / 달력 달)으로 계산해야 매칭된다.
 */
public enum RankingPeriod {

    DAILY,
    WEEKLY,
    MONTHLY;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM", Locale.KOREA);

    /** 요청 파라미터(대소문자 무관)를 주기로 파싱한다. 없으면 일간, 알 수 없으면 400. */
    public static RankingPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period는 DAILY, WEEKLY, MONTHLY 중 하나여야 합니다.", e);
        }
    }

    public boolean isDaily() {
        return this == DAILY;
    }

    /** 이 주기 MV의 period_key. 배치의 Period.key와 동일 규칙. 일간은 MV를 쓰지 않으므로 호출 대상이 아니다. */
    public String mvPeriodKey(LocalDate date) {
        return switch (this) {
            case WEEKLY -> String.format("%04d-W%02d",
                    date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTHLY -> date.format(MONTH);
            case DAILY -> throw new IllegalStateException("일간 랭킹은 MV period_key를 사용하지 않는다.");
        };
    }
}
