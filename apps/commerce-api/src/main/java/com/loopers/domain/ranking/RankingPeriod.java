package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

// 랭킹 조회 기간. DAILY 는 실시간 Redis, WEEKLY/MONTHLY 는 배치가 만든 MV 를 조회한다.
public enum RankingPeriod {

    DAILY, WEEKLY, MONTHLY;

    // 파라미터 미지정 시 기존 동작(일간)을 유지한다.
    public static RankingPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        try {
            return RankingPeriod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period는 DAILY, WEEKLY, MONTHLY 중 하나여야 합니다.");
        }
    }

    // MV 조회 시 date 가 속한 기간의 대표일(집계 컬럼 값)로 변환한다.
    // WEEKLY: 그 주 월요일(week_start_date), MONTHLY: 그 달 1일(month_start_date), DAILY: 그대로.
    public LocalDate resolveAggregateDate(LocalDate date) {
        return switch (this) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> date.with(TemporalAdjusters.firstDayOfMonth());
        };
    }
}
