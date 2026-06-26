package com.loopers.domain.payment;

/**
 * PG 호출 자체가 실패(타임아웃, 연결 실패, 5xx 등)했음을 나타내는 인프라 예외.
 * 결제 결과가 '실패'인 것과는 구분된다. (Phase 4 서킷브레이커/폴백의 대상)
 */
public class PgClientException extends RuntimeException {

    public PgClientException(String message) {
        super(message);
    }

    public PgClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
