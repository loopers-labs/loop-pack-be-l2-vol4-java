package com.loopers.domain.payment;

/**
 * PG 결제 '요청(접수)' 응답. POST /payments 의 결과.
 * 이 시점엔 처리 결과를 알 수 없고, 접수 식별자(transactionKey)만 받는다.
 */
public record PgRequestResult(
        String transactionKey,
        PgStatus status
) {}
