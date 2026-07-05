package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 대기열에서의 내 순번 값객체. 0-based (0 = 맨 앞, 곧 입장).
 * {@code ZRANK} 결과를 감싸며, 음수는 존재할 수 없다.
 */
public record QueuePosition(long value) {

    public QueuePosition {
        if (value < 0) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "순번은 음수일 수 없습니다: " + value);
        }
    }

    public static QueuePosition of(long value) {
        return new QueuePosition(value);
    }
}
