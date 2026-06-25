package com.loopers.domain.payment;

import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /** TX1: 주문 비관적 락 + 검증 + 중복 체크 + PENDING 저장 */
    @Transactional
    public PaymentEntity prepare(String userId, String orderId, CardType cardType, String cardNo) {
        OrderEntity order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 가능한 주문 상태가 아닙니다.");
        }
        if (paymentRepository.existsByOrderIdAndStatusIn(orderId, PaymentStatus.PENDING, PaymentStatus.SUCCESS)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 진행 중이거나 완료된 결제가 있습니다.");
        }
        return paymentRepository.save(new PaymentEntity(orderId, userId, cardType, cardNo, order.finalAmount()));
    }

    /** TX2: transactionKey 저장 + PG 즉시 SUCCESS/FAILED 반영 */
    @Transactional
    public void applyPgResponse(String paymentId, PgTransactionResponse pgResponse) {
        PaymentEntity payment = findPaymentOrThrow(paymentId);
        payment.registerTransactionKey(pgResponse.transactionKey());
        if (pgResponse.status() == PgTransactionStatus.SUCCESS) {
            approveAndPayOrder(payment);
        } else if (pgResponse.status() == PgTransactionStatus.FAILED) {
            payment.fail(pgResponse.reason());
        }
        paymentRepository.save(payment);
    }

    /** PG 요청 자체 실패 시 FAILED 확정 */
    @Transactional
    public void markFailed(String paymentId, String reason) {
        PaymentEntity payment = findPaymentOrThrow(paymentId);
        payment.fail(reason);
        paymentRepository.save(payment);
    }

    /** TX3: 콜백 / 1차 Poll 공통 확정. first-wins 멱등 */
    @Transactional
    public void settle(String transactionKey, PgTransactionStatus status, String reason) {
        PaymentEntity payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            // 이미 확정 → 첫 결정 유지. 중복/지연 콜백(at-least-once 재전송)을 멱등하게 무시.
            return;
        }
        if (status == PgTransactionStatus.PENDING) {
            log.info("settle no-op: transactionKey={} is still PENDING, skipping confirmation.", transactionKey);
            return;
        }
        if (status == PgTransactionStatus.SUCCESS) {
            approveAndPayOrder(payment);
        } else if (status == PgTransactionStatus.FAILED) {
            payment.fail(reason);
        }
        paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentEntity getOrThrow(String paymentId) {
        return findPaymentOrThrow(paymentId);
    }

    private PaymentEntity findPaymentOrThrow(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PaymentEntity getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    private void approveAndPayOrder(PaymentEntity payment) {
        payment.approve();
        OrderEntity order = orderRepository.findById(payment.getOrderId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.pay();
        orderRepository.save(order);
    }
}
