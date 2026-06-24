package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 결제 확정 → 도메인 이벤트(AFTER_COMMIT) → 주문 반영까지 in-process 배선이 실제로 도는지 H2로 검증한다.
 * 트랜잭션을 걸지 않는다 — confirm()이 자기 트랜잭션으로 커밋되어야 AFTER_COMMIT 리스너가 발화하기 때문.
 */
@SpringBootTest
class PaymentEventFlowIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private IssuedCouponRepository issuedCouponRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderItem item(long productId, long unitPrice, int quantity) {
        return new OrderItem(productId, "상품-" + productId, unitPrice, quantity);
    }

    private PaymentModel pendingPayment(Long orderId, Money amount, String transactionKey) {
        PaymentModel payment = new PaymentModel(orderId, USER_ID, CardType.SAMSUNG, amount);
        payment.assignTransactionKey(transactionKey);
        return paymentRepository.save(payment);
    }

    @DisplayName("결제 성공 콜백을 확정하면")
    @Nested
    class OnSuccess {

        @DisplayName("AFTER_COMMIT 이벤트로 주문이 PAID로 반영된다")
        @Test
        void marksOrderPaid() {
            OrderModel order = orderRepository.save(new OrderModel(USER_ID, List.of(item(100L, 1_000L, 1)), null, Money.ZERO));
            pendingPayment(order.getId(), order.getFinalAmount(), "tx-success");

            paymentService.confirm("tx-success", true, null);

            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        }
    }

    @DisplayName("결제 실패 콜백을 확정하면")
    @Nested
    class OnFailure {

        @DisplayName("AFTER_COMMIT 이벤트로 재고·쿠폰이 복원되고 주문이 CANCELED로 반영된다")
        @Test
        void compensatesAndCancels() {
            stockRepository.save(new StockModel(100L, 10));
            IssuedCoupon coupon = new IssuedCoupon(USER_ID, 1L);
            coupon.use();
            issuedCouponRepository.save(coupon);
            OrderModel order = orderRepository.save(
                new OrderModel(USER_ID, List.of(item(100L, 1_000L, 2)), coupon.getId(), Money.of(500L)));
            pendingPayment(order.getId(), order.getFinalAmount(), "tx-fail");

            paymentService.confirm("tx-fail", false, "한도 초과");

            assertAll(
                () -> assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED),
                () -> assertThat(stockRepository.findByProductId(100L).orElseThrow().getQuantity()).isEqualTo(12),
                () -> assertThat(issuedCouponRepository.findById(coupon.getId()).orElseThrow().getStatus()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }
    }
}
