package com.loopers.application.payment;

import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 결제 트랜잭션 단위. PG 외부 호출은 여기 들어오지 않는다(원칙 3: 외부 호출은 트랜잭션 밖) —
 * Facade 가 createPending/attachTransactionKey 사이에서 PG 를 호출한다.
 * applyResult 는 PG 결과가 이미 도착한 뒤 우리 DB 만 만지므로 원자적 트랜잭션이 정답이다.
 */
@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 결제 접수: 주문 검증 + 멱등 가드 + PENDING 결제를 먼저 저장(record-first).
     * 활성 결제가 이미 있으면 새로 만들지 않고 그것을 돌려준다(한 주문 = 한 활성 결제).
     */
    @Transactional
    public Payment createPending(Long userId, Long orderId, CardType cardType) {
        // 비관적 락: 동시 결제 접수를 직렬화 → 두 번째 요청은 첫 번째의 활성 결제를 보고 새로 만들지 않는다.
        Order order = orderRepository.findForUpdate(orderId)
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

    /** 본인 소유 결제 조회 (수동 복구용). 타 유저 결제는 존재를 드러내지 않는다. */
    @Transactional(readOnly = true)
    public Payment findOwned(Long paymentId, Long userId) {
        Payment payment = paymentRepository.find(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + paymentId + "] 결제건을 찾을 수 없습니다."));
        if (!payment.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + paymentId + "] 결제건을 찾을 수 없습니다.");
        }
        return payment;
    }

    /** 거래키로 결제 조회 (콜백 트리거용). */
    @Transactional(readOnly = true)
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey);
    }

    /** PG 가 접수해 발급한 거래키를 결제에 기록한다 (PG 호출 성공 직후, TX2). */
    @Transactional
    public Payment attachTransactionKey(Long paymentId, String transactionKey) {
        Payment payment = paymentRepository.find(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + paymentId + "] 결제건을 찾을 수 없습니다."));
        payment.assignTransactionKey(transactionKey);
        return paymentRepository.save(payment);
    }

    /**
     * PG 결과(콜백/폴링)를 반영한다. PENDING→터미널 가드 전이로 정확히 한 번만 적용(중복 도착은 흡수상태 no-op).
     * SUCCESS → 주문 PAID, FAILED → 주문 FAILED + 재고·쿠폰 복원(모델 A 보상).
     */
    @Transactional
    public void applyResult(String transactionKey, PaymentStatus result, String reason) {
        if (result != PaymentStatus.SUCCESS && result != PaymentStatus.FAILED) {
            return; // 비종결(PENDING 등)은 실패로 단정하지 않는다 — PG 처리 진행 중일 수 있음
        }
        Payment payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "(transactionKey: " + transactionKey + ") 결제건을 찾을 수 없습니다."));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return; // 중복 도착 — 흡수상태, no-op (Drill C 멱등)
        }
        Order order = orderRepository.find(payment.getOrderId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + payment.getOrderId() + "] 주문을 찾을 수 없습니다."));

        if (result == PaymentStatus.SUCCESS) {
            payment.markSuccess();
            order.markPaid();
        } else {
            payment.markFailed(reason);
            order.markPaymentFailed();
            compensate(order);
        }
        paymentRepository.save(payment);
        orderRepository.save(order);
    }

    /** 결제 실패 보상: 주문이 차감했던 재고/쿠폰을 되돌린다. */
    private void compensate(Order order) {
        List<Long> productIds = order.getItems().stream()
            .map(OrderItem::getProductId).distinct().sorted().toList();
        Map<Long, Product> products = productRepository.findAllForUpdate(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        order.getItems().forEach(item -> {
            Product product = products.get(item.getProductId());
            if (product != null) {
                product.increaseStock(Quantity.of(item.getQuantity()));
                productRepository.save(product);
            }
        });
        if (order.getUserCouponId() != null) {
            userCouponRepository.find(order.getUserCouponId()).ifPresent(userCoupon -> {
                userCoupon.restore();
                userCouponRepository.save(userCoupon);
            });
        }
    }
}
