package com.loopers.domain.payment;

/**
 * PG 가 명백한 영구 에러를 반환했을 때 던지는 예외.
 * 예: HTTP 4xx (잘못된 요청, 유효하지 않은 카드 등) — 재시도해도 같은 결과.
 *
 * 이 예외를 받으면 호출자는 Payment 를 즉시 FAILED 로 전이하고, 재고 복구 흐름을 트리거한다.
 */
public class PgPermanentException extends RuntimeException {

    public PgPermanentException(String message) {
        super(message);
    }

    public PgPermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
