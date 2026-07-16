package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;

public record RankingQueryCondition(LocalDate date, int page, int size) {

    public RankingQueryCondition {
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

    public long start() {
        return (long) (page - 1) * size;
    }

    public long end() {
        return start() + size - 1;
    }
}
