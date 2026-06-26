package com.loopers.domain.payment;

/**
 * PG가 보고하는 거래 한 건. status는 pg-simulator의 TransactionStatus(PENDING/SUCCESS/FAILED)와 1:1 대응하며,
 * 우리 도메인의 PaymentStatus enum을 그대로 재사용한다(이름 동일).
 */
public record PgTransaction(
        String transactionKey,
        PaymentStatus status,
        String reason
) {
    public boolean isSuccess() {
        return status == PaymentStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }
}
