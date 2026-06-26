package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

/**
 * 결제 응용 출력 DTO.
 */
public record PaymentInfo(
        Long id,
        Long orderId,
        PaymentStatus status,
        String transactionKey,   // 접수 전이면 null
        String reason,           // 처리 사유, null 가능
        long amount
) {
    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus(),
                payment.getTransactionKey().orElse(null),
                payment.getReason().orElse(null),
                payment.getAmount().amount()
        );
    }
}
