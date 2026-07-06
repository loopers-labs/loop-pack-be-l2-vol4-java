package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.PaymentErrorCode;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayCommand;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgProvider;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 단일 PG(Toss) 게이트웨이. PG 전송(Feign)·응답 매핑·provider 스탬프·callbackUrl 조립을 담당하고,
 * 회복탄력성(CB/Retry/RateLimiter) 조합은 {@link PgResilienceExecutor} 에 위임한다.
 * "안 닿음"(CallNotPermitted/RequestNotPermitted) 처리는 단일 PG 정책상 불가 응답으로 번역한다
 * — 멀티 PG 가 되면 여기가 failover 분기점이 된다.
 */
@Slf4j
@Component
public class PgPaymentGateway implements PaymentGateway {

    private static final PgProvider PROVIDER = PgProvider.TOSS;
    private static final String INSTANCE = "toss";
    private static final String CALLBACK_PATH = "/api/v1/payments/callback/toss";

    private final TossPgClient tossPgClient;
    private final String callbackUrl;
    private final PgResilienceExecutor resilienceExecutor;

    public PgPaymentGateway(TossPgClient tossPgClient,
                            @Value("${payment.pg.callback-base-url}") String callbackBaseUrl,
                            PgResilienceExecutor resilienceExecutor) {
        this.tossPgClient = tossPgClient;
        this.callbackUrl = callbackBaseUrl + CALLBACK_PATH;
        this.resilienceExecutor = resilienceExecutor;
    }

    @Override
    public PaymentGatewayResult request(PaymentGatewayCommand command) {
        PgPaymentRequest body = new PgPaymentRequest(
                command.orderNumber(),
                command.cardType().name(),
                command.cardNo(),
                command.amount(),
                callbackUrl);

        PgTransactionResponse data;
        try {
            data = resilienceExecutor.call(INSTANCE,
                    () -> tossPgClient.request(String.valueOf(command.userId()), body).data());
        } catch (CallNotPermittedException | RequestNotPermitted e) {
            // PG 에 안 나간 게 확실. 단일 PG 라 불가 처리(멀티 PG 면 여기서 failover).
            log.warn("PG 호출 차단 provider={} orderNumber={} 사유={}", PROVIDER, command.orderNumber(), e.getClass().getSimpleName());
            throw new CoreException(ErrorType.INTERNAL_ERROR, PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }

        log.info("PG 결제 요청 수락 provider={} orderNumber={} transactionKey={} status={}",
                PROVIDER, command.orderNumber(), data.transactionKey(), data.status());
        return new PaymentGatewayResult(
                data.transactionKey(),
                PROVIDER,
                PaymentStatus.valueOf(data.status()),
                data.reason());
    }

    @Override
    public PaymentGatewayResult inquire(Long userId, String transactionKey) {
        PgTransactionDetail detail = tossPgClient.getTransaction(String.valueOf(userId), transactionKey).data();
        return new PaymentGatewayResult(
                detail.transactionKey(), PROVIDER, PaymentStatus.valueOf(detail.status()), detail.reason());
    }

    @Override
    public List<PaymentGatewayResult> inquireByOrder(Long userId, String orderNumber) {
        PgOrderTransactions data = tossPgClient.getTransactionsByOrder(String.valueOf(userId), orderNumber).data();
        if (data == null || data.transactions() == null) {
            return List.of();
        }
        return data.transactions().stream()
                .map(tx -> new PaymentGatewayResult(
                        tx.transactionKey(), PROVIDER, PaymentStatus.valueOf(tx.status()), tx.reason()))
                .toList();
    }
}
