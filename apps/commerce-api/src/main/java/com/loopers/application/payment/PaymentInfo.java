package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

/**
 * 결제 결과 응답 DTO (application 경계). 카드번호 등 민감정보는 노출하지 않는다.
 */
public record PaymentInfo(
        Long id,
        Long orderId,
        String transactionKey,
        PaymentStatus status,
        String reason
) {
    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getTransactionKey(),
                payment.getStatus(),
                payment.getReason()
        );
    }
}
