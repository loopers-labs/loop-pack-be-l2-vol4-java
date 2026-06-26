package com.loopers.domain.payment;

/**
 * PG 요청이 실패했음을 알리는 신호 — 호출자는 이걸 받으면 결제를 TIMEOUT_PENDING 으로 보내고
 * 복구 스케줄러에 위임해야 한다. (단순 에러가 아닌, 복구 가능한 미확정 상태)
 */
public class PgRequestException extends RuntimeException {
    public PgRequestException(String message) {
        super(message);
    }

    public PgRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
