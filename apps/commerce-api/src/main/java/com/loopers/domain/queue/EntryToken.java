package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.UUID;

/**
 * 주문 API 진입 토큰 값객체. 대기열에서 입장 차례가 된 유저에게 발급되며, TTL 이 지나면 만료된다.
 */
public record EntryToken(String value) {

    public EntryToken {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "입장 토큰 값은 비어 있을 수 없습니다.");
        }
    }

    /** 새 입장 토큰 발급용 값 생성. */
    public static EntryToken generate() {
        return new EntryToken(UUID.randomUUID().toString());
    }

    public static EntryToken of(String value) {
        return new EntryToken(value);
    }
}
