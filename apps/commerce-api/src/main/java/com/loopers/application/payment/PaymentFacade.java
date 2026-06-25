package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayRouter;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgPermanentException;
import com.loopers.domain.payment.PgProvider;
import com.loopers.domain.payment.PgRequest;
import com.loopers.domain.payment.PgResponse;
import com.loopers.domain.payment.PgUnknownException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 결제 유스케이스 조립. 트랜잭션 경계 설계:
 *  - TX1 (createRequested) : Order 검증 + Payment(REQUESTED) 저장 — PG 호출 전 좀비 주문 방지
 *  - TX 밖                  : PG 호출 (외부 시스템, Resilience4j 적용)
 *  - TX2 (markXxx)          : PG 결과에 따른 상태 전이 (+ 필요 시 이벤트 발행)
 *
 * 외부 호출을 트랜잭션 안에 두지 않는다 — 1~5s 외부 지연이 row lock 을 점유하면 시스템 마비.
 *
 * 결과 분기:
 *  - 정상 응답              → markInProgress (사용자에겐 "처리 중", 콜백/폴링이 확정)
 *  - PgPermanentException   → markFailed     (즉시 FAILED, 재고 복구 이벤트)
 *  - PgUnknownException     → markUnknown    (폴링이 확정할 때까지 보존)
 *  - CallNotPermittedExc    → markFailed     (CB Open + 다른 PG 없음 → 더 시도할 수 없음)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final PaymentGatewayRouter router;

    public PaymentInfo requestPayment(Long userId, PaymentCommand command) {
        if (userId == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "결제하려면 로그인이 필요합니다.");
        }

        // 1. Order 검증 — 본인 주문 + CREATED 상태만 결제 가능
        Order order = orderRepository.find(command.orderId())
            .filter(o -> o.getUserId().equals(userId))
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[orderId = " + command.orderId() + "] 주문을 찾을 수 없습니다."));
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new CoreException(ErrorType.CONFLICT,
                "[status = " + order.getStatus() + "] 이미 처리된 주문입니다.");
        }

        // 2. Payment(REQUESTED) 저장 — 좀비 주문 방지의 핵심. PG 호출이 실패해도 row 는 남아 폴링/배치가 복구 가능.
        Payment payment = paymentService.createRequested(
            order.getId(), userId, PgProvider.PG_SIMULATOR,
            order.getFinalAmount(), command.cardType(), lastFour(command.cardNo())
        );

        // 3. PG 라우팅 + 호출 (트랜잭션 밖)
        try {
            PaymentGateway gateway = router.select(payment);
            PgResponse response = gateway.request(new PgRequest(
                order.getId(), userId, command.cardType(), command.cardNo(), order.getFinalAmount()
            ));
            paymentService.markInProgress(payment.getId(), response.transactionKey());
        } catch (PgPermanentException e) {
            log.warn("[Payment] 영구 에러 → FAILED. paymentId={}, reason={}", payment.getId(), e.getMessage());
            paymentService.markFailed(payment.getId(), e.getMessage());
        } catch (PgUnknownException e) {
            log.warn("[Payment] 결과 미확정 → UNKNOWN (폴링 위임). paymentId={}, reason={}", payment.getId(), e.getMessage());
            paymentService.markUnknown(payment.getId(), e.getMessage());
        } catch (CallNotPermittedException e) {
            log.error("[Payment] Circuit Breaker Open → FAILED. paymentId={}", payment.getId());
            paymentService.markFailed(payment.getId(), "PG 회로 차단됨 (장애 감지)");
        }

        // 4. 현재 상태를 응답으로 매핑 (UserStatus PROCESSING/SUCCESS/FAILED)
        return PaymentInfo.from(paymentService.getPayment(payment.getId()));
    }

    /** 카드번호 마지막 4자리만 추출. Payment 엔티티는 마스킹된 값만 영속화 (PCI-DSS). */
    private static String lastFour(String cardNo) {
        String digits = cardNo.replace("-", "");
        return digits.substring(digits.length() - 4);
    }
}
