package com.loopers.payment.infrastructure.pg;

// 타임아웃, 네트워크 오류, PG 서버 오류(500) 등 일시적 실패 — 멱등키 전제 하에 retry 가능
public class PgRetriableException extends RuntimeException {
    public PgRetriableException(String message) {
        super(message);
    }
}
