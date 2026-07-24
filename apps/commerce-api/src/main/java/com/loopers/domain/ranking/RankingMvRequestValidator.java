package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

/**
 * 랭킹 조회 API의 date/period/periodKey 조합을 검증한다(§3.5).
 * date와 period는 상호 배타, period와 periodKey는 함께 있거나 함께 없어야 한다.
 */
public class RankingMvRequestValidator {

    private static final Pattern WEEKLY_PERIOD_KEY = Pattern.compile("^\\d{4}W(0[1-9]|[1-4]\\d|5[0-3])$");
    private static final Pattern MONTHLY_PERIOD_KEY = Pattern.compile("^\\d{4}(0[1-9]|1[0-2])$");

    private RankingMvRequestValidator() {}

    public static void validate(String date, String period, String periodKey) {
        if (date != null && period != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date와 period는 함께 사용할 수 없습니다.");
        }
        if ((period == null) != (periodKey == null)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period와 periodKey는 함께 있거나 함께 없어야 합니다.");
        }
        if (period == null) {
            return;
        }

        RankingMvPeriod periodType = RankingMvPeriod.from(period);
        Pattern pattern = periodType == RankingMvPeriod.WEEKLY ? WEEKLY_PERIOD_KEY : MONTHLY_PERIOD_KEY;
        if (!pattern.matcher(periodKey).matches()) {
            String expected = periodType == RankingMvPeriod.WEEKLY ? "yyyyWww(주차 01~53)" : "yyyyMM(월 01~12)";
            throw new CoreException(ErrorType.BAD_REQUEST, "periodKey는 " + expected + " 형식이어야 합니다: " + periodKey);
        }
    }
}
