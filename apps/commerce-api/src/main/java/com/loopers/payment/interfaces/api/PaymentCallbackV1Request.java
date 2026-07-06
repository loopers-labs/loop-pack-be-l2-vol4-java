package com.loopers.payment.interfaces.api;

import com.loopers.payment.application.PaymentCommand;
import com.loopers.payment.domain.PaymentStatus;

public class PaymentCallbackV1Request {

    /**
     * PG 콜백 바디. {@code orderId} 는 우리 orderNumber, {@code status} 는 PG 처리 상태(PENDING/SUCCESS/FAILED)다.
     * cardType·cardNo 등 나머지 필드는 수신만 하고 사용하지 않는다(Jackson 이 무시).
     */
    public record Callback(
            String transactionKey,
            String orderId,
            long amount,
            String status,
            String reason
    ) {
        public PaymentCommand.Confirm toConfirm() {
            return new PaymentCommand.Confirm(transactionKey, orderId, amount, PaymentStatus.valueOf(status), reason);
        }
    }
}
