package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentFailureCategory;
import com.loopers.domain.payment.PaymentStatus;

/**
 * 결제 응답 캡슐. 내부 5개 상태를 사용자용 4개로 매핑한다.
 *
 *  REQUESTED / IN_PROGRESS / UNKNOWN  → PROCESSING        (사용자에겐 "처리 중")
 *  SUCCESS                             → SUCCESS
 *  FAILED + RECOVERABLE                → FAILED_RETRYABLE  (다른 카드로 즉시 재시도 안내)
 *  FAILED + TERMINAL                   → FAILED
 *
 * pollingUrl 은 처리 중일 때 사용자가 상태를 확인할 수 있게 안내.
 * message 는 사용자에게 보일 안내 텍스트.
 */
public record PaymentInfo(
    Long paymentId,
    Long orderId,
    String userStatus,
    String pollingUrl,
    String message
) {
    public static PaymentInfo from(Payment payment) {
        String userStatus = toUserStatus(payment);
        return new PaymentInfo(
            payment.getId(),
            payment.getOrderId(),
            userStatus,
            "/api/v1/payments/" + payment.getId(),
            toMessage(userStatus, payment.getReason())
        );
    }

    private static String toUserStatus(Payment payment) {
        return switch (payment.getStatus()) {
            case SUCCESS -> "SUCCESS";
            case FAILED -> PaymentFailureCategory.classify(payment.getReason()).isRecoverable()
                ? "FAILED_RETRYABLE"
                : "FAILED";
            case REQUESTED, IN_PROGRESS, UNKNOWN -> "PROCESSING";
        };
    }

    private static String toMessage(String userStatus, String reason) {
        return switch (userStatus) {
            case "SUCCESS" -> "결제가 완료되었습니다.";
            case "FAILED_RETRYABLE" -> (reason == null ? "결제에 실패했어요." : reason)
                + " 다른 카드로 다시 시도해주세요.";
            case "FAILED" -> (reason == null ? "결제에 실패했습니다." : reason);
            case "PROCESSING" -> "결제 처리 중입니다. 잠시 후 결과를 확인해주세요.";
            default -> "";
        };
    }
}
