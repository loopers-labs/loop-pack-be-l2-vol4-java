package com.loopers.domain.payment;

/**
 * PG 에 연결 자체가 실패(connection refused 등)하여 <b>요청이 PG 에 도달하지 못한 것이 확실한</b> 경우.
 * 이 경우에만 재시도해도 이중 결제 위험이 없다. (read timeout 등 '도달 여부 불확실' 한 경우는 {@link PgClientException})
 */
public class PgConnectionException extends PgClientException {

    public PgConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
