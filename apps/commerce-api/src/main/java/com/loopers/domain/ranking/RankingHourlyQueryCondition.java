package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDateTime;

public record RankingHourlyQueryCondition(LocalDateTime dateTime, int page, int size) {

    public RankingHourlyQueryCondition {
        if (dateTime == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "dateTime은 필수입니다.");
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
