package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentDeclinedException;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayUnavailableException;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgRequestResult;
import com.loopers.domain.payment.PgStatus;
import com.loopers.domain.payment.PgTransactionResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 흐름 오케스트레이션.
 * ⚠ 이 메서드 자체는 @Transactional 이 아니다. TransactionTemplate 로 트랜잭션을 명시적으로 쪼개
 *   '외부 PG 호출이 트랜잭션 밖'에서 일어나도록 한다 (커넥션 점유/정합성 문제 회피).
 *
 *   TX1(주문검증 + Payment PENDING 저장) → [트랜잭션 밖] PG 호출 → TX2(결과 반영)
 */
@Slf4j
@Component
public class PaymentFacade {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate tx;

    public PaymentFacade(OrderRepository orderRepository,
                         PaymentRepository paymentRepository,
                         PaymentGateway paymentGateway,
                         PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public PaymentInfo pay(PaymentCriteria criteria) {
        // ── TX1: 주문 검증 + Payment(PENDING) 저장 ──────────────────────────
        PaymentModel pending = tx.execute(status -> createPendingPayment(criteria));

        // ── 트랜잭션 밖: PG 결제 요청 (느리거나 실패해도 DB 커넥션을 쥐지 않는다) ──
        try {
            PgRequestResult result = paymentGateway.requestPayment(new PgPaymentCommand(
                    pending.getOrderId(),
                    pending.getCardType(),
                    pending.getCardNo(),
                    pending.getAmount().amount()
            ));

            // ── TX2: 접수 성공 → PROCESSING ──
            return tx.execute(status -> {
                PaymentModel p = mustFindPayment(pending.getId());
                p.markProcessing(result.transactionKey());
                return PaymentInfo.from(paymentRepository.save(p));
            });

        } catch (PaymentGatewayUnavailableException e) {
            // 시스템 장애 / CB OPEN → PENDING 유지. 사용자에겐 '처리 중'으로 정상 응답.
            // (콜백/폴링 복구가 나중에 결과를 맞춘다)
            log.warn("[결제] PG 일시 불가, PENDING 유지 orderId={}", criteria.orderId());
            return PaymentInfo.from(pending);

        } catch (PaymentDeclinedException e) {
            // PG가 정상 작동하며 거절(요청/비즈니스 오류) → 즉시 FAILED 확정.
            log.info("[결제] PG 거절 orderId={}, reason={}", criteria.orderId(), e.getMessage());
            return tx.execute(status -> {
                PaymentModel p = mustFindPayment(pending.getId());
                p.markFailed(e.getMessage());
                paymentRepository.save(p);
                OrderModel order = mustFindOrder(p.getOrderId());
                order.fail();
                return PaymentInfo.from(p);
            });
        }
    }

    /**
     * PG 콜백 수신 처리. transactionKey 로 결제를 찾아 결과를 반영한다.
     * 콜백/폴링이 공유하는 결과 반영 로직(applyResult)을 한 트랜잭션에서 수행한다.
     */
    public void handleCallback(String transactionKey, PgStatus pgStatus, String reason) {
        tx.executeWithoutResult(status -> {
            PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            "[txKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));
            applyResult(payment, pgStatus, reason);
        });
    }

    /**
     * PG 결과를 Payment + Order 에 반영. (콜백/폴링 공용, 반드시 트랜잭션 안에서 호출)
     * Payment 의 상태 가드(idempotent) 덕분에 중복/경합 호출에도 안전하다.
     */
    void applyResult(PaymentModel payment, PgStatus pgStatus, String reason) {
        switch (pgStatus) {
            case SUCCESS -> {
                payment.markSuccess(reason);
                mustFindOrder(payment.getOrderId()).confirm();
            }
            case FAILED -> {
                payment.markFailed(reason);
                mustFindOrder(payment.getOrderId()).fail();
            }
            case PENDING -> {
                // 아직 처리 중 — 반영할 것 없음
            }
        }
        paymentRepository.save(payment);
    }

    /**
     * 복구: PG에서 조회한 실제 결과를 결제에 반영한다. (콜백 유실/요청 타임아웃 보정)
     * - 요청 타임아웃으로 txKey 가 없던 건은 여기서 txKey 를 보정(PENDING→PROCESSING)한 뒤 결과 반영.
     */
    public void reconcile(Long paymentId, PgTransactionResult result) {
        tx.executeWithoutResult(status -> {
            PaymentModel payment = mustFindPayment(paymentId);
            if (payment.getTransactionKey().isEmpty() && result.transactionKey() != null) {
                payment.markProcessing(result.transactionKey());
            }
            applyResult(payment, result.status(), result.reason());
        });
    }

    // ===== TX1 본문 =====
    private PaymentModel createPendingPayment(PaymentCriteria criteria) {
        OrderModel order = mustFindOrder(criteria.orderId());

        if (!order.getUserId().equals(criteria.userId())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인 주문이 아닙니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 가능한 주문 상태가 아닙니다. (현재: " + order.getStatus() + ")");
        }
        // 중복 결제 가드: 이미 진행 중/완료된 결제가 있으면 거절 (FAILED 만 재시도 허용)
        paymentRepository.findByOrderId(order.getId()).ifPresent(existing -> {
            if (existing.getStatus() != PaymentStatus.FAILED) {
                throw new CoreException(ErrorType.CONFLICT,
                        "이미 처리 중이거나 완료된 결제가 있습니다. (상태: " + existing.getStatus() + ")");
            }
        });

        PaymentModel payment = PaymentModel.create(
                criteria.userId(),
                order.getId(),
                order.getFinalAmount(),
                criteria.cardType(),
                criteria.cardNo()
        );
        return paymentRepository.save(payment);
    }

    private PaymentModel mustFindPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + id + "] 결제를 찾을 수 없습니다."));
    }

    private OrderModel mustFindOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + id + "] 주문을 찾을 수 없습니다."));
    }
}
