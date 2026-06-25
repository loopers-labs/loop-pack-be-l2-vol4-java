package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;

public record PaymentGatewayTransaction(
    String transactionKey,
    PgPaymentStatus status,
    String reason
) {

    public PaymentGatewayTransaction {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 거래 키는 비어있을 수 없습니다.");
        }
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 거래 상태는 비어있을 수 없습니다.");
        }
    }
}
