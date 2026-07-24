package com.loopers.domain.ranking;

import java.time.LocalDate;

// 집계 대상 기간을 [start, end] 양끝 포함 날짜 구간으로 표현한다. KST 기준 metric_date 와 직접 비교된다.
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("기간의 시작/종료 날짜는 필수입니다.");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("기간의 시작 날짜는 종료 날짜보다 뒤일 수 없습니다.");
        }
    }
}
