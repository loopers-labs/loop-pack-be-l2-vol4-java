package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

/**
 * 결제 요청/조회 응답용 application DTO. 확정된 DB 상태를 그대로 노출한다(결과 통지 = 클라이언트 폴링).
 */
public record PaymentInfo(
        Long paymentId,
        String orderNumber,
        PaymentStatus status,
        String failureReason,
        String message
) {

    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderNumber(),
                payment.getStatus(),
                payment.getFailureReason(),
                message(payment.getStatus()));
    }

    private static String message(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "결제를 처리 중입니다.";
            case PAID -> "결제가 완료되었습니다.";
            case FAILED -> "결제가 실패했습니다.";
            case UNKNOWN -> "결제 상태를 확인 중입니다. 잠시 후 다시 확인해주세요.";
        };
    }
}
