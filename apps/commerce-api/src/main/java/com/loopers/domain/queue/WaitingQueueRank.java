package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record WaitingQueueRank(long value) {

    public WaitingQueueRank {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "대기열 순번은 0 이상이어야 합니다.");
        }
    }

    // Redis ZRANK는 0-based → 사용자에게 보여줄 "몇 번째"는 1-based
    public long position() {
        return value + 1;
    }
}
