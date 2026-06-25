package com.loopers.tddstudy.infrastructure.order;

import com.loopers.tddstudy.domain.order.PaymentGateway;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

@Primary
@Component
public class PgPaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(PgPaymentGateway.class);

    private final PgPaymentClient pgPaymentClient;

    public PgPaymentGateway(PgPaymentClient pgPaymentClient) {
        this.pgPaymentClient = pgPaymentClient;
    }

    @CircuitBreaker(name = "pg", fallbackMethod = "fallback")
    @Override
    public String requestPayment(Long orderId, int amount) {
        String formattedOrderId = String.format("%06d", orderId);

        PgPaymentRequest request = new PgPaymentRequest(
                formattedOrderId,
                "SAMSUNG",
                "1234-5678-9814-1451",
                amount,
                "http://localhost:8080/api/v1/payments/callback"
        );
        log.info("PG 요청 시작 - orderId: {}, amount: {}", formattedOrderId, amount);
        PgPaymentResponse response = pgPaymentClient.requestPayment(request);
        log.info("PG 응답 - response: {}", response);

        if (response == null) {
            return "FAIL";
        }

        return "PENDING";
    }

    public String fallback(Long orderId, int amount, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.warn("CircuitBreaker OPEN - orderId: {} (PG 호출 차단됨)", orderId);
            return "CIRCUIT_OPEN";
        }
        log.error("PG 결제 실패 - orderId: {}, 원인: {}", orderId, t.getMessage(), t);
        return "FAIL";
    }

}
