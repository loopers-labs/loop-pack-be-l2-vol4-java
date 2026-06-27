package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

/**
 * PaymentGateway 포트의 PG(Feign) 구현체.
 * 타임아웃(Feign) + 재시도/서킷브레이커(resilience4j) + 폴백으로 외부 장애를 방어한다.
 */
@Component
public class PgPaymentGateway implements PaymentGateway {

    private final PgPaymentClient pgPaymentClient;

    public PgPaymentGateway(PgPaymentClient pgPaymentClient) {
        this.pgPaymentClient = pgPaymentClient;
    }

    @CircuitBreaker(name = "pgPayment", fallbackMethod = "fallback")
    @Retry(name = "pgPayment")
    @Override
    public PaymentResult requestPayment(PaymentRequest request) {
        PgPaymentV1Dto.TransactionResponse transaction = pgPaymentClient.requestPayment(
            request.userId(),
            new PgPaymentV1Dto.PaymentRequest(
                request.orderId(),
                request.cardType(),
                request.cardNo(),
                request.amount(),
                request.callbackUrl()
            )
        ).data();

        return new PaymentResult(
            transaction.transactionKey(),
            toPaymentStatus(transaction.status()),
            transaction.reason()
        );
    }

    /**
     * 재시도/서킷 차단으로도 끝내 실패하면 PENDING 으로 받는다 (Q5).
     * "결제 실패" 단정이 아니라 "결과 미확정" — 이후 상태조회/콜백으로 정합성을 맞춘다.
     */
    private PaymentResult fallback(PaymentRequest request, Throwable t) {
        return new PaymentResult(null, PaymentStatus.PENDING, "결제 결과 미확정: " + t.getMessage());
    }

    @Retry(name = "pgPayment", fallbackMethod = "getTransactionFallback")
    @Override
    public PaymentResult getTransaction(String userId, String transactionKey) {
        PgPaymentV1Dto.TransactionResponse transaction = pgPaymentClient.getTransaction(userId, transactionKey).data();
        return new PaymentResult(
            transaction.transactionKey(),
            toPaymentStatus(transaction.status()),
            transaction.reason()
        );
    }

    /** 상태 조회가 끝내 실패하면 PENDING 으로 둬서 confirm 이 no-op 되게 한다 — 다음 폴링 주기에 다시 시도한다. */
    private PaymentResult getTransactionFallback(String userId, String transactionKey, Throwable t) {
        return new PaymentResult(transactionKey, PaymentStatus.PENDING, "상태 조회 실패: " + t.getMessage());
    }

    private PaymentStatus toPaymentStatus(PgPaymentV1Dto.TransactionStatus status) {
        return switch (status) {
            case PENDING -> PaymentStatus.PENDING;
            case SUCCESS -> PaymentStatus.SUCCESS;
            case FAILED -> PaymentStatus.FAILED;
        };
    }
}
