package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 오케스트레이션. 결제 요청(pay)과, PG가 콜백/폴링으로 전해온 결과 반영(confirm)을 담당한다.
 * 외부(PG) 호출은 트랜잭션 밖에서 한다(외부 지연이 DB 커넥션을 점유하지 않도록).
 * 콜백과 폴링이 같은 결제를 동시에 건드릴 수 있으므로, 이미 확정된 결제는 멱등하게 무시한다.
 */
@RequiredArgsConstructor
@Component
public class PaymentFacade {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final CouponService couponService;
    private final PaymentGateway paymentGateway;

    @Value("${payment.callback-url}")
    private String callbackUrl;

    /**
     * 주문에 대한 결제를 요청한다. 주문을 PG에 결제 요청하고(트랜잭션 밖), 결과로 Payment 를 기록한다.
     * 결과는 보통 PENDING — 최종 확정은 콜백/폴링이 confirm 으로 처리한다.
     */
    public PaymentInfo pay(Long userId, Long orderId, CardType cardType, String cardNo) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다."));
        if (!order.getUserId().equals(userId)) {
            // 타인 주문 존재 비노출 — 동일하게 NOT_FOUND
            throw new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "결제 대기 상태의 주문만 결제할 수 있습니다. 현재 상태: " + order.getStatus());
        }

        PaymentGateway.PaymentResult result = paymentGateway.requestPayment(new PaymentGateway.PaymentRequest(
            String.valueOf(userId),
            String.format("%06d", order.getId()),   // 시뮬레이터는 orderId 를 6자리 이상 문자열로 요구
            cardType.name(),
            cardNo,
            order.getPaymentAmount().getAmount().longValue(),
            callbackUrl
        ));
        Payment payment = paymentRepository.save(new Payment(
            order.getId(), userId, result.transactionKey(),
            order.getPaymentAmount(), result.status(), result.reason()));
        return PaymentInfo.from(payment);
    }

    @Transactional
    public void confirm(String transactionKey, PaymentStatus status, String reason) {
        Payment payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return; // 이미 확정됨 — 콜백 중복/폴링 동시 도착에 대한 멱등 no-op
        }
        Order order = orderRepository.findById(payment.getOrderId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + payment.getOrderId() + "] 주문을 찾을 수 없습니다."));

        switch (status) {
            case SUCCESS -> {
                payment.markSuccess();
                order.markPaid();
            }
            case FAILED -> {
                payment.markFailed(reason);
                order.markFailed();
                compensate(order);
            }
            case PENDING -> {
                // 아직 미확정 통지 — 확정할 것이 없으므로 그대로 둔다
            }
        }
        // 전이는 영속 상태의 엔티티를 수정한 것이라 트랜잭션 커밋 시 dirty checking 으로 반영된다 (별도 save 불필요).
    }

    /** 결제 실패 보상: 차감했던 재고와 사용했던 쿠폰을 되돌린다. */
    private void compensate(Order order) {
        orderRepository.findItemsByOrderId(order.getId())
            .forEach(item -> productService.restoreStock(item.getProductId(), item.getQuantity().getValue()));
        if (order.getCouponId() != null) {
            couponService.restore(order.getUserId(), order.getCouponId());
        }
    }
}
