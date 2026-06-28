package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.PaymentStatus;

public class PaymentV1Dto {

    public record PaymentRequest(Long orderId, String cardType, String cardNo) {
        public PaymentCommand.Pay toCommand() {
            return new PaymentCommand.Pay(orderId, cardType, cardNo);
        }
    }

    /**
     * PG 가 비동기 결과를 통보하는 콜백(웹훅) 페이로드. PG 의 TransactionInfo 직렬화 형태와 대응한다.
     * 디스패치는 transactionKey 로만 하며, orderId/amount 등 나머지 필드는 신뢰 대상이 아니다.
     */
    public record CallbackRequest(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
    ) {
        public PaymentStatus paymentStatus() {
            return PaymentStatus.valueOf(status);
        }
    }

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        PaymentStatus status,
        String transactionKey
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.status(),
                info.transactionKey()
            );
        }
    }
}
