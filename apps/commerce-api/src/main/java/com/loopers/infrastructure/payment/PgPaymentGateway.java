package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.model.PaymentStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

    private final PgClient pgClient;

    @CircuitBreaker(name = "pgPayment", fallbackMethod = "requestPaymentFallback")
    @Override
    public PaymentGatewayResult requestPayment(PaymentGatewayCommand command) {
        PgPaymentRequest request = new PgPaymentRequest(
            command.orderId(),
            command.cardType().name(),
            command.cardNo(),
            command.amount(),
            command.callbackUrl()
        );

        PgPaymentResponse data = pgClient.requestPayment(command.userId(), request).data();

        return new PaymentGatewayResult(
            data.transactionKey(),
            PaymentStatus.valueOf(data.status()),
            data.reason()
        );
    }

    @SuppressWarnings("unused")
    private PaymentGatewayResult requestPaymentFallback(PaymentGatewayCommand command, Throwable t) {
        log.warn("PG 결제 요청 실패로 fallback 처리합니다. orderId={}, cause={}", command.orderId(), t.toString());
        // 결과 미확정(PENDING)으로 두고, 트랜잭션 키 없이 반환 -> 폴링 복구가 PG 실제 상태를 확인
        return new PaymentGatewayResult(null, PaymentStatus.PENDING, "결제 처리 중입니다. 잠시 후 확인됩니다.");
    }
}
