package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PgPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PgPaymentGateway.class);

    private final PgClient pgClient;
    private final String callbackUrl;

    public PgPaymentGateway(PgClient pgClient, @Value("${pg.callback-url}") String callbackUrl) {
        this.pgClient = pgClient;
        this.callbackUrl = callbackUrl;
    }

    /**
     * 예외를 잡지 않고 그대로 전파한다 → resilience4j가 본다.
     * 기본 aspect 순서상 Retry가 CircuitBreaker를 감싸며(Retry 최외곽), 최종 실패는 fallback이 도메인 예외로 변환한다.
     * - 재시도: 요청단계 5xx(FeignServerException)만 (yml). 타임아웃(RetryableException)은 제외 → 이중 결제 방지.
     * - 서킷: 반복 실패 시 open → 빠른 실패 + fallback.
     */
    @Retry(name = "pgRetry", fallbackMethod = "requestPaymentFallback")
    @CircuitBreaker(name = "pgCircuit")
    @Override
    public Result requestPayment(Command command) {
        PgV1Dto.PaymentRequest request = new PgV1Dto.PaymentRequest(
            String.valueOf(command.orderId()),
            command.cardType().name(),
            command.cardNo(),
            command.amount(),
            callbackUrl
        );
        PgV1Dto.PaymentResponse response = pgClient.requestPayment(String.valueOf(command.userId()), request);
        PgV1Dto.PaymentResponse.Data data = response.data();
        return new Result(data.transactionKey(), PaymentStatus.valueOf(data.status()), data.reason());
    }

    /**
     * 재시도 소진/서킷 open/타임아웃 등 모든 PG 호출 실패의 최종 착지점.
     * 외부 장애를 도메인 예외로 변환해 내부 시스템이 정상 응답하도록 한다.
     */
    @SuppressWarnings("unused")
    private Result requestPaymentFallback(Command command, Throwable t) {
        log.warn("PG 결제 요청 실패(재시도/서킷 처리 후): {}", t.toString());
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청이 지연되고 있습니다. 잠시 후 다시 시도해주세요.");
    }

    /**
     * PG에 결제 진짜 상태를 조회한다. GET은 멱등하므로 콜백 검증·복구에서 안전하게 재호출 가능.
     * 조회 실패는 도메인 예외로 변환(호출자가 건너뛰거나 다음 주기에 재시도).
     */
    @Override
    public Result getTransaction(Long userId, String transactionKey) {
        try {
            PgV1Dto.PaymentResponse response = pgClient.getTransaction(String.valueOf(userId), transactionKey);
            PgV1Dto.PaymentResponse.Data data = response.data();
            return new Result(data.transactionKey(), PaymentStatus.valueOf(data.status()), data.reason());
        } catch (FeignException e) {
            log.warn("PG 결제 조회 실패: transactionKey={}, httpStatus={}", transactionKey, e.status());
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 조회에 실패했습니다.");
        }
    }

    /**
     * orderId로 PG 결제건 목록을 조회한다. PG가 404(결제건 없음)면 빈 목록을 반환해 호출자가 "미아 만료"로 판단하게 한다.
     */
    @Override
    public java.util.List<Result> findTransactionsByOrderId(Long userId, Long orderId) {
        try {
            PgV1Dto.OrderResponse response = pgClient.findByOrderId(String.valueOf(userId), String.valueOf(orderId));
            return response.data().transactions().stream()
                .map(t -> new Result(t.transactionKey(), PaymentStatus.valueOf(t.status()), t.reason()))
                .toList();
        } catch (FeignException.NotFound e) {
            return java.util.List.of();
        } catch (FeignException e) {
            log.warn("PG 주문별 결제 조회 실패: orderId={}, httpStatus={}", orderId, e.status());
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 조회에 실패했습니다.");
        }
    }
}
