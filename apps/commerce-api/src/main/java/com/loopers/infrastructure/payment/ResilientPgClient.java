package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgClientException;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgTransactionDetail;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link PgClientImpl} 을 감싸 서킷브레이커를 적용하는 데코레이터.
 * <p>빈을 분리함으로써 CircuitBreaker(outer) → Retry(inner, {@link PgClientImpl}) 순서를 보장한다.
 * 회로가 열리거나(CallNotPermittedException) 호출이 실패하면 폴백이 이를 {@link PgClientException} 으로 변환하여,
 * 호출자(PaymentFacade)가 결제를 PENDING 으로 유지하고 <b>사용자에게는 정상 응답</b>하도록 한다.
 */
@Primary
@Component
@RequiredArgsConstructor
public class ResilientPgClient implements PgClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientPgClient.class);
    private static final String RESILIENCE_INSTANCE = "pg";

    private final PgClientImpl delegate;

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "requestPaymentFallback")
    public PgPaymentResult requestPayment(PgPaymentCommand command) {
        return delegate.requestPayment(command);
    }

    // package-private: resilience4j 가 폴백으로 호출하며, 단위 테스트에서 직접 검증한다.
    @SuppressWarnings("unused")
    PgPaymentResult requestPaymentFallback(PgPaymentCommand command, Throwable t) {
        log.warn("PG 서킷 차단/장애로 결제 요청을 보류합니다. orderId={}, cause={}", command.orderId(), t.toString());
        throw new PgClientException("PG 일시 장애로 결제 요청을 처리할 수 없습니다.", t);
    }

    @Override
    public Optional<PgTransactionDetail> getTransaction(String userId, String transactionKey) {
        return delegate.getTransaction(userId, transactionKey);
    }

    @Override
    public List<PgTransactionDetail> findTransactionsByOrderId(String userId, String orderId) {
        return delegate.findTransactionsByOrderId(userId, orderId);
    }
}
