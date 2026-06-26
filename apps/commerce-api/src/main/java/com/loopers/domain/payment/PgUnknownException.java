package com.loopers.domain.payment;

/**
 * PG 호출 결과를 확정할 수 없을 때 던지는 예외.
 * 예: read timeout, 5xx, 응답 파싱 실패 등 — "PG 가 처리했는지 안 했는지 모르는" 상황.
 *
 * 이 예외를 받으면 호출자(PaymentFacade) 는 Payment 를 UNKNOWN 상태로 둔다.
 * 폴링이 PG 의 실제 상태를 확정할 때까지 보류하며, FAILED 로 단정하지 않는다 (돈 잃음 방지).
 */
public class PgUnknownException extends RuntimeException {

    public PgUnknownException(String message) {
        super(message);
    }

    public PgUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
