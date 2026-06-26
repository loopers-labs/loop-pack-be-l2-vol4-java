package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PaymentGatewayTimeoutException;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;

/**
 * PaymentGateway 어댑터. Feign 예외를 재시도 정책과 직결되는 도메인 예외로 분기 변환한다(설계 §7.4, 계획 §2.4).
 * <ul>
 *   <li>5xx · Connect Timeout/연결 실패 → {@link PaymentGatewayException} (미도달, 재시도 안전)</li>
 *   <li>Read Timeout → {@link PaymentGatewayTimeoutException} (응답 유실 가능, 자동 재시도 금지)</li>
 *   <li>요청(POST) 4xx → {@link CoreException}(BAD_REQUEST) (우리 측 버그, CB/Retry 모두 무시)</li>
 *   <li>조회(GET) 404 → 빈 결과(주문 없음, 미도달 신호) — 예외로 흘리지 않는다</li>
 * </ul>
 * fallbackMethod 는 두지 않는다 — Fallback(PENDING "처리 중")은 facade 의 try-catch 한 곳에서 처리한다.
 */
@RequiredArgsConstructor
@Component
public class PaymentGatewayImpl implements PaymentGateway {

    private final PaymentGatewayFeignClient feignClient;
    private final PaymentGatewayProperties properties;

    @Override
    @Retry(name = "paymentRequest")
    @CircuitBreaker(name = "paymentRequest")
    public PgTransaction request(PgPaymentCommand command) {
        PgPaymentRequest body = new PgPaymentRequest(
                command.orderNumber(),
                command.cardType().name(),
                command.cardNo(),
                command.amount(),
                properties.callbackUrl());
        try {
            PgApiResponse<PgTransactionResponse> response = feignClient.request(properties.userId(), body);
            PgTransactionResponse data = response.data();
            return new PgTransaction(data.transactionKey(), mapStatus(data.status()), data.reason(), null);
        } catch (RetryableException e) {
            // RetryableException 은 FeignException 의 하위 타입이므로 먼저 잡는다(타임아웃/연결 실패).
            throw mapTimeoutOrConnect(e);
        } catch (FeignException e) {
            throw mapRequestFeignException(e);
        }
    }

    @Override
    @CircuitBreaker(name = "paymentQuery")
    public Optional<PgTransaction> findByTransactionKey(String transactionKey) {
        try {
            PgApiResponse<PgTransactionDetailResponse> response =
                    feignClient.getByKey(properties.userId(), transactionKey);
            PgTransactionDetailResponse d = response.data();
            return Optional.of(new PgTransaction(d.transactionKey(), mapStatus(d.status()), d.reason(), d.amount()));
        } catch (RetryableException e) {
            throw mapTimeoutOrConnect(e);
        } catch (FeignException.NotFound e) {
            return Optional.empty(); // 주문 없음(미도달)
        } catch (FeignException e) {
            throw new PaymentGatewayException("PG 단건 조회 실패(status=" + e.status() + ")", e);
        }
    }

    @Override
    @CircuitBreaker(name = "paymentQuery")
    public List<PgTransaction> findByOrderId(String orderNumber) {
        try {
            PgApiResponse<PgOrderResponse> response = feignClient.getByOrderId(properties.userId(), orderNumber);
            PgOrderResponse order = response.data();
            if (order == null || order.transactions() == null) {
                return List.of();
            }
            return order.transactions().stream()
                    .map(t -> new PgTransaction(t.transactionKey(), mapStatus(t.status()), t.reason(), null))
                    .toList();
        } catch (RetryableException e) {
            throw mapTimeoutOrConnect(e);
        } catch (FeignException.NotFound e) {
            return List.of(); // 주문 없음(미도달)
        } catch (FeignException e) {
            throw new PaymentGatewayException("PG 주문별 조회 실패(status=" + e.status() + ")", e);
        }
    }

    private RuntimeException mapRequestFeignException(FeignException e) {
        int status = e.status();
        if (status >= 400 && status < 500) {
            // 입력 검증 실패 등 우리 측 버그 — PG 장애가 아니므로 CB/Retry 에서 모두 무시
            return new CoreException(ErrorType.BAD_REQUEST, "PG 결제 요청이 거부되었습니다.");
        }
        // 5xx — 요청 미도달(돈 안 빠짐), 재시도 안전
        return new PaymentGatewayException("PG 결제 요청 실패(status=" + status + ")", e);
    }

    private RuntimeException mapTimeoutOrConnect(RetryableException e) {
        Throwable cause = e.getCause();
        boolean readTimeout = cause instanceof SocketTimeoutException
                && cause.getMessage() != null
                && cause.getMessage().toLowerCase().contains("read");
        if (readTimeout) {
            // 요청은 도달했는데 응답만 유실 가능 → 블라인드 재시도 금지(이중결제 위험), PENDING 유지 후 폴링 확정
            return new PaymentGatewayTimeoutException("PG Read Timeout", e);
        }
        // Connect Timeout / 연결 거부 등 미도달 → 재시도 안전
        return new PaymentGatewayException("PG 연결 실패(미도달)", e);
    }

    private PaymentStatus mapStatus(String pgStatus) {
        if (pgStatus == null) {
            throw new PaymentGatewayException("PG 응답 상태가 비어 있습니다.");
        }
        return switch (pgStatus) {
            case "SUCCESS" -> PaymentStatus.PAID;
            case "FAILED" -> PaymentStatus.FAILED;
            case "PENDING" -> PaymentStatus.PENDING;
            default -> throw new PaymentGatewayException("알 수 없는 PG 상태: " + pgStatus);
        };
    }
}
