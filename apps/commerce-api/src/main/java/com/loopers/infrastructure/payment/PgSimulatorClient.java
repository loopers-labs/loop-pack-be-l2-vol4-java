package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.infrastructure.payment.PgSimulatorFeignClient.PgOrderDto;
import com.loopers.infrastructure.payment.PgSimulatorFeignClient.PgPaymentRequestDto;
import com.loopers.infrastructure.payment.PgSimulatorFeignClient.PgTransactionDto;
import com.loopers.interfaces.api.ApiResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link PgClient} 어댑터 — pg-simulator Feign 호출을 감싸고, 도메인 VO ↔ HTTP DTO를 변환한다.
 * <p>
 * 회복성: pg-simulator는 동기 요청의 40%를 500으로 떨어뜨리므로 {@code @Retry}로 재시도하고,
 * 연속 실패가 누적되면 {@code @CircuitBreaker}가 열려 빠르게 차단한다(설정은 application.yml).
 * <p>
 * orderId 변환: 우리 도메인은 Long, pg-simulator는 "6자 이상 문자열"을 요구 → {@code %06d}로 제로패딩하고
 * 응답/콜백에서 받은 문자열은 {@link Long#parseLong}으로 되돌린다(선행 0 무시).
 */
@Component
@RequiredArgsConstructor
public class PgSimulatorClient implements PgClient {

    static final String INSTANCE = "pgSimulator";

    private final PgSimulatorFeignClient feignClient;

    @Override
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    public PgTransaction requestPayment(PgPaymentRequest request) {
        PgPaymentRequestDto body = new PgPaymentRequestDto(
                toPgOrderId(request.orderId()),
                request.cardType().name(),
                request.cardNo(),
                request.amount(),
                request.callbackUrl()
        );
        ApiResponse<PgTransactionDto> response = feignClient.requestPayment(String.valueOf(request.userId()), body);
        return toTransaction(response.data());
    }

    @Override
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    public List<PgTransaction> findTransactionsByOrder(Long orderId) {
        ApiResponse<PgOrderDto> response = feignClient.findTransactionsByOrder(
                String.valueOf(orderId), toPgOrderId(orderId));
        PgOrderDto order = response.data();
        if (order == null || order.transactions() == null) {
            return List.of();
        }
        return order.transactions().stream()
                .map(PgSimulatorClient::toTransaction)
                .toList();
    }

    /** Long orderId → pg-simulator가 요구하는 "6자 이상" 문자열. */
    static String toPgOrderId(Long orderId) {
        return String.format("%06d", orderId);
    }

    private static PgTransaction toTransaction(PgTransactionDto dto) {
        return new PgTransaction(dto.transactionKey(), PaymentStatus.valueOf(dto.status()), dto.reason());
    }
}
