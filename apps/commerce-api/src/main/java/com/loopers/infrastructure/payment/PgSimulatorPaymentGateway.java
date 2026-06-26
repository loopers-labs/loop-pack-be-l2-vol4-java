package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentGateway;
import com.loopers.application.payment.PaymentGatewayCommand;
import com.loopers.application.payment.PaymentOrderIdMapper;
import com.loopers.domain.payment.PaymentCardType;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentGatewayStatus;
import com.loopers.domain.payment.PaymentPendingReason;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Component
public class PgSimulatorPaymentGateway implements PaymentGateway {

    private static final String PAYMENT_REQUEST_CIRCUIT_BREAKER_NAME = "pgPaymentRequest";
    private static final String PAYMENT_LOOKUP_CIRCUIT_BREAKER_NAME = "pgPaymentLookup";

    private final RestClient pgSimulatorRestClient;
    private final PgSimulatorProperties properties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public boolean isRequestAvailable() {
        var state = circuitBreakerRegistry.circuitBreaker(PAYMENT_REQUEST_CIRCUIT_BREAKER_NAME).getState();
        return state != io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN
            && state != io.github.resilience4j.circuitbreaker.CircuitBreaker.State.FORCED_OPEN;
    }

    @Override
    @CircuitBreaker(name = PAYMENT_REQUEST_CIRCUIT_BREAKER_NAME, fallbackMethod = "requestFallback")
    public PaymentGatewayResult request(PaymentGatewayCommand command) {
        PgPaymentRequest request = new PgPaymentRequest(
            PaymentOrderIdMapper.toPgOrderId(command.orderId()),
            PgCardType.from(command.cardType()),
            command.cardNo(),
            command.amount(),
            properties.callbackUrl()
        );

        PgTransactionResponse response = pgSimulatorRestClient.post()
            .uri("/api/v1/payments")
            .header("X-USER-ID", command.userLoginId())
            .body(request)
            .retrieve()
            .body(PgApiResponseOfTransaction.class)
            .data();

        return response.toGatewayResult();
    }

    @Override
    @Retry(name = "pgPaymentStatusLookup", fallbackMethod = "getByOrderFallback")
    @CircuitBreaker(name = PAYMENT_LOOKUP_CIRCUIT_BREAKER_NAME)
    public PaymentGatewayResult getByOrder(String userLoginId, Long orderId) {
        PgOrderResponse response;
        try {
            response = pgSimulatorRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/payments")
                    .queryParam("orderId", PaymentOrderIdMapper.toPgOrderId(orderId))
                    .build())
                .header("X-USER-ID", userLoginId)
                .retrieve()
                .body(PgApiResponseOfOrder.class)
                .data();
        } catch (HttpClientErrorException.NotFound ignored) {
            return PaymentGatewayResult.pending(
                null,
                PaymentPendingReason.PG_LOOKUP_EMPTY,
                "PG 결제 조회 결과가 비어있습니다."
            );
        }

        return response.transactions().stream()
            .max(Comparator.comparing(PgTransactionResponse::transactionKey))
            .map(PgTransactionResponse::toGatewayResult)
            .orElse(PaymentGatewayResult.pending(
                null,
                PaymentPendingReason.PG_LOOKUP_EMPTY,
                "PG 결제 조회 결과가 비어있습니다."
            ));
    }

    private PaymentGatewayResult requestFallback(PaymentGatewayCommand command, Throwable throwable) {
        return PaymentGatewayResult.pending(
            null,
            pendingReasonOfRequestFailure(throwable),
            "PG 요청 결과를 확인하지 못했습니다: " + throwable.getClass().getSimpleName()
        );
    }

    private PaymentGatewayResult getByOrderFallback(String userLoginId, Long orderId, Throwable throwable) {
        return PaymentGatewayResult.pending(
            null,
            pendingReasonOfLookupFailure(throwable),
            "PG 조회 결과를 확인하지 못했습니다: " + throwable.getClass().getSimpleName()
        );
    }

    private PaymentPendingReason pendingReasonOfRequestFailure(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            return PaymentPendingReason.CB_OPEN;
        }
        if (throwable instanceof ResourceAccessException) {
            return PaymentPendingReason.TIMEOUT_UNKNOWN;
        }
        return PaymentPendingReason.PG_REQUEST_FAILED;
    }

    private PaymentPendingReason pendingReasonOfLookupFailure(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            return PaymentPendingReason.CB_OPEN;
        }
        if (throwable instanceof ResourceAccessException) {
            return PaymentPendingReason.TIMEOUT_UNKNOWN;
        }
        return PaymentPendingReason.PG_LOOKUP_FAILED;
    }

    private record PgPaymentRequest(
        String orderId,
        PgCardType cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {
    }

    private record PgApiResponseOfTransaction(
        PgMetadata meta,
        PgTransactionResponse data
    ) {
    }

    private record PgApiResponseOfOrder(
        PgMetadata meta,
        PgOrderResponse data
    ) {
    }

    private record PgMetadata(
        String result,
        String errorCode,
        String message
    ) {
    }

    private record PgOrderResponse(
        String orderId,
        List<PgTransactionResponse> transactions
    ) {
    }

    private record PgTransactionResponse(
        String transactionKey,
        PgTransactionStatus status,
        String reason
    ) {
        PaymentGatewayResult toGatewayResult() {
            if (status == PgTransactionStatus.SUCCESS) {
                return PaymentGatewayResult.success(transactionKey, reason);
            }
            if (status == PgTransactionStatus.FAILED) {
                return PaymentGatewayResult.failed(transactionKey, reason);
            }
            return PaymentGatewayResult.pending(transactionKey, reason);
        }
    }

    private enum PgCardType {
        SAMSUNG,
        KB,
        HYUNDAI;

        static PgCardType from(PaymentCardType cardType) {
            return PgCardType.valueOf(cardType.name());
        }
    }

    private enum PgTransactionStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}
