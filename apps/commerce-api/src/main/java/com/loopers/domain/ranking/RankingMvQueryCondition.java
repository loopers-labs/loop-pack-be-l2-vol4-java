package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;

// 주간/월간(MV) 랭킹 조회 조건. daily(Redis)와 소스가 달라 별도 조건으로 둔다(hourly 와 동일한 패턴).
public record RankingMvQueryCondition(RankingPeriod period, LocalDate date, int page, int size) {

    public RankingMvQueryCondition {
        if (period == null || period == RankingPeriod.DAILY) {
            throw new CoreException(ErrorType.BAD_REQUEST, "MV 랭킹 조회는 WEEKLY 또는 MONTHLY만 지원합니다.");
        }
        if (date == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date는 필수입니다.");
        }
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상이어야 합니다.");
        }
    }

    // date 가 속한 기간의 MV 식별 날짜(week_start_date / month_start_date).
    public LocalDate aggregateDate() {
        return period.resolveAggregateDate(date);
    }
}
