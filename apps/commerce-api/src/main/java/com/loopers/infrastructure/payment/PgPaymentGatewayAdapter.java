package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgRequestResult;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.interfaces.api.ApiResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgPaymentGatewayAdapter implements PaymentGateway {

    private static final String CB = "pgPayment";
    private static final String USER_ID_HEADER = "X-USER-ID";

    private final RestClient pgRestClient;
    private final PgProperties properties;

    /**
     * fallbackMethod 를 @Retry(최외곽 aspect)에 부착한다. @CircuitBreaker 에 달면 내부 aspect 가
     * 첫 실패에서 폴백을 정상 반환해버려 재시도가 동작하지 않는다.
     */
    @Retry(name = CB, fallbackMethod = "requestPaymentFallback")
    @CircuitBreaker(name = CB)
    @Override
    public PgRequestResult requestPayment(PgPaymentCommand command) {
        ApiResponse<TransactionResponse> response = pgRestClient.post()
                .uri("/api/v1/payments")
                .header(USER_ID_HEADER, String.valueOf(command.userId()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PaymentRequest(
                        toPgOrderId(command.orderId()),
                        command.cardType(),
                        command.cardNo(),
                        command.amount(),
                        properties.callbackUrl()
                ))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        String transactionKey = response != null && response.data() != null ? response.data().transactionKey() : null;
        if (transactionKey == null) {
            return PgRequestResult.notAttempted();
        }
        return PgRequestResult.accepted(transactionKey);
    }

    @SuppressWarnings("unused")
    private PgRequestResult requestPaymentFallback(PgPaymentCommand command, Throwable t) {
        // read timeout: PG 에 거래가 생성됐을 수 있어 orderId 역조회 복구 대상으로 둔다.
        if (t instanceof ResourceAccessException) {
            log.warn("[orderId = {}] PG 요청 타임아웃 — 역조회 복구 대상으로 둔다.", command.orderId());
            return PgRequestResult.timeout();
        }
        // 서킷 Open / 5xx 소진: PG 에 거래 미생성 보장 → 재요청/거절 대상.
        log.warn("[orderId = {}] PG 요청 폴백(미시도): {}", command.orderId(), t.toString());
        return PgRequestResult.notAttempted();
    }

    @Override
    public Optional<PgTransaction> getTransaction(Long userId, String transactionKey) {
        try {
            ApiResponse<TransactionDetailResponse> response = pgRestClient.get()
                    .uri("/api/v1/payments/{transactionKey}", transactionKey)
                    .header(USER_ID_HEADER, String.valueOf(userId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.data() == null) {
                return Optional.empty();
            }
            TransactionDetailResponse data = response.data();
            return Optional.of(new PgTransaction(data.transactionKey(), data.status(), data.amount(), data.reason()));
        } catch (RuntimeException e) {
            log.warn("[transactionKey = {}] PG 거래 조회 실패: {}", transactionKey, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public Optional<PgTransaction> findByOrderId(Long userId, Long orderId) {
        try {
            ApiResponse<OrderResponse> response = pgRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/payments")
                            .queryParam("orderId", toPgOrderId(orderId))
                            .build())
                    .header(USER_ID_HEADER, String.valueOf(userId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.data() == null) {
                return Optional.empty();
            }
            List<TransactionResponse> transactions = response.data().transactions();
            if (transactions == null || transactions.isEmpty()) {
                return Optional.empty();
            }
            // 종결(SUCCESS/FAILED) 거래가 있으면 그것을, 없으면 첫 거래를 반환.
            TransactionResponse picked = transactions.stream()
                    .filter(tx -> tx.status() != PaymentStatus.PENDING)
                    .findFirst()
                    .orElse(transactions.get(0));
            return Optional.of(new PgTransaction(picked.transactionKey(), picked.status(), null, picked.reason()));
        } catch (RuntimeException e) {
            log.warn("[orderId = {}] PG 주문별 거래 조회 실패: {}", orderId, e.toString());
            return Optional.empty();
        }
    }

    /** PG 는 orderId 가 6자 이상 문자열이어야 한다. DB Long id 를 0-padding 으로 변환. */
    private String toPgOrderId(Long orderId) {
        return String.format("%06d", orderId);
    }

    // --- PG(pg-simulator) HTTP 와이어 DTO. 이 어댑터 전용 번역 타입. ---
    private record PaymentRequest(
            String orderId,
            CardType cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {
    }

    private record TransactionResponse(
            String transactionKey,
            PaymentStatus status,
            String reason
    ) {
    }

    private record TransactionDetailResponse(
            String transactionKey,
            String orderId,
            CardType cardType,
            String cardNo,
            Long amount,
            PaymentStatus status,
            String reason
    ) {
    }

    private record OrderResponse(
            String orderId,
            List<TransactionResponse> transactions
    ) {
    }
}