package com.loopers.ranking.domain;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RankingErrorCode implements ErrorCode {
    RANKING_INVALID_DATE("RANKING_INVALID_DATE", "날짜 형식이 올바르지 않습니다."),
    RANKING_INVALID_PERIOD("RANKING_INVALID_PERIOD", "기간 구분이 올바르지 않습니다."),
    RANKING_NOT_AVAILABLE("RANKING_NOT_AVAILABLE", "해당 기간의 랭킹 데이터가 없습니다.");

    private final String code;
    private final String message;
}
