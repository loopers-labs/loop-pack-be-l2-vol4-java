package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record ProductRank(long value) {

    public ProductRank {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "순위는 0 이상이어야 합니다.");
        }
    }

    // Redis ZREVRANK는 0-based → 사용자에게 보여줄 "몇 위"는 1-based
    public long position() {
        return value + 1;
    }
}
