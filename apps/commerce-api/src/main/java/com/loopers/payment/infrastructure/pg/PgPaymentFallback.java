package com.loopers.payment.infrastructure.pg;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class PgPaymentFallback implements PgPaymentClient {

    @Override
    public PgPaymentClientDto.TransactionResponse requestPayment(String userId, PgPaymentClientDto.PaymentRequest request) {
        throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "결제 시스템이 일시적으로 불가합니다.");
    }

    @Override
    public PgPaymentClientDto.TransactionResponse getTransaction(String userId, String transactionKey) {
        throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "결제 시스템이 일시적으로 불가합니다.");
    }
}
