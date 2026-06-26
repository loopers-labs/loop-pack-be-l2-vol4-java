package com.loopers.infrastructure.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRequestResult;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentGatewayImpl implements PaymentGateway {

    private static final String CIRCUIT_BREAKER_NAME = "pg-simulator";
    private static final String EXTERNAL_ORDER_ID_FORMAT = "%06d";
    private static final String REJECTED_REASON = "결제가 거절되었습니다.";
    private static final int SERVER_ERROR_STATUS = 500;

    private final PgSimulatorClient pgSimulatorClient;

    @Value("${pg-simulator.callback-url}")
    private String callbackUrl;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "fallbackRequestPayment")
    public PaymentRequestResult requestPayment(PaymentModel payment) {
        PgSimulatorDto.PaymentRequest request = new PgSimulatorDto.PaymentRequest(
            toExternalOrderId(payment.getOrderId()),
            payment.getCardType().name(),
            payment.getCardNo().value(),
            (long) payment.getAmount(),
            callbackUrl
        );

        String transactionKey = pgSimulatorClient.requestPayment(String.valueOf(payment.getUserId()), request)
            .data()
            .transactionKey();

        return PaymentRequestResult.accepted(transactionKey);
    }

    private PaymentRequestResult fallbackRequestPayment(PaymentModel payment, Throwable throwable) {
        if (isConfirmedFailure(throwable)) {
            log.warn("PG 결제 접수 확정 실패 (orderId={}): {}", payment.getOrderId(), throwable.getMessage());
            return PaymentRequestResult.rejected(REJECTED_REASON);
        }

        log.warn("PG 결제 접수 결과 불명 (orderId={}): {}", payment.getOrderId(), throwable.getMessage());
        return PaymentRequestResult.unknown();
    }

    private boolean isConfirmedFailure(Throwable throwable) {
        if (throwable instanceof RetryableException) {
            return false;
        }

        return throwable instanceof FeignException feignException && feignException.status() >= SERVER_ERROR_STATUS;
    }

    private String toExternalOrderId(Long orderId) {
        return String.format(EXTERNAL_ORDER_ID_FORMAT, orderId);
    }
}
