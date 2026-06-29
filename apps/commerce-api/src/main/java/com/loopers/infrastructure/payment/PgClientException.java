package com.loopers.infrastructure.payment;

/**
 * PG '요청/비즈니스 오류' (4xx). 예: 잘못된 카드 형식, 존재하지 않는 결제 조회.
 * 이것은 PG가 '정상 작동'하며 돌려준 응답이므로 CircuitBreaker 가
 * '실패'로 집계하면 안 된다 (ignore-exceptions).
 */
public class PgClientException extends RuntimeException {
    public PgClientException(String message) {
        super(message);
    }

    public PgClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
