package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;

import java.util.List;

public record PaymentGatewayOrderTransactions(
    Long orderId,
    List<PaymentGatewayTransaction> transactions
) {

    public PaymentGatewayOrderTransactions {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 주문 ID는 비어있을 수 없습니다.");
        }
        if (transactions == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 거래 목록은 비어있을 수 없습니다.");
        }
        transactions = List.copyOf(transactions);
    }
}
