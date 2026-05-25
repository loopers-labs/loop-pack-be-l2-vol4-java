package com.loopers.domain.payment;

/**
 * 외부 결제 시스템 응답을 도메인 언어로 캡슐화한 VO (03 §2.6).
 */
public record PaymentResult(
    PgStatus status,
    String reason,
    String externalTxId
) {
    public static PaymentResult success(String externalTxId) {
        return new PaymentResult(PgStatus.SUCCESS, null, externalTxId);
    }

    public static PaymentResult failed(String reason) {
        return new PaymentResult(PgStatus.FAILED, reason, null);
    }

    public static PaymentResult timeout() {
        return new PaymentResult(PgStatus.TIMEOUT, "결제 응답 시간 초과", null);
    }

    public boolean isSuccess() {
        return status == PgStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == PgStatus.FAILED;
    }

    public boolean isTimeout() {
        return status == PgStatus.TIMEOUT;
    }
}
