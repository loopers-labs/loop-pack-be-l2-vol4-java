package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.*;
import feign.RetryableException;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentGatewayImpl implements PaymentGateway {

    private final PaymentGatewayFeignClient paymentGatewayFeignClient;
    private final PaymentGatewayProperties properties;
    private final Retry retry;

    @Override
    public PaymentGatewayResponse requestPayment(PaymentGatewayRequest request) {
        try {
            return Retry.decorateSupplier(
                    retry,
                    () -> {
                        try {
                            return callRequestPayment(request);
                        } catch (RetryableException e) {
                            log.warn("PG 결제 요청 응답을 받지 못했습니다. orderNumber={}", request.orderNumber(), e);

                            List<TransactionData> transactions = getTransactionsResponse(request).data().transactions();
                            if (transactions.isEmpty()) {
                                throw e;
                            }
                            TransactionData transaction = transactions.getFirst();
                            return new PaymentGatewayResponse(transaction.transactionKey(), TransactionStatus.valueOf(transaction.status()));
                        }
                    }
            ).get();
        } catch (RetryableException e) {
            throw new PaymentGatewayException(FailureReason.RETRY_FAILED, "PG 결제 요청이 재시도 후에도 실패했습니다.");
        }
    }

    private @NonNull PaymentGatewayResponse callRequestPayment(PaymentGatewayRequest request) {
        TransactionResponse response = paymentGatewayFeignClient.requestPayment(
                request.userNumber(),
                new TransactionRequest(
                        request.orderNumber(),
                        request.cardType().name(),
                        request.cardNo(),
                        request.amount().longValueExact(),
                        properties.callbackUrl()
                )
        );
        return new PaymentGatewayResponse(response.data().transactionKey(), TransactionStatus.valueOf(response.data().status()));
    }

    private TransactionsResponse getTransactionsResponse(PaymentGatewayRequest request) {
        try {
            return paymentGatewayFeignClient.findTransactionsByOrderId(request.userNumber(), request.orderNumber());
        } catch (RetryableException e1) {
            log.warn("PG 트랜잭션 조회 중 오류가 발생했습니다. orderNumber={}", request.orderNumber(), e1);
            throw new PaymentGatewayException(FailureReason.UNKNOWN, "PG 트랜잭션 조회 중 오류가 발생했습니다.");
        }
    }
}
