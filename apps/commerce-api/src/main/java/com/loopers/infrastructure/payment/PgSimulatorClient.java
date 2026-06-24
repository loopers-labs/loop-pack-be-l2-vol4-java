package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.infrastructure.payment.PgSimulatorFeignClient.PgOrderDto;
import com.loopers.infrastructure.payment.PgSimulatorFeignClient.PgPaymentRequestDto;
import com.loopers.infrastructure.payment.PgSimulatorFeignClient.PgTransactionDto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link PgClient} 어댑터 — pg-simulator Feign 호출을 감싸고, 도메인 VO ↔ HTTP DTO를 변환한다.
 * <p>
 * 회복성: pg-simulator는 동기 요청의 40%를 500으로 떨어뜨리므로 {@code @Retry}로 재시도하고,
 * 연속 실패가 누적되면 {@code @CircuitBreaker}가 열려 빠르게 차단한다(설정은 application.yml).
 * <p>
 * 폴백: 재시도 소진 또는 서킷 OPEN({@code CallNotPermittedException})으로 호출이 끝내 실패하면, 가장 바깥
 * aspect인 {@code @Retry}의 fallbackMethod가 인프라 예외(Feign/Resilience4j 타입)를 도메인
 * {@link CoreException}({@link ErrorType#SERVICE_UNAVAILABLE}, 503)으로 변환한다. 덕분에 application/domain
 * 레이어는 외부 라이브러리 예외를 모른 채 "PG 일시 불가"만 알게 된다(추후 폴백 전략 강화 여지).
 * <p>
 * orderId 변환: 우리 도메인은 Long(앱 생성 TSID, 13자리 이상)이라 pg-simulator의 "6자 이상 문자열" 조건을
 * 그대로 충족 → 별도 패딩 없이 {@link String#valueOf(long)}로 문자열화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgSimulatorClient implements PgClient {

    static final String INSTANCE = "pgSimulator";

    private final PgSimulatorFeignClient feignClient;

    @Override
    @Retry(name = INSTANCE, fallbackMethod = "requestPaymentFallback")
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
    @Retry(name = INSTANCE, fallbackMethod = "findTransactionsByOrderFallback")
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

    /**
     * 재시도 소진/서킷 OPEN으로 결제 요청이 끝내 실패했을 때의 폴백. 인프라 예외를 도메인 503으로 변환한다.
     * (지금은 단순 실패 전파 — 결제는 호출부에서 FAILED로 정리된다. 폴백 전략은 추후 강화 여지.)
     */
    private PgTransaction requestPaymentFallback(PgPaymentRequest request, Throwable t) {
        log.warn("PG 결제 요청 실패 — 폴백 (orderId={}, cause={}: {})",
                request.orderId(), t.getClass().getSimpleName(), t.getMessage());
        throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "PG 결제 요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }

    /** 거래 조회(reconcile) 폴백. 진실원천을 못 읽었으므로 503으로 전파해 다음 주기에 재시도하게 한다. */
    private List<PgTransaction> findTransactionsByOrderFallback(Long orderId, Throwable t) {
        log.warn("PG 거래 조회 실패 — 폴백 (orderId={}, cause={}: {})",
                orderId, t.getClass().getSimpleName(), t.getMessage());
        throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "PG 거래 조회를 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }

    /** Long orderId(TSID) → pg-simulator 문자열. TSID는 13자리 이상이라 패딩 불필요. */
    static String toPgOrderId(Long orderId) {
        return String.valueOf(orderId);
    }

    private static PgTransaction toTransaction(PgTransactionDto dto) {
        return new PgTransaction(dto.transactionKey(), PaymentStatus.valueOf(dto.status()), dto.reason());
    }
}
