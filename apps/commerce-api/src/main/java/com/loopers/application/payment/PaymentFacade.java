package com.loopers.application.payment;

import com.loopers.domain.event.OrderCompletedEvent;
import com.loopers.domain.event.PaymentSettledEvent;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgClient.PgRequestCommand;
import com.loopers.domain.payment.PgClient.PgRequestResponse;
import com.loopers.domain.payment.PgRequestException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * 결제 유스케이스 — 외부 PG 호출을 트랜잭션과 분리한다.
 * <p>
 * 핵심 원칙: <b>PG 호출은 어떤 DB 트랜잭션에도 포함되지 않는다</b>.
 * <ol>
 *   <li>PENDING 결제 레코드 저장 (REQUIRES_NEW 로 즉시 커밋)</li>
 *   <li>트랜잭션 밖에서 PG 호출 — 성공/실패/타임아웃 모두 가능</li>
 *   <li>응답에 따라 REQUESTED / TIMEOUT_PENDING 으로 별도 트랜잭션에 전이</li>
 * </ol>
 * <p>
 * 멱등성: orderId UNIQUE 가 1번을 보장. 중복 요청은 기존 결제건 반환.
 */
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PgClient pgClient;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${pg.callback-url}")
    private String callbackUrl;

    /**
     * 결제 요청 시작점.
     * <p>
     * 반환되는 PaymentInfo 의 상태는 PENDING/REQUESTED/TIMEOUT_PENDING 중 하나 — 즉 *최종 상태가 아닐 수 있다*.
     * 최종 상태(SUCCEEDED/FAILED) 는 콜백 또는 복구 폴링으로 채워진다.
     */
    public PaymentInfo request(PaymentCriteria.Request criteria) {
        OrderModel order = orderService.getOrder(criteria.orderId());
        assertOwnership(order, criteria.userId());

        CardType cardType = CardType.parse(criteria.cardType());

        PaymentModel payment = paymentService.createPending(
            order.getId(),
            order.getUserId(),
            order.getFinalPrice(),
            cardType,
            criteria.cardNo()
        );

        // 멱등 통과 — 이미 PENDING 이 아닌 결제는 그대로 반환
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return PaymentInfo.from(payment);
        }

        try {
            PgRequestResponse response = pgClient.request(new PgRequestCommand(
                order.getId(),
                cardType,
                criteria.cardNo(),
                order.getFinalPrice(),
                callbackUrl,
                order.getUserId()
            ));
            paymentService.markRequested(payment.getId(), response.transactionKey());
        } catch (PgRequestException pgFailure) {
            // 회로 차단 / 타임아웃 / 5xx 모두 여기로 — 복구 스케줄러에 위임
            log.warn("PG 요청 실패 — TIMEOUT_PENDING 으로 전환. orderId={}, paymentId={}",
                order.getId(), payment.getId());
            paymentService.markTimeoutPending(payment.getId(), pgFailure.getMessage());
        }

        // 다시 조회 — 최신 상태 반영
        return PaymentInfo.from(paymentService.getById(payment.getId()));
    }

    /**
     * PG 콜백 처리. 멱등 — 같은 콜백이 N 번 와도 안전.
     * <p>
     * 콜백은 PG → 우리 시스템 방향이므로, 인증·서명 검증은 별도 단계(인터페이스 레이어) 가 책임진다.
     */
    public void handleCallback(PaymentCriteria.Callback callback) {
        if (callback.transactionKey() == null || callback.transactionKey().isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "콜백에 transactionKey 가 없습니다.");
        }
        PaymentModel payment = paymentService.getByTransactionKey(callback.transactionKey());
        applyExternalStatus(payment.getId(), callback.status(), callback.reason());
    }

    /**
     * 복구 스케줄러 / 수동 동기화에서 호출하는 상태 반영 메서드.
     */
    public void applyExternalStatus(Long paymentId, String externalStatus, String reason) {
        if (externalStatus == null) {
            log.warn("외부 상태가 null — 무시. paymentId={}", paymentId);
            return;
        }
        String normalized = externalStatus.trim().toUpperCase();
        switch (normalized) {
            case "SUCCESS", "SUCCEEDED" -> {
                paymentService.markSucceeded(paymentId);
                PaymentModel payment = paymentService.getById(paymentId);
                boolean orderMarkedPaid = tryMarkOrderPaid(payment);
                publishSettled(payment, PaymentSettledEvent.Outcome.SUCCEEDED);
                if (orderMarkedPaid) {
                    publishOrderCompleted(payment.getOrderId());
                }
            }
            case "FAILED", "FAIL", "INVALID_CARD", "LIMIT_EXCEEDED" -> {
                paymentService.markFailed(paymentId, reason);
                PaymentModel payment = paymentService.getById(paymentId);
                tryMarkOrderFailed(payment);
                publishSettled(payment, PaymentSettledEvent.Outcome.FAILED);
            }
            default -> log.info("외부 상태가 비종료(PENDING) — 추후 폴링으로 재확인. paymentId={}, status={}",
                paymentId, normalized);
        }
    }

    private boolean tryMarkOrderPaid(PaymentModel payment) {
        try {
            orderService.markPaid(payment.getOrderId());
            return true;
        } catch (CoreException e) {
            // 이미 종료 상태인 주문 등 — 멱등 통과
            log.info("주문 PAID 전이 스킵 (이미 종료 또는 부재): orderId={}, cause={}",
                payment.getOrderId(), e.getMessage());
            return false;
        }
    }

    private void tryMarkOrderFailed(PaymentModel payment) {
        try {
            orderService.markFailed(payment.getOrderId());
        } catch (CoreException e) {
            log.info("주문 FAILED 전이 스킵: orderId={}, cause={}",
                payment.getOrderId(), e.getMessage());
        }
    }

    private void publishSettled(PaymentModel payment, PaymentSettledEvent.Outcome outcome) {
        eventPublisher.publishEvent(new PaymentSettledEvent(
            payment.getId(),
            payment.getOrderId(),
            payment.getUserId(),
            outcome,
            payment.getAmount(),
            ZonedDateTime.now()
        ));
    }

    private void publishOrderCompleted(Long orderId) {
        OrderModel order = orderService.getOrder(orderId);
        eventPublisher.publishEvent(new OrderCompletedEvent(
            order.getId(),
            order.getUserId(),
            order.getFinalPrice(),
            order.getItems().stream()
                .map(item -> new OrderCompletedEvent.Line(
                    item.getProductId(), item.getQuantity(), item.getPriceSnapshot()))
                .toList(),
            ZonedDateTime.now()
        ));
    }

    private static void assertOwnership(OrderModel order, Long userId) {
        if (userId == null || !userId.equals(order.getUserId())) {
            // 본인 외 결제 시도 — 정보 노출 방지 위해 NOT_FOUND
            throw new CoreException(ErrorType.NOT_FOUND,
                "[orderId = " + order.getId() + "] 주문을 찾을 수 없습니다.");
        }
    }

    public PaymentInfo getByOrderId(Long orderId) {
        return PaymentInfo.from(paymentService.getByOrderId(orderId));
    }
}
