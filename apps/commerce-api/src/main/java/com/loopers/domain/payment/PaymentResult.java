package com.loopers.domain.payment;

public record PaymentResult(
    Status status,
    String pgTransactionId,
    String failureReason
) {
    public enum Status {
        SUCCESS,
        FAILED,
        TIMEOUT
    }

    public static PaymentResult success(String pgTransactionId) {
        return new PaymentResult(Status.SUCCESS, pgTransactionId, null);
    }

    public static PaymentResult failed(String reason) {
        return new PaymentResult(Status.FAILED, null, reason);
    }

    public static PaymentResult timeout() {
        return new PaymentResult(Status.TIMEOUT, null, "외부 결제 시스템 응답 시간 초과");
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /** 실패 사유. null 이면 기본 메시지를 반환한다. 결제 실패 응답 메시지 구성에 사용. */
    public String failureReasonOrDefault() {
        return failureReason != null ? failureReason : "결제 실패로 주문이 취소되었습니다.";
    }
}
