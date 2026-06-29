package com.loopers.infrastructure.payment;

/**
 * PG '시스템 장애' (5xx, 타임아웃, 커넥션 거부 등).
 * CircuitBreaker 가 '실패'로 집계해야 하는 대상 (record-exceptions).
 */
public class PgServerException extends RuntimeException {
    public PgServerException(String message) {
        super(message);
    }

    public PgServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
