package com.loopers.domain.payment.gateway;

import java.util.List;
import java.util.Optional;

public interface PaymentGateway {
    default PaymentGatewayResult requestPayment(PaymentGatewayCommand.Request command) {
        throw new UnsupportedOperationException("PG 결제 요청을 지원하지 않습니다.");
    }

    default Optional<PaymentGatewayResult> getPayment(String userId, String transactionKey) {
        throw new UnsupportedOperationException("PG 결제 조회를 지원하지 않습니다.");
    }

    default List<PaymentGatewayResult> getPaymentsByOrderId(String userId, String orderId) {
        throw new UnsupportedOperationException("PG 주문별 결제 조회를 지원하지 않습니다.");
    }

    default PaymentGatewayResult authorize(Long orderId, Long amount, String idempotencyKey) {
        throw new UnsupportedOperationException("PG 승인을 지원하지 않습니다.");
    }

    default PaymentGatewayResult capture(String transactionKey) {
        throw new UnsupportedOperationException("PG 매입을 지원하지 않습니다.");
    }

    default PaymentGatewayResult voidAuthorization(String transactionKey) {
        throw new UnsupportedOperationException("PG 승인 취소를 지원하지 않습니다.");
    }
}
