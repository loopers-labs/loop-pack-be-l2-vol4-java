package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 트랜잭션 단위. PG 외부 호출은 여기 들어오지 않는다(원칙 3: 외부 호출은 트랜잭션 밖) —
 * Facade 가 이 메서드들 '사이'에서 PG 를 호출한다. 짧은 트랜잭션으로 커넥션/락 보유를 최소화한다.
 */
@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * 결제 접수: 주문 검증 + 멱등 가드 + PENDING 결제를 먼저 저장(record-first).
     * 활성 결제가 이미 있으면 새로 만들지 않고 그것을 돌려준다(한 주문 = 한 활성 결제).
     */
    @Transactional
    public Payment createPending(Long userId, Long orderId, CardType cardType) {
        Order order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        if (!order.isOwnedBy(userId)) {
            // 타 유저 주문은 존재를 드러내지 않는다
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "결제할 수 없는 주문 상태입니다. (status=" + order.getStatus() + ")");
        }
        return paymentRepository.findActiveByOrderId(orderId)
            .orElseGet(() -> paymentRepository.save(
                Payment.pending(userId, orderId, Money.of(order.getFinalAmount()), cardType)));
    }

    /** PG 가 접수해 발급한 거래키를 결제에 기록한다 (PG 호출 성공 직후, TX2). */
    @Transactional
    public Payment attachTransactionKey(Long paymentId, String transactionKey) {
        Payment payment = paymentRepository.find(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + paymentId + "] 결제건을 찾을 수 없습니다."));
        payment.assignTransactionKey(transactionKey);
        return paymentRepository.save(payment);
    }
}
