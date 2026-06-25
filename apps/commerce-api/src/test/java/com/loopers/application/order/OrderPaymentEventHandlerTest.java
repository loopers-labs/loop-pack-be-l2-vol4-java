package com.loopers.application.order;

import com.loopers.domain.order.FakeOrderRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentFailureCategory;
import com.loopers.domain.payment.event.PaymentFailedEvent;
import com.loopers.domain.payment.event.PaymentSucceededEvent;
import com.loopers.domain.product.FakeProductRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.shared.Money;
import com.loopers.domain.shared.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderPaymentEventHandler 단위 테스트. 결제 이벤트가 Order 상태 전이 + 재고 복구로 연결되는지,
 * 그리고 중복 이벤트에서도 재고가 한 번만 복구되는지를 검증한다.
 */
class OrderPaymentEventHandlerTest {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private OrderPaymentEventHandler handler;

    @BeforeEach
    void setUp() {
        orderRepository = new FakeOrderRepository();
        productRepository = new FakeProductRepository();
        handler = new OrderPaymentEventHandler(orderRepository, productRepository);
    }

    /**
     * 주문 생성 → 재고 차감 → 저장 순서로 실제 흐름과 동일하게 셋업.
     * (재고가 미리 차감된 상태가 결제 시점의 정상 상태)
     */
    private Order saveCreatedOrder(int initialStock, int orderQty) {
        Product product = productRepository.save(
            Product.create("상품A", "설명", Money.of(1_000L), initialStock, 1L)
        );
        product.decreaseStock(orderQty);
        Order order = Order.create(
            1L,
            List.of(OrderItem.of(product.getId(), product.getName(), Money.of(1_000L), Quantity.of(orderQty))),
            null, null
        );
        return orderRepository.save(order);
    }

    @DisplayName("PaymentSucceededEvent 수신 시 Order 가 PAID 로 전이된다.")
    @Test
    void onPaymentSucceeded_marksOrderPaid() {
        Order order = saveCreatedOrder(10, 2);

        handler.onPaymentSucceeded(new PaymentSucceededEvent(1L, order.getId(), 1L, Money.of(2_000L)));

        assertThat(orderRepository.find(order.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PAID);
    }

    @DisplayName("TERMINAL 실패 이벤트 수신 시 Order FAILED + 재고 복구 (10 → 8 → 10).")
    @Test
    void onPaymentFailed_terminal_marksFailedAndRestoresStock() {
        Order order = saveCreatedOrder(10, 2);
        Long productId = order.getItems().get(0).getProductId();
        assertThat(productRepository.find(productId).orElseThrow().getStock()).isEqualTo(8);

        handler.onPaymentFailed(new PaymentFailedEvent(
            1L, order.getId(), 1L, "PG 서버 오류", PaymentFailureCategory.TERMINAL));

        assertThat(orderRepository.find(order.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.FAILED);
        assertThat(productRepository.find(productId).orElseThrow().getStock()).isEqualTo(10);
    }

    @DisplayName("RECOVERABLE 실패 (한도초과) 이벤트 수신 시 Order/재고 모두 유지 — 사용자가 다른 카드로 재시도 가능.")
    @Test
    void onPaymentFailed_recoverable_keepsOrderAndStock() {
        Order order = saveCreatedOrder(10, 2);
        Long productId = order.getItems().get(0).getProductId();

        handler.onPaymentFailed(new PaymentFailedEvent(
            1L, order.getId(), 1L, "한도초과입니다.", PaymentFailureCategory.RECOVERABLE));

        // Order 는 CREATED 유지, 재고도 그대로 (8개)
        assertThat(orderRepository.find(order.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.CREATED);
        assertThat(productRepository.find(productId).orElseThrow().getStock()).isEqualTo(8);
    }

    @DisplayName("같은 TERMINAL 실패 이벤트 두 번 도착해도 재고는 한 번만 복구된다 (중복 복구 방지).")
    @Test
    void onPaymentFailed_terminalIdempotent_restoresStockOnlyOnce() {
        Order order = saveCreatedOrder(10, 2);
        Long productId = order.getItems().get(0).getProductId();

        handler.onPaymentFailed(new PaymentFailedEvent(
            1L, order.getId(), 1L, "실패1", PaymentFailureCategory.TERMINAL));
        handler.onPaymentFailed(new PaymentFailedEvent(
            1L, order.getId(), 1L, "실패2", PaymentFailureCategory.TERMINAL));

        assertThat(productRepository.find(productId).orElseThrow().getStock()).isEqualTo(10); // 12 가 아님
    }
}
